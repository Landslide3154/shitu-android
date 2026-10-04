package com.landslide.shitu.core

import com.landslide.shitu.shizuku.RemoteFile

/**
 * 候选过滤（规格 §9.1/§9.3）：扩展名白名单 ∧ 已过稳定期 ∧ 非目录 ∧ 非软链。
 * 大小写不敏感。
 */
class ScanFilter(
    extensions: Set<String>,
    private val stableSeconds: Int,
) {
    private val exts = extensions.map { it.trim().lowercase().removePrefix(".") }
        .filter { it.isNotEmpty() }
        .toSet()

    fun accept(file: RemoteFile, nowMillis: Long): Boolean {
        if (file.isDirectory || file.isSymlink) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in exts) return false
        return nowMillis - file.mtimeMillis >= stableSeconds * 1000L
    }
}
