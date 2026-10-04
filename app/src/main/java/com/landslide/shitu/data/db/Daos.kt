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

    @Query("DELETE FROM logs WHERE ts < :before")
    suspend fun pruneBefore(before: Long)

    @Query("DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY ts DESC LIMIT -1 OFFSET :keep)")
    suspend fun pruneToCount(keep: Int)

    @Query("DELETE FROM logs WHERE ruleId = :ruleId")
    suspend fun pruneForRule(ruleId: Long)
}
