package com.landslide.shitu.data

import com.landslide.shitu.data.db.AppDatabase
import com.landslide.shitu.data.db.CopiedEntity
import com.landslide.shitu.engine.ContentLedger

/**
 * 账本的持久化实现：
 * - 内容去重靠 `copied` 表（按内容指纹，全局，不随日志清理、规则删掉也不删）；
 * - "这个文件以前处理成功过没有"直接查 `items` 表（同规则 + 同路径 + 同大小 + 同 mtime）。
 */
class RoomContentLedger(db: AppDatabase) : ContentLedger {

    private val dao = db.copiedDao()
    private val itemDao = db.itemDao()

    override suspend fun seen(fingerprint: String): Boolean =
        fingerprint.isNotBlank() && dao.count(fingerprint) > 0

    /**
     * 借 `items` 表回答"同一个源文件以前是不是已经处理成功过"。
     * 只有同规则 + 同路径 + 同大小 + 同 mtime 才命中；文件改过（size/mtime 变）就查不中，会重新处理。
     */
    override suspend fun handledBefore(ruleId: Long, srcPath: String, size: Long, mtime: Long): Boolean =
        srcPath.isNotBlank() && itemDao.countHandled(ruleId, srcPath, size, mtime) > 0

    override suspend fun remember(fingerprint: String, ruleId: Long, dstPath: String?, now: Long) {
        if (fingerprint.isBlank()) return
        dao.insert(CopiedEntity(fingerprint, ruleId, dstPath, now))
    }

    override suspend fun count(): Int = dao.total()

    override suspend fun clear(): Int = dao.clearAll()
}
