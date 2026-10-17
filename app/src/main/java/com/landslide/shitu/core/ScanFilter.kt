package com.landslide.shitu.core

import com.landslide.shitu.shizuku.RemoteFile

/**
 * 候选过滤（规格 §9.1/§9.3）：扩展名白名单 ∧ 已是文件 ∧ 非软链（+ 稳定期）
 * ∧ 最近 N 天内修改 ∧ 不小于 N KB。大小写不敏感。
 *
 * 拆成两步，方便调用方加"按文件大小不再变化判定写完"的第二道判定：
 * - [matches]：是不是我们要处理的文件
 * - [isStable]：按"创建/修改后过了 N 秒"的老规矩（兜底）
 *
 * 后两个条件（天数/大小）是 0.12.5 加的：用户常需要「只搬最近几天的图」「跳过缩略图」，
 * 0 表示不限，老规则不受影响。
 */
class ScanFilter(
    extensions: Set<String>,
    private val stableSeconds: Int,
    /** 只搬最近 N 天内修改过的文件；0 = 不限 */
    private val maxAgeDays: Int = 0,
    /** 只搬不小于 N KB 的文件（正好 N KB 会通过）；0 = 不限 */
    private val minSizeKb: Int = 0,) {
    private val exts = extensions.map { it.trim().lowercase().removePrefix(".") }
        .filter { it.isNotEmpty() }
        .toSet()

    /** 扩展名在白名单里、不是目录/软链，且满足天数/大小条件。 */
    fun matches(file: RemoteFile, nowMillis: Long): Boolean {
        if (file.isDirectory || file.isSymlink) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in exts) return false
        if (minSizeKb > 0 && file.size < minSizeKb * 1024L) return false
        if (maxAgeDays > 0 && nowMillis - file.mtimeMillis > maxAgeDays * 86_400_000L) return false
        return true
    }

    /** 按固定稳定期判断（兜底路径）。 */
    fun isStable(file: RemoteFile, nowMillis: Long): Boolean =
        nowMillis - file.mtimeMillis >= stableSeconds * 1000L

    /** 老口径：白名单 + 固定稳定期。 */
    fun accept(file: RemoteFile, nowMillis: Long): Boolean =
        matches(file, nowMillis) && isStable(file, nowMillis)
}
