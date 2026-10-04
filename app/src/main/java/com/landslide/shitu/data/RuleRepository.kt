package com.landslide.shitu.data

import com.landslide.shitu.data.db.AppDatabase
import com.landslide.shitu.data.db.ItemEntity
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState

/** 规则 / 条目 / 日志的统一入口；只读写自己的数据库，不做文件操作。 */
class RuleRepository(private val db: AppDatabase) {

    private val ruleDao = db.ruleDao()
    private val itemDao = db.itemDao()
    private val logDao = db.logDao()

    // ---------- 规则 ----------

    suspend fun allRules(): List<RuleEntity> = ruleDao.all()

    suspend fun enabledRules(): List<RuleEntity> = ruleDao.enabledRules()

    suspend fun ruleById(id: Long): RuleEntity? = ruleDao.byId(id)

    suspend fun insertRule(rule: RuleEntity): Long = ruleDao.insert(rule)

    suspend fun updateRule(rule: RuleEntity) = ruleDao.update(rule.copy(updatedAt = System.currentTimeMillis()))

    suspend fun setRuleEnabled(rule: RuleEntity, enabled: Boolean) = ruleDao.update(
        rule.copy(
            enabled = enabled,
            state = if (enabled) RuleState.IDLE else RuleState.PAUSED_MANUAL,
            pauseReason = if (enabled) null else "已手动停用",
            consecutiveFailures = if (enabled) 0 else rule.consecutiveFailures,
            updatedAt = System.currentTimeMillis(),
        ),
    )

    suspend fun setRuleState(id: Long, state: RuleState, reason: String?) =
        ruleDao.setState(id, state, reason, System.currentTimeMillis())

    suspend fun deleteRule(id: Long) {
        ruleDao.delete(id)
        itemDao.pruneForRule(id)
        logDao.pruneForRule(id)
    }

    // ---------- 条目 ----------

    suspend fun recordItem(item: ItemEntity): Long = itemDao.upsert(item)

    suspend fun item(ruleId: Long, srcPath: String): ItemEntity? = itemDao.find(ruleId, srcPath)

    suspend fun itemById(id: Long): ItemEntity? = itemDao.byId(id)

    suspend fun countItems(ruleId: Long): Int = itemDao.countForRule(ruleId)

    suspend fun recentDone(limit: Int = 200): List<ItemEntity> = itemDao.recentDone(limit)

    suspend fun recentDoneForRule(ruleId: Long, limit: Int = 500): List<ItemEntity> =
        itemDao.recentDoneForRule(ruleId, limit)

    suspend fun deleteItem(id: Long) = itemDao.delete(id)

    // ---------- 日志 ----------

    suspend fun log(
        result: LogResult,
        ruleId: Long? = null,
        srcPath: String? = null,
        dstPath: String? = null,
        durationMs: Long = 0,
        message: String? = null,
        ts: Long = System.currentTimeMillis(),
    ) = logDao.insert(
        LogEntity(
            ts = ts, ruleId = ruleId, srcPath = srcPath, dstPath = dstPath,
            result = result, durationMs = durationMs, message = message,
        ),
    )

    suspend fun recentLogs(limit: Int = 500): List<LogEntity> = logDao.recent(limit)

    suspend fun filteredLogs(limit: Int, ruleId: Long?, result: LogResult?): List<LogEntity> =
        logDao.filtered(limit, ruleId, result?.name)

    suspend fun logCount(): Int = logDao.count()

    suspend fun allLogs(limit: Int = 50_000): List<LogEntity> = logDao.recent(limit)

    /** 容量控制（规格 §7）：日志 30 天 / 5 万条先到者为准；SKIPPED 条目 7 天后清理。 */
    suspend fun prune(keepDays: Int, keepCount: Int, now: Long = System.currentTimeMillis()) {
        logDao.pruneBefore(now - keepDays * 24L * 3600_000L)
        logDao.pruneToCount(keepCount)
        itemDao.pruneSkipped(now - 7L * 24 * 3600_000L)
    }
}
