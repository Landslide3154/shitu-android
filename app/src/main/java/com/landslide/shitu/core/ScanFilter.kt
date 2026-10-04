package com.landslide.shitu.core

import com.landslide.shitu.shizuku.RemoteFile

/**
 * 候选过滤（规格 §9.1/§9.3）：扩展名白名单 ∧ 已是文件 ∧ 非软链（+ 稳定期）。
 * 大小写不敏感。
 *
 * 拆成两步，方便调用方加"按文件大小不再变化判定写完"的第二道判定：
 * - [matches]：是不是我们要处理的文件
 * - [isStable]：按"创建/修改后过了 N 秒"的老规矩（兜底）
 */
class ScanFilter(
    extensions: Set<String>,
    private val stableSeconds: Int,
) {
    private val exts = extensions.map { it.trim().lowercase().removePrefix(".") }
        .filter { it.isNotEmpty() }
        .toSet()

    /** 扩展名在白名单里，且不是目录/软链。 */
    fun matches(file: RemoteFile): Boolean {
        if (file.isDirectory || file.isSymlink) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return ext in exts
    }

    /** 按固定稳定期判断（兜底路径）。 */
    fun isStable(file: RemoteFile, nowMillis: Long): Boolean =
        nowMillis - file.mtimeMillis >= stableSeconds * 1000L

    /** 老口径：白名单 + 固定稳定期。 */
    fun accept(file: RemoteFile, nowMillis: Long): Boolean =
        matches(file) && isStable(file, nowMillis)
}
