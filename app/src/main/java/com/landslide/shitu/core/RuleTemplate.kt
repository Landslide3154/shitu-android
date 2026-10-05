package com.landslide.shitu.core

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 自建模板：把一条规则的设置存下来，下次新建规则时一键套用。
 *
 * 存在 DataStore 的一小段文本里（见 [encode] / [decode]），不进 Room —— 免得为了一张表去改数据库版本。
 * 选了模板之后仍然会进编辑页，改完点「保存并开始搬运」才真的建出规则。
 */
data class RuleTemplate(
    val id: String,
    val name: String,
    val srcPath: String,
    val dstPath: String = RuleEntity.DEFAULT_DST,
    val includeSubdirs: Boolean = true,
    val maxDepth: Int? = null,
    val mode: Mode = Mode.MOVE,
    val intervalMinutes: Int = 5,
    val extensions: String = RuleEntity.DEFAULT_EXTENSIONS,
    val suffix: String = RuleEntity.DEFAULT_SUFFIX,
    val contentRename: Boolean = true,
) {
    /** 「新建规则」弹窗里的副标题：一眼看清这条模板会干什么 */
    fun detail(): String = buildString {
        append(srcPath)
        append(" → ")
        append(dstPath)
        append(" · ")
        append(if (mode == Mode.COPY) "复制" else "移动")
        append(" · 间隔 ")
        append(intervalMinutes)
        append(" 分钟")
        if (!includeSubdirs) append(" · 只看这一层")
        if (contentRename) append(" · 按内容命名")
    }

    fun toEntity(now: Long): RuleEntity = RuleEntity(
        name = name,
        srcPath = srcPath,
        includeSubdirs = includeSubdirs,
        maxDepth = maxDepth,
        dstPath = dstPath,
        mode = mode,
        intervalMinutes = intervalMinutes,
        extensions = extensions,
        suffix = suffix,
        contentRename = contentRename,
        createdAt = now,
        updatedAt = now,
    )

    companion object {
        /** 从一条规则（编辑页里正在改的那份）生成模板，名字由用户填 */
        fun fromRule(rule: RuleEntity, name: String): RuleTemplate = RuleTemplate(
            id = newId(),
            name = name,
            srcPath = rule.srcPath,
            dstPath = rule.dstPath,
            includeSubdirs = rule.includeSubdirs,
            maxDepth = rule.maxDepth,
            mode = rule.mode,
            intervalMinutes = rule.intervalMinutes,
            extensions = rule.extensions,
            suffix = rule.suffix,
            contentRename = rule.contentRename,
        )

        fun newId(): String = System.currentTimeMillis().toString(36) + "-" + (0..999999).random().toString(36)

        // —— 存 DataStore 的极简文本格式：每条模板一段，段内每行 "key=值"（值做百分号转义），段间空行分隔 ——

        fun encode(list: List<RuleTemplate>): String = list.joinToString("\n\n") { t ->
            listOf(
                "id" to t.id,
                "name" to t.name,
                "src" to t.srcPath,
                "dst" to t.dstPath,
                "sub" to t.includeSubdirs.toString(),
                "depth" to (t.maxDepth?.toString() ?: ""),
                "mode" to t.mode.name,
                "interval" to t.intervalMinutes.toString(),
                "ext" to t.extensions,
                "suffix" to t.suffix,
                "content" to t.contentRename.toString(),
            ).joinToString("\n") { (k, v) -> "$k=${enc(v)}" }
        }

        fun decode(text: String?): List<RuleTemplate> {
            if (text.isNullOrBlank()) return emptyList()
            return text.split("\n\n").mapNotNull { block ->
                val map = HashMap<String, String>()
                for (line in block.lines()) {
                    val i = line.indexOf('=')
                    if (i <= 0) continue
                    map[line.substring(0, i)] = dec(line.substring(i + 1))
                }
                val name = map["name"] ?: return@mapNotNull null
                val src = map["src"] ?: return@mapNotNull null
                RuleTemplate(
                    id = map["id"] ?: newId(),
                    name = name,
                    srcPath = src,
                    dstPath = map["dst"] ?: RuleEntity.DEFAULT_DST,
                    includeSubdirs = map["sub"]?.toBooleanStrictOrNull() ?: true,
                    maxDepth = map["depth"]?.toIntOrNull(),
                    mode = map["mode"]?.let { m -> Mode.entries.firstOrNull { it.name == m } } ?: Mode.MOVE,
                    intervalMinutes = map["interval"]?.toIntOrNull() ?: 5,
                    extensions = map["ext"] ?: RuleEntity.DEFAULT_EXTENSIONS,
                    suffix = map["suffix"] ?: RuleEntity.DEFAULT_SUFFIX,
                    // 老模板里没有这一项：按"新规则"的默认值 true 处理
                    contentRename = map["content"]?.toBooleanStrictOrNull() ?: true,
                )
            }
        }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        private fun dec(v: String): String = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
    }
}
