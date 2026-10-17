package com.landslide.shitu.data

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 规则的导入 / 导出。
 *
 * 格式刻意选「一行一条、tab 分隔、字段 URL 编码」的纯文本（扩展名 .txt）：
 * - 不引 JSON 库、可被 JVM 单测直接验证；
 * - 用户拿文本编辑器也能看个大概；
 * - 字段里出现 tab/换行（文件名、路径都可能）也不会串列。
 *
 * 导入一律建成「暂停」的规则、id 归零，避免一导入就立刻搬东西。
 */
object RuleIo {

    private const val HEADER = "# shitu rules v1"

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun fileName(now: Long = System.currentTimeMillis()): String =
        "shitu-rules-${stamp.format(Date(now))}.txt"

    fun encode(rules: List<RuleEntity>): String = buildString {
        appendLine(HEADER)
        rules.forEach { r ->
            appendLine(
                listOf(
                    r.name, r.srcPath, r.dstPath,
                    r.includeSubdirs.toString(), (r.maxDepth ?: 0).toString(),
                    r.mode.name, r.intervalMinutes.toString(),
                    r.extensions, r.suffix, r.contentRename.toString(),
                    r.dstSubdirApp.toString(), r.minAgeDays.toString(), r.minSizeKb.toString(),
                ).joinToString("\t") { enc(it) },
            )
        }
    }

    /** 解析；坏行直接跳过（宁可少导入，也不要一条坏数据把整个导入搞崩）。 */
    fun decode(text: String): List<RuleEntity> {
        if (text.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val f = line.split('\t').map { dec(it) }
                if (f.size < 3) return@mapNotNull null
                if (f[0].isBlank() || f[1].isBlank() || f[2].isBlank()) return@mapNotNull null
                RuleEntity(
                    name = f[0],
                    srcPath = f[1],
                    dstPath = f[2],
                    includeSubdirs = f.getOrNull(3)?.toBooleanStrictOrNull() ?: true,
                    maxDepth = f.getOrNull(4)?.toIntOrNull()?.takeIf { it > 0 },
                    mode = f.getOrNull(5)
                        ?.let { m -> Mode.entries.firstOrNull { it.name == m } } ?: Mode.MOVE,
                    intervalMinutes = (f.getOrNull(6)?.toIntOrNull() ?: 5).coerceIn(1, 30),
                    extensions = f.getOrNull(7)?.takeIf { it.isNotBlank() }
                        ?: RuleEntity.DEFAULT_EXTENSIONS,
                    suffix = f.getOrNull(8) ?: RuleEntity.DEFAULT_SUFFIX,
                    contentRename = f.getOrNull(9)?.toBooleanStrictOrNull() ?: true,
                    dstSubdirApp = f.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
                    minAgeDays = (f.getOrNull(11)?.toIntOrNull() ?: 0).coerceIn(0, 365),
                    minSizeKb = (f.getOrNull(12)?.toIntOrNull() ?: 0).coerceIn(0, 100_000),
                    // 导入的规则先停着，让用户确认后再开
                    enabled = false,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            .toList()
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun dec(v: String): String = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
}
