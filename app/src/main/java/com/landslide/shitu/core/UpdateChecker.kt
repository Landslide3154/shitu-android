package com.landslide.shitu.core

import java.net.HttpURLConnection
import java.net.URI

/**
 * 一次「有新版本」的解析结果。
 *
 * [version] 是去掉 `v` 前缀的纯版本号，直接拿去和 `BuildConfig.VERSION_NAME` 比；
 * [apkUrl] 只取附件里的 APK——Release 页面还可能挂着源码包、校验文件等其它附件。
 */
data class UpdateInfo(
    val tagName: String,
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val publishedAt: String,
)

/**
 * 「检查更新」的纯逻辑层：解析 GitHub Releases、比较版本号、拉取最新发布。
 *
 * 这里不碰 UI、不下载、不安装，也不读写本地状态，所以能直接在 JVM 单测里跑；
 * 弹窗、下载与安装由上层接线。
 *
 * 项目没有 JSON 库，也不值得为这一个接口引依赖；而 Android 自带的 `org.json`
 * 只在真机（Android 运行时）里有，JVM 单测里没有。因此下面自带一个只够读 Release
 * 的最小 JSON 解析器，保证解析逻辑在 JVM 上可测。
 */
object UpdateChecker {

    /**
     * 固定仓库地址：App 内更新只认自己的仓库，不做成可配置项，
     * 避免有人改配置后把安装包来源指向第三方。
     */
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Landslide3154/shitu-android/releases/latest"

    /** GitHub API 强制要求带 User-Agent，否则直接返回 403。 */
    private const val USER_AGENT = "shitu-android"

    /**
     * notes 截断长度：Release 说明可能上万字，整段塞进对话框既占内存又没人看完；
     * 4000 字符足够讲清一次更新，也远小于 Binder/Intent 常见的传输上限。
     */
    private const val NOTES_LIMIT = 4_000

    // ---------------------------------------------------------------- 解析

    /**
     * 解析 `releases/latest` 的 JSON 响应。
     *
     * 返回 null 的三种情况：JSON 畸形、根节点不是对象、缺少 `tag_name`
     * （没有 tag 就无从判断版本，宁可当作「没有更新」，也不要弹一个空版本）。
     * 其它字段缺失都按空值降级处理，不因此丢掉整条更新。
     */
    fun parseRelease(json: String): UpdateInfo? {
        val rootValue = try {
            JsonParser(json).parse()
        } catch (_: Exception) {
            // 解析失败只可能是响应被截断/不是 JSON；检查更新不该因为脏数据把 App 弄崩
            return null
        }
        val root = rootValue as? JsonObject ?: return null

        val tagName = root.string("tag_name")?.trim().orEmpty()
        if (tagName.isEmpty()) return null

        // tag 习惯写 v 前缀，而本地 versionName 不带 v，去掉后才能按数字段比较
        val version = tagName.removePrefix("v").removePrefix("V")
        // GitHub 的 name 可以为空/null，此时用 tag 当标题，总比空白标题好
        val title = root.string("name")?.takeIf { it.isNotBlank() } ?: tagName
        val notes = truncateNotes(root.string("body").orEmpty())
        val pageUrl = root.string("html_url").orEmpty()
        val publishedAt = root.string("published_at").orEmpty()
        val apkUrl = pickApk(root.array("assets"), version)

        return UpdateInfo(
            tagName = tagName,
            version = version,
            title = title,
            notes = notes,
            pageUrl = pageUrl,
            apkUrl = apkUrl,
            publishedAt = publishedAt,
        )
    }

    /** 从 assets 数组里挑下载地址；没有 APK 时返回 null（上层据此决定只展示说明、不提供下载）。 */
    private fun pickApk(assets: JsonArray?, version: String): String? {
        val apkUrls = assets?.items.orEmpty()
            .filterIsInstance<JsonObject>()
            .mapNotNull { it.string("browser_download_url") }
            .filter { isApkUrl(it) }
        if (apkUrls.isEmpty()) return null

        // 同一 Release 里偶尔会有多个 APK（旧命名、debug 包等）。
        // 优先挑文件名带本次版本号的那个，避免点「更新」却装到另一个版本。
        return apkUrls.firstOrNull { version.isNotEmpty() && it.contains(version, ignoreCase = true) }
            ?: apkUrls.first()
    }

