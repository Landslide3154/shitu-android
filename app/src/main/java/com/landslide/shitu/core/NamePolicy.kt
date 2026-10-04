package com.landslide.shitu.core

/**
 * 目标文件名生成：清洗 → 后缀 → 冲突 → 截断（规格 §6.5）。
 * 纯函数、无 IO：候选名以序列形式给出，调用方（TransferExecutor）负责实际探测存在性。
 */
class NamePolicy(private val maxBytes: Int = 180) {

    private val illegal = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    /** 清洗非法字符、控制字符、首尾空白与结尾的点。 */
    fun sanitize(name: String): String =
        name.replace(illegal, "_").trim().trimEnd('.')

    /** 原名_来源.ext（无扩展名时直接追加）；后缀自己带 `_`/`-` 开头时不再重复加连接符。 */
    fun withSourceSuffix(name: String, sourceApp: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot <= 0) name else name.substring(0, dot)
        val ext = if (dot <= 0) "" else name.substring(dot)
        val separator = if (sourceApp.startsWith("_") || sourceApp.startsWith("-")) "" else "_"
        return base + separator + sourceApp + ext
    }

    /** 按 UTF-8 字节数截断主体，保留扩展名。 */
    fun truncate(name: String, maxBytes: Int = this.maxBytes): String {
        if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot) else ""
        val base = if (dot > 0) name.substring(0, dot) else name
        val budget = maxBytes - ext.toByteArray(Charsets.UTF_8).size
        val sb = StringBuilder()
        for (ch in base) {
            if ((sb.toString() + ch).toByteArray(Charsets.UTF_8).size > budget) break
            sb.append(ch)
        }
        return sb.toString() + ext
    }

    /**
     * 候选名序列：`原名_标签` → `原名_标签_时间戳` → `原名_标签_1`、`_2`…
     * 标签为空（不追加来源后缀）时省略标签段。
     */
    fun candidates(rawName: String, sourceApp: String?, timestamp: String = ""): Sequence<String> = sequence {
        val base = truncate(sanitize(rawName))
        val tag = sourceApp?.let { sanitize(it) }.orEmpty()
        fun named(extra: String) =
            truncate(withSourceSuffix(base, if (tag.isEmpty()) extra else "${tag}_$extra"))

        yield(if (tag.isEmpty()) base else truncate(withSourceSuffix(base, tag)))
        if (timestamp.isNotEmpty()) yield(named(timestamp))
        var n = 1
        while (true) {
            yield(named("$n"))
            n++
        }
    }

    /**
     * 冲突链路：原名_标签 → 原名_标签_时间戳 → 原名_标签_n。
     * @param exists 给定的候选文件名在目标目录中是否已存在
     */
    fun resolve(
        rawName: String,
        sourceApp: String?,
        exists: (String) -> Boolean,
        timestamp: String = "",
    ): String {
        var guard = 0
        for (c in candidates(rawName, sourceApp, timestamp)) {
            if (!exists(c)) return c
            if (++guard > 10_000) throw IllegalStateException("无法为目标文件生成不冲突的名字：$rawName")
        }
        error("unreachable")
    }
}
