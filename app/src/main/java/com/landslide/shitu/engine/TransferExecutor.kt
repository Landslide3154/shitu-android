package com.landslide.shitu.engine

import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.db.ItemEntity
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 单文件事务（规格 §6.4）：
 * 1) 目标已有同内容文件 → MOVE 删源 / COPY 跳过；2) 否则选不冲突的名字；
 * 3) mkdirs → move/copy；4) stat 校验；5) 返回 Item。
 *
 * 铁律：绝不覆盖、绝不先删后搬、任何失败源文件保持不动（MOVE 校验失败会回滚）。
 */
class TransferExecutor(
    private val bridge: FileBridge,
    private val namePolicy: NamePolicy = NamePolicy(),
) {

    companion object {
        private const val MAX_NAME_PROBES = 10_000

        fun timestamp(now: Long): String =
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))

        fun join(dir: String, name: String): String = dir.trimEnd('/') + "/" + name
    }

    suspend fun transfer(
        src: RemoteFile,
        srcPath: String,
        dstDir: String,
        /** 文件名后缀标签；null 或空白 = 不加后缀 */
        suffixTag: String?,
        mode: Mode,
        ruleId: Long,
        now: Long,
        /**
         * 「按内容命名」时给的名字主体（15 位、不含扩展名）。不为空时目标主体就用它，
         * 扩展名照旧取自源文件；为空则沿用原来的文件名。
         */
        contentBase: String? = null,
    ): ItemEntity {
        val ts = timestamp(now)
        val tag = suffixTag?.takeIf { it.isNotBlank() }
        val rawName = contentBase
            ?.takeIf { it.isNotBlank() }
            ?.let { it + namePolicy.extensionOf(src.name) }
            ?: src.name

        // ---- 1) 目标已有"同一个文件"：视为同一文件，不重复搬 ----
        // 按内容命名时，名字本身就是内容指纹：目标里已经有这个名字（大小一致）就认定是同一份。
        // 这里以 exists 为准、stat 只用来比大小：名字相同的文件内容必然相同（SHA-256），
        // 而 stat 偶发失败时若只信它会把这个名字当成不存在，于是同一张图被存成两份。
        val naturalName = namePolicy.candidates(rawName, tag, ts).first()
        val naturalPath = join(dstDir, naturalName)
        val naturalExists = runCatching { bridge.exists(naturalPath) }.getOrDefault(false)
        val naturalStat = if (naturalExists) runCatching { bridge.stat(naturalPath) }.getOrNull() else null
        val sameFile = if (!contentBase.isNullOrBlank()) {
            // 按内容命名：同名 + （拿不到大小时也当作命中，宁可不去重存也不重复存一份）
            naturalExists && (naturalStat == null || naturalStat.size == src.size)
        } else {
            naturalStat != null && naturalStat.size == src.size && naturalStat.mtimeMillis == src.mtimeMillis
        }
        if (sameFile) {
            return if (mode == Mode.MOVE) {
                val ok = runCatching { bridge.delete(srcPath) }.getOrDefault(false)
                item(
                    ruleId, srcPath, src, dstPath = naturalPath,
                    status = if (ok) ItemStatus.MOVED else ItemStatus.FAILED,
                    now = now,
                    error = if (ok) "目标已存在同内容文件" else "目标已存在同内容文件，但删源失败",
                )
            } else {
                item(
                    ruleId, srcPath, src, dstPath = naturalPath,
                    status = ItemStatus.SKIPPED, now = now, error = "目标已存在同内容文件",
                )
            }
        }

        // ---- 2) 选一个确实不存在的目标名（逐个实测，防止列举不全导致覆盖） ----
        var targetName: String? = null
        var probes = 0
        for (c in namePolicy.candidates(rawName, tag, ts)) {
            if (probes++ > MAX_NAME_PROBES) break
            val exists = runCatching { bridge.exists(join(dstDir, c)) }.getOrDefault(true)
            if (!exists) {
                targetName = c
                break
            }
        }
        if (targetName == null) {
            return item(ruleId, srcPath, src, null, ItemStatus.FAILED, now, "无法生成不冲突的目标文件名")
        }
        val dstPath = join(dstDir, targetName)

        // ---- 3) 搬运 ----
        runCatching { bridge.mkdirs(dstDir) }
        val moved = runCatching {
            if (mode == Mode.MOVE) bridge.move(srcPath, dstPath) else bridge.copy(srcPath, dstPath)
        }.getOrElse { t ->
            return item(ruleId, srcPath, src, null, ItemStatus.FAILED, now, "搬运调用失败：${t.message}")
        }
        if (!moved) {
            return item(ruleId, srcPath, src, null, ItemStatus.FAILED, now, "move/copy 失败；源保持不动")
        }

        // ---- 4) 校验 ----
        val dst = runCatching { bridge.stat(dstPath) }.getOrNull()
        val verified = dst != null && dst.size == src.size
        if (!verified) {
            // MOVE 模式下源可能已被 rename 走：回滚，绝不留下"搬没了又没搬成"的状态
            if (mode == Mode.MOVE) runCatching { bridge.move(dstPath, srcPath) }
            return item(ruleId, srcPath, src, null, ItemStatus.FAILED, now, "搬运后校验失败（大小不一致）")
        }

        return item(
            ruleId, srcPath, src, dstPath,
            if (mode == Mode.MOVE) ItemStatus.MOVED else ItemStatus.COPIED, now, null,
        )
    }

    private fun item(
        ruleId: Long,
        srcPath: String,
        src: RemoteFile,
        dstPath: String?,
        status: ItemStatus,
        now: Long,
        error: String?,
    ) = ItemEntity(
        ruleId = ruleId,
        srcPath = srcPath,
        srcSize = src.size,
        srcMtime = src.mtimeMillis,
        dstPath = dstPath,
        status = status,
        attemptCount = 1,
        lastError = error,
        processedAt = now,
    )
}