    /** 只认「路径以 .apk 结尾」的链接；查询串/锚点不算文件名的一部分。 */
    private fun isApkUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return path.endsWith(".apk", ignoreCase = true)
    }

    /** 截断 notes，且不把 emoji 的代理对从中间切开（切开会留下半个字符，UI 显示成乱码方块）。 */
    private fun truncateNotes(body: String): String {
        if (body.length <= NOTES_LIMIT) return body
        var cut = body.substring(0, NOTES_LIMIT)
        if (cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        return cut
    }

    // -------------------------------------------------------------- 版本比较

    /**
     * [remote] 是否比 [local] 新。
     *
     * 按点分段逐段比数字：`0.12.3` < `0.13.0`；段数不同时缺的段按 0 算，
     * 所以 `0.13` 与 `0.13.0` 相等；允许 `v` 前缀，`-beta`/`+build` 之类的后缀
     * 不参与比较。任何一边不合法（空串、非数字段、空段）都返回 false：
     * 检查更新宁可漏报，也不能把坏数据当成「有新版本」去烦用户。
     */
    fun isNewer(local: String, remote: String): Boolean {
        val localSegments = numericSegments(local) ?: return false
        val remoteSegments = numericSegments(remote) ?: return false

        val count = maxOf(localSegments.size, remoteSegments.size)
        for (i in 0 until count) {
            // 段数不同按 0 补齐：0.13 == 0.13.0
            val l = localSegments.getOrElse(i) { "0" }
            val r = remoteSegments.getOrElse(i) { "0" }
            val cmp = compareNumeric(l, r)
            // cmp 是「本地 vs 远端」：远端更大时 cmp < 0，才说明有新版本
            if (cmp != 0) return cmp < 0
        }
        return false // 完全相等不算更新
    }

    /**
     * 把版本串拆成「去掉前导零的纯数字段」；不合法返回 null。
     * 用字符串而不是 Int/Long 存段，是为了让超长数字（或恶意输入）既不会溢出、也不会抛异常。
     */
    private fun numericSegments(raw: String): List<String>? {
        val trimmed = raw.trim()
        val withoutPrefix = when {
            trimmed.startsWith('v') || trimmed.startsWith('V') -> trimmed.drop(1)
            else -> trimmed
        }
        // 预发布/构建后缀不影响「谁更新」：0.13.0-beta 仍按 0.13.0 算
        val core = withoutPrefix.substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null

        val parts = core.split('.')
        if (parts.any { part -> part.isEmpty() || part.any { it !in '0'..'9' } }) return null

        return parts.map { it.trimStart('0').ifEmpty { "0" } }
    }

    /** 两个已归一化的数字串比较大小；它们没有前导零，位数多的一定更大。 */
    private fun compareNumeric(a: String, b: String): Int {
        if (a.length != b.length) return a.length - b.length
        return a.compareTo(b)
    }

    // ---------------------------------------------------------------- 网络

    /**
     * 拉取最新 Release 并解析；失败（离线、超时、限流、非 200、脏数据）一律返回 null。
     *
     * 这是阻塞调用，上层应放到 IO 线程；这里不自己起线程，保持逻辑层简单、可测。
     */
    fun fetchLatest(timeoutMs: Int = 10_000): UpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            // HttpURLConnection 里 0 表示「永不超时」，调用方传 0/负数时兜底成 1 秒，避免挂死线程
            val timeout = timeoutMs.coerceAtLeast(1_000)
            // 用 URI.toURL() 而不是已废弃的 URL(String) 构造器：JDK 20+ 编译时会报弃用警告
            val conn = (URI(LATEST_RELEASE_API).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeout
                readTimeout = timeout
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            connection = conn

            // GitHub 对匿名请求限流（60 次/小时），拿不到 200 就当作「这次没查到」，不弹错误
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null

            // 固定按 UTF-8 读：Release 说明里有中文，不能依赖平台默认编码
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            parseRelease(body)
        } catch (_: Exception) {
            // 网络与解析异常都吞掉：检查更新是辅助功能，任何失败都不该影响主流程
            null
        } finally {
            connection?.disconnect()
        }
    }
}

// ---------------------------------------------------------------- 最小 JSON 解析器

/** 仅覆盖 Release 响应需要的 JSON 值类型；数字/布尔/null 只保留原文，取值时用不到。 */
private sealed interface JsonValue

