package com.landslide.shitu.data

import com.landslide.shitu.data.db.AppDatabase
import com.landslide.shitu.data.db.CopiedEntity
import com.landslide.shitu.engine.ContentLedger

/**
 * 账本的持久化实现：一张 `copied` 表，按内容指纹去重。
 * 不随日志清理、也不随"跳过"记录清理，规则删掉也不会连带删除（内容确实已经搬进来过了）。
 */
class RoomContentLedger(db: AppDatabase) : ContentLedger {

    private val dao = db.copiedDao()

    override suspend fun seen(fingerprint: String): Boolean =
        fingerprint.isNotBlank() && dao.count(fingerprint) > 0

    override suspend fun remember(fingerprint: String, ruleId: Long, dstPath: String?, now: Long) {
        if (fingerprint.isBlank()) return
        dao.insert(CopiedEntity(fingerprint, ruleId, dstPath, now))
    }

    override suspend fun count(): Int = dao.total()

    override suspend fun clear(): Int = dao.clearAll()
}
