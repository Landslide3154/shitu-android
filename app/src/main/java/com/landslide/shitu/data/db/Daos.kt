package com.landslide.shitu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RuleDao {
    @Insert
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun byId(id: Long): RuleEntity?

    @Query("SELECT * FROM rules ORDER BY id")
    suspend fun all(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY id")
    suspend fun enabledRules(): List<RuleEntity>

    @Query("UPDATE rules SET state = :state, pauseReason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun setState(id: Long, state: RuleState, reason: String?, now: Long)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity): Long

    @Query("SELECT * FROM items WHERE ruleId = :ruleId AND srcPath = :srcPath")
    suspend fun find(ruleId: Long, srcPath: String): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE ruleId = :ruleId")
    suspend fun countForRule(ruleId: Long): Int

    /**
     * 这个源文件（同规则 + 同路径 + 同大小 + 同 mtime）以前是否已经成功搬走/复制过。
     * 只看 MOVED / COPIED：失败和跳过都还要再处理。
     */
    @Query(
        "SELECT COUNT(*) FROM items WHERE ruleId = :ruleId AND srcPath = :srcPath " +
            "AND srcSize = :srcSize AND srcMtime = :srcMtime AND status IN ('MOVED','COPIED')",
    )
    suspend fun countHandled(ruleId: Long, srcPath: String, srcSize: Long, srcMtime: Long): Int

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): ItemEntity?

    @Query(
        "SELECT * FROM items WHERE status IN ('MOVED','COPIED') " +
            "ORDER BY processedAt DESC LIMIT :limit",
    )
    suspend fun recentDone(limit: Int): List<ItemEntity>

    @Query(
        "SELECT * FROM items WHERE ruleId = :ruleId AND status IN ('MOVED','COPIED') " +
            "ORDER BY processedAt DESC LIMIT :limit",
    )
    suspend fun recentDoneForRule(ruleId: Long, limit: Int): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status = 'SKIPPED' AND processedAt < :before")
    suspend fun staleSkipped(before: Long): List<ItemEntity>

    @Query("DELETE FROM items WHERE status = 'SKIPPED' AND processedAt < :before")
    suspend fun pruneSkipped(before: Long): Int

    /**
     * 清空「复制记录」账本时一起清掉复制模式的完成条目。
     * 否则 `countHandled` 还会命中这些老条目，同一张图在账本清空后仍然不会被重新复制。
     */
    @Query(
        "DELETE FROM items WHERE ruleId IN (SELECT id FROM rules WHERE mode = 'COPY') " +
            "AND status IN ('MOVED','COPIED')",
    )
    suspend fun deleteDoneForCopyRules(): Int

    @Query("SELECT * FROM items WHERE ruleId = :ruleId")
    suspend fun allForRule(ruleId: Long): List<ItemEntity>

    /** 最近 7 天失败次数（统计卡用）。 */
    @Query("SELECT COUNT(*) FROM items WHERE status = 'FAILED' AND processedAt >= :since")
    suspend fun countFailedSince(since: Long): Int

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM items WHERE ruleId = :ruleId")
    suspend fun pruneForRule(ruleId: Long)
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("SELECT * FROM logs ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LogEntity>

    @Query(
        "SELECT * FROM logs WHERE (:ruleId IS NULL OR ruleId = :ruleId) " +
            "AND (:result IS NULL OR result = :result) ORDER BY ts DESC LIMIT :limit",
    )
    suspend fun filtered(limit: Int, ruleId: Long?, result: String?): List<LogEntity>

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun count(): Int

    @Query("DELETE FROM logs")
    suspend fun clearAll()

    @Query("DELETE FROM logs WHERE ts < :before")
    suspend fun pruneBefore(before: Long)

    @Query("DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY ts DESC LIMIT -1 OFFSET :keep)")
    suspend fun pruneToCount(keep: Int)

    @Query("DELETE FROM logs WHERE ruleId = :ruleId")
    suspend fun pruneForRule(ruleId: Long)
}

/** 「已经复制过的内容」账本：全局，不随规则/日志清理。 */
@Dao
interface CopiedDao {
    @Query("SELECT COUNT(*) FROM copied WHERE fingerprint = :fingerprint")
    suspend fun count(fingerprint: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CopiedEntity)

    @Query("SELECT COUNT(*) FROM copied")
    suspend fun total(): Int

    /** 复制记录列表（设置页多选清除用），最近的在前。 */
    @Query("SELECT * FROM copied ORDER BY firstSeenAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CopiedEntity>

    @Query("DELETE FROM copied WHERE fingerprint IN (:fingerprints)")
    suspend fun deleteSelected(fingerprints: List<String>): Int

    @Query("DELETE FROM copied")
    suspend fun clearAll(): Int
}