private class JsonObject(val fields: Map<String, JsonValue>) : JsonValue

private class JsonArray(val items: List<JsonValue>) : JsonValue

private class JsonString(val value: String) : JsonValue

private class JsonLiteral(val raw: String) : JsonValue

/**
 * 递归下降的最小 JSON 解析器。
 *
 * 之所以手写而不是用正则找字段：body 里可能包含 `"tag_name"` 这样的文本，
 * 正则会被字符串内容骗到；而 Release 的 JSON 嵌套固定、语法也简单，几十行就能覆盖。
 */
private class JsonParser(private val src: String) {

    private var pos = 0

    fun parse(): JsonValue {
        val value = parseValue()
        skipWhitespace()
        if (pos != src.length) fail() // 后面还有垃圾字符，说明响应不是完整 JSON
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (pos >= src.length) fail()
        return when (val c = src[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonString(parseString())
            't' -> JsonLiteral(readLiteral("true"))
            'f' -> JsonLiteral(readLiteral("false"))
            'n' -> JsonLiteral(readLiteral("null"))
            else -> if (c == '-' || c in '0'..'9') JsonLiteral(readNumber()) else fail()
        }
    }

    private fun parseObject(): JsonObject {
        pos++ // 跳过 '{'
        val fields = HashMap<String, JsonValue>()
        skipWhitespace()
        if (peekOrNull() == '}') {
            pos++
            return JsonObject(fields)
        }
        while (true) {
            skipWhitespace()
            if (peekOrNull() != '"') fail()
            val key = parseString()
            skipWhitespace()
            if (peekOrNull() != ':') fail()
            pos++
            fields[key] = parseValue()
            skipWhitespace()
            when (peekOrNull()) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return JsonObject(fields)
                }
                else -> fail()
            }
        }
    }

    private fun parseArray(): JsonArray {
        pos++ // 跳过 '['
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (peekOrNull() == ']') {
            pos++
            return JsonArray(items)
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            when (peekOrNull()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return JsonArray(items)
                }
                else -> fail()
            }
        }
    }

    /** 解析字符串并处理转义；body 里的 `\"`、`\n`、`\uXXXX` 都靠这里还原。 */
    private fun parseString(): String {
        pos++ // 跳过开引号
        val sb = StringBuilder()
        while (pos < src.length) {
            when (val c = src[pos++]) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= src.length) fail()
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> sb.append(readUnicodeEscape())
                        else -> fail()
                    }
                }
                else -> sb.append(c) // 普通字符（含中文）原样收下
            }
        }
        fail() // 字符串没有收尾引号
    }

    /**
     * 读 `\uXXXX`。按 UTF-16 code unit 逐个 append，
     * emoji 这类代理对会在结果字符串里自动拼回去，不需要额外配对逻辑。
     */
    private fun readUnicodeEscape(): Char {
        if (pos + 4 > src.length) fail()
        var code = 0
        repeat(4) {
            val digit = Character.digit(src[pos++], 16)
            if (digit < 0) fail()
            code = code * 16 + digit
        }
        return code.toChar()
    }

    private fun readLiteral(literal: String): String {
        if (!src.startsWith(literal, pos)) fail()
        pos += literal.length
        return literal
    }

    /** 数字只做语法跳过；Release 里的数字字段（id、size 等）我们都不需要取值。 */
    private fun readNumber(): String {
        val start = pos
        if (pos < src.length && src[pos] == '-') pos++
        while (pos < src.length && src[pos] in '0'..'9') pos++
        if (pos < src.length && src[pos] == '.') {
            pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
        }
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            pos++
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
        }
        if (pos == start) fail()
        return src.substring(start, pos)
    }

    private fun skipWhitespace() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun peekOrNull(): Char? = if (pos < src.length) src[pos] else null

    private fun fail(): Nothing = throw IllegalArgumentException("JSON 解析失败（位置 $pos）")
}

/** 取对象里的字符串字段；字段缺失或类型不对都返回 null，由调用方决定怎么降级。 */
private fun JsonObject.string(key: String): String? = (fields[key] as? JsonString)?.value

/** 取对象里的数组字段；`assets` 缺失时返回 null。 */
private fun JsonObject.array(key: String): JsonArray? = fields[key] as? JsonArray
