package com.landslide.shitu.engine

import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.core.RateLimiter
import com.landslide.shitu.core.ScanFilter
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.ItemEntity
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile

/**
 * 一轮 Run 的编排（规格 §6.3 / §9.1）：
 * 扫描（时间预算 + 游标分页）→ 过滤 → 按 mtime 升序 → 三重限速 → 单文件事务 → 记账 → 收尾。
 */
class RuleEngine(
    private val bridge: FileBridge,
    private val namePolicy: NamePolicy = NamePolicy(),
    private val settings: Settings,
    private val now: () -> Long = System::currentTimeMillis,
    private val sourceAppLabel: (srcPath: String, ruleName: String) -> String = { _, rule -> rule },
) {
    companion object {
        const val PAGE_SIZE = 500
    }

    var lastState: RuleState = RuleState.IDLE
        private set

    data class RunResult(
        val scanned: Int = 0,
        val moved: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val loopSuspected: Boolean = false,
        val error: String? = null,
        val durationMs: Long = 0,
    )

    /** 记账出口；由 ShituApp 用 RuleRepository 适配，单测可不传。 */
    interface Recorder {
        suspend fun onItem(item: ItemEntity)

        suspend fun onLog(
            result: LogResult,
            ruleId: Long?,
            srcPath: String?,
            dstPath: String?,
            durationMs: Long,
            message: String?,
        )
    }

    suspend fun runOnce(rule: RuleEntity, guard: LoopGuard, recorder: Recorder? = null): RunResult {
        val t0 = now()
        val filter = ScanFilter(rule.extensions.split(',').toSet(), settings.stableSec)
        val limiter = RateLimiter(settings.maxPerRun, settings.maxPerMinute, settings.maxRunSec * 1000L)
        val executor = TransferExecutor(bridge, namePolicy)
        val srcRoot = rule.srcPath.trimEnd('/')

        // ---- 1) 扫描（迭代 DFS + 游标分页，受时间预算约束）----
        val candidates = ArrayList<RemoteFile>()
        var scanned = 0
        var scanError: String? = null
        val deadline = t0 + settings.scanBudgetSec * 1000L
        var after: String? = null
        try {
            do {
                val page = bridge.list(srcRoot, rule.includeSubdirs, rule.maxDepth ?: 0, PAGE_SIZE, after)
                if (page.isEmpty()) break
                scanned += page.size
                after = page.last().path
                candidates += page.filter { filter.accept(it, now()) }
            } while (page.size == PAGE_SIZE && now() < deadline)
        } catch (t: Throwable) {
            scanError = "扫描失败：${t.message}"
            recorder?.onLog(LogResult.INFO, rule.id, srcRoot, null, 0, scanError)
        }

        // ---- 2) 排序（老的先搬）→ 3) 限速执行 ----
        var moved = 0
        var failed = 0
        var skipped = 0
        var loop = false

        for (src in candidates.sortedBy { it.mtimeMillis }) {
            if (!limiter.tryAcquire(now())) break
            val fileStart = now()
            val tag = com.landslide.shitu.core.AppLabel.expandSuffix(
                rule.suffix,
                sourceAppLabel(src.path, rule.name),
            )
            val item = executor.transfer(
                src = src,
                srcPath = src.path,
                dstDir = rule.dstPath,
                suffixTag = tag,
                mode = rule.mode,
                ruleId = rule.id,
                now = fileStart,
            )
            recorder?.onItem(item)
            val cost = now() - fileStart
            when (item.status) {
                ItemStatus.MOVED, ItemStatus.COPIED -> {
                    moved++
                    recorder?.onLog(
                        if (item.status == ItemStatus.MOVED) LogResult.MOVED else LogResult.COPIED,
                        rule.id, src.path, item.dstPath, cost, item.lastError,
                    )
                    if (guard.record(src.name, fileStart)) loop = true
                }
                ItemStatus.SKIPPED -> {
                    skipped++
                    recorder?.onLog(LogResult.SKIPPED, rule.id, src.path, item.dstPath, cost, item.lastError)
                }
                ItemStatus.FAILED -> {
                    failed++
                    recorder?.onLog(LogResult.FAILED, rule.id, src.path, item.dstPath, cost, item.lastError)
                }
                else -> Unit
            }
            if (loop) break
        }
        limiter.endRun()

        lastState = if (loop) RuleState.PAUSED_LOOP else RuleState.IDLE
        return RunResult(
            scanned = scanned,
            moved = moved,
            failed = failed,
            skipped = skipped,
            loopSuspected = loop,
            error = scanError,
            durationMs = now() - t0,
        )
    }
}
