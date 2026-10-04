package com.landslide.shitu.engine

import com.landslide.shitu.data.db.ItemEntity
import com.landslide.shitu.shizuku.FileBridge

/**
 * 撤回（规格 §6.6）：只撤"本 App 搬的、且目标未被改动、且源位置仍空"的文件。
 * 逐文件事务，失败不回滚已成功的部分。
 */
class UndoService(private val bridge: FileBridge) {

    data class Result(val ok: Boolean, val reason: String? = null)

    suspend fun undo(item: ItemEntity): Result {
        val dstPath = item.dstPath
            ?: return Result(false, "这条记录没有目标路径（未成功搬移）")
        return undo(item.srcPath, dstPath, item.srcSize)
    }

    suspend fun undo(srcPath: String, dstPath: String, expectedSize: Long): Result {
        val dst = runCatching { bridge.stat(dstPath) }.getOrNull()
            ?: return Result(false, "目标已不存在：$dstPath")
        if (dst.size != expectedSize) {
            return Result(false, "目标已被改动（大小不一致），跳过：$dstPath")
        }
        if (runCatching { bridge.exists(srcPath) }.getOrDefault(true)) {
            return Result(false, "源位置已被新文件占用，跳过：$srcPath")
        }
        val ok = runCatching { bridge.move(dstPath, srcPath) }.getOrDefault(false)
        return if (ok) Result(true) else Result(false, "移动回源失败：$dstPath")
    }

    /** 兼容规格里的布尔写法。 */
    suspend fun undoBoolean(srcPath: String, dstPath: String, expectedSize: Long): Boolean =
        undo(srcPath, dstPath, expectedSize).ok
}
