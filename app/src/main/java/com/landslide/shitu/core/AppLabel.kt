package com.landslide.shitu.core

/**
 * 来源 App 标签（规格 §6.5 第 4 步）：从源路径里认出包名，取显示名；
 * 取不到显示名时用包名最后一段。
 */
object AppLabel {

    /** 从 `/sdcard/Android/data/<pkg>/...` 或 `/storage/emulated/0/Android/data/<pkg>/...` 里取包名。 */
    fun packageFromPath(path: String): String? {
        val marker = "/Android/data/"
        val i = path.indexOf(marker)
        if (i < 0) return null
        val rest = path.substring(i + marker.length)
        val pkg = rest.substringBefore('/')
        if (pkg.isBlank()) return null
        // 包名至少要有两段，且只含合法字符
        if (!pkg.contains('.')) return null
        return pkg
    }

    fun lastSegment(pkg: String): String = pkg.substringAfterLast('.').ifBlank { pkg }

    /**
     * 文件名后缀模板展开：
     * - 空白 → 不加后缀（返回 null）
     * - `{app}` → 用来源 App 标签；标签为空则退化为不加后缀
     * - 其他内容 → 原样作为后缀（分隔用的下划线由命名管线统一加，这里吃掉用户多写的 `_`/`-`）
     */
    fun expandSuffix(template: String, appLabel: String): String? = when {
        template.isBlank() -> null
        template.trim() == "{app}" -> appLabel.takeIf { it.isNotBlank() }
        else -> template.trim().trimStart('_', '-').takeIf { it.isNotBlank() }
    }
}
