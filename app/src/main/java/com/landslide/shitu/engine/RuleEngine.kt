package com.landslide.shitu.engine

import com.landslide.shitu.core.FileStability
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
    /**
     * 「写完就搬」：同一个文件两次观察到 size/mtime 都没变就可以搬，不必等满固定稳定期。
     * 为 null（单测默认 / 设置里关掉）时退回"固定稳定期"口径。
     */
    private val stability: FileStability? = null,
) {
    /** 扫描出口 */
    companion object {
        const val PAGE_SIZE = 500

        /**
         * 目标目录（及其子目录）里的文件一律不当候选。
         *
         * 否则"目标目录在源目录里面"这种配法会把刚搬进去的文件再搬一次：
         * 每轮给同一个文件加一次后缀，名字越滚越长（files/x.png → x_标签.png → x_标签_标签.png …）。
         */
        fun isInside(path: String, dir: String): Boolean =
            dir.isNotEmpty() && (path == dir || path.startsWith("$dir/"))
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

    /**
     * 一个文件要不要搬：
     * 1) 白名单命中；
     * 2) 不在目标目录里（否则"目标在源里面"会反复改名）；
     * 3) 过了固定稳定期 **或者** 用"大小不再变化"确认写完了。
     */
    private fun isCandidate(filter: ScanFilter, f: RemoteFile, dstRoot: String): Boolean {
        if (!filter.matches(f)) return false
        if (isInside(f.path, dstRoot)) return false
        val t = now()
        if (filter.isStable(f, t)) return true
        return settings.settleDetect &&
            stability?.settled(f.path, f.size, f.mtimeMillis, t) == true
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
        val dstRoot = rule.dstPath.trimEnd('/')
        var after: String? = null
        try {
            do {
                val page = bridge.list(srcRoot, rule.includeSubdirs, rule.maxDepth ?: 0, PAGE_SIZE, after)
                if (page.isEmpty()) break
                scanned += page.size
                after = page.last().path
                candidates += page.filter { f -> isCandidate(filter, f, dstRoot) }
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
            // 开了「按内容命名」就先算一次内容指纹（算不出来就退回原名，绝不因此不搬）
            val contentBase = if (rule.contentRename) {
                val got = runCatching { bridge.contentName(src.path) }
                val name = got.getOrDefault("")
                if (name.isBlank()) {
                    // 失败也留个痕：用户能在日志页看到"这次为什么还是原名"
                    recorder?.onLog(
                        LogResult.INFO, rule.id, src.path, null, 0,
                        "按内容命名失败，这次用原名（${got.exceptionOrNull()?.message ?: "算不出内容名"}）",
                    )
                }
                name.ifBlank { null }
            } else {
                null
            }
            val item = executor.transfer(
                src = src,
                srcPath = src.path,
                dstDir = rule.dstPath,
                suffixTag = tag,
                mode = rule.mode,
                ruleId = rule.id,
                now = fileStart,
                contentBase = contentBase,
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
