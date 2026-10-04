package com.landslide.shitu

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import com.landslide.shitu.core.AppLabel
import com.landslide.shitu.core.DirProbe
import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.LogExporter
import com.landslide.shitu.data.RuleRepository
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.data.db.AppDatabase
import com.landslide.shitu.data.db.ItemEntity
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.engine.RuleEngine
import com.landslide.shitu.engine.UndoService
import com.landslide.shitu.service.FallbackWorker
import com.landslide.shitu.service.Notifier
import com.landslide.shitu.shizuku.ShizukuBridge
import com.landslide.shitu.shizuku.ShizukuState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

/**
 * 应用级容器（规格 §6.1）：持有数据库、设置、桥、通知，并负责"跑一轮"的编排与记账。
 * 注意：它不做任何文件操作 —— 文件操作只经 [bridge] 交给 shell 身份的 UserService。
 */
class ShituApp : Application() {

    lateinit var db: AppDatabase
        private set
    lateinit var repo: RuleRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var bridge: ShizukuBridge
        private set
    lateinit var notifier: Notifier
        private set

    val namePolicy = NamePolicy()

    private val guards = ConcurrentHashMap<Long, LoopGuard>()
    private val runMutex = Mutex()

    /** 快节奏检测：每条规则一个"目录变没变"探测器（只 stat 目录，很便宜） */
    private val probes = ConcurrentHashMap<Long, DirProbe>()
    private val lastFastRun = ConcurrentHashMap<Long, Long>()

    /**
     * "等它过稳定期再看一次"的时间点。
     * 场景：文件刚创建就被发现，但没过稳定期（可能还在写），这一轮搬不走；
     * 如果不对齐一次重查，就得等下一个间隔（默认 5 分钟）才会再看它。
     */
    private val pendingRecheck = ConcurrentHashMap<Long, Long>()

    /** 系统媒体库（相册）刚有新增的时间戳——用它兜住"下载到公共目录"的情况 */
    @Volatile private var mediaHintAt = 0L

    private var mediaObserver: ContentObserver? = null

    @Volatile var watchMoved: Long = 0
        private set

    @Volatile var lastRunAt: Long? = null
        private set

    @Volatile var lastMessage: String? = null
        private set

    data class RunSummary(
        val rulesRun: Int = 0,
        val moved: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val message: String? = null,
    )

    data class UndoSummary(val done: Int = 0, val skipped: Int = 0, val notes: List<String> = emptyList())

    companion object {
        /** 同一规则两次快节奏检测的最小间隔，避免目录一变就反复扫 */
        private const val MIN_FAST_GAP_MS = 5_000L

        /** 相册新增信号的有效期 */
        private const val HINT_FRESH_MS = 5_000L

        /** 稳定期过后再多等一点，确保 mtime 已经不会再变 */
        private const val RECHECK_SLACK_MS = 3_000L
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        repo = RuleRepository(db)
        settings = SettingsStore(this)
        bridge = ShizukuBridge(this)
        notifier = Notifier(this)
        notifier.ensureChannels()
        FallbackWorker.schedule(this)
        registerShizukuListeners()
        registerMediaObserver()
    }

    /** 相册（MediaStore）新增/变化 → 给快节奏通道一个"有动静"的信号。 */
    private fun registerMediaObserver() {
        runCatching {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    mediaHintAt = System.currentTimeMillis()
                }
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
            mediaObserver = observer
        }
    }

    private fun registerShizukuListeners() {
        runCatching {
            Shizuku.addBinderReceivedListener { lastMessage = "Shizuku 已连接" }
            Shizuku.addBinderDeadListener {
                bridge.onBinderLost()
                lastMessage = "Shizuku binder 已断开"
            }
        }
    }

    suspend fun currentSettings(): Settings = settings.settings.first()

    /** 供 WatchService / Worker 调用：跑所有"到期且启用"的规则。 */
    suspend fun runDueRules(force: Boolean = false): RunSummary = runMutex.withLock {
        val s = currentSettings()
        if (s.lowBatteryPause && isLowPower(s)) return@withLock RunSummary(message = "低电量/省电模式暂停")

        if (bridge.state() != ShizukuState.READY && !bridge.bindWithRetry(1)) {
            val msg = "Shizuku 未就绪（${bridge.state().name}）"
            lastMessage = msg
            if (s.notifyEnabled) notifier.event("Shizuku 未就绪", "请打开 Shizuku 并授权拾图，然后点此回到 App 自检")
            return@withLock RunSummary(message = msg)
        }

        val now = System.currentTimeMillis()
        var rulesRun = 0
        var moved = 0
        var failed = 0
        var skipped = 0
        var sawFailure = false

        // 进程上次被杀可能留下"运行中"状态：超过 5 分钟就当作异常中断，放回待命
        for (stale in repo.enabledRules()) {
            if (stale.state == RuleState.RUNNING && (stale.lastRunAt == null || now - stale.lastRunAt!! > 5 * 60_000L)) {
                repo.setRuleState(stale.id, RuleState.IDLE, null)
            }
        }

        for (rule in repo.enabledRules()) {
            if (rule.state == RuleState.PAUSED_LOOP || rule.state == RuleState.PAUSED_ERROR) continue
            if (rule.state == RuleState.RUNNING) continue
            if (!force) {
                val last = rule.lastRunAt
                if (last != null && now - last < rule.intervalMinutes * 60_000L) continue
            }
            val outcome = runOne(rule, s)
            rulesRun++
            moved += outcome.moved
            failed += outcome.failed
            skipped += outcome.skipped
            if (outcome.failed > 0 && outcome.moved == 0) sawFailure = true
        }

        repo.prune(s.logKeepDays, s.logKeepCount)
        watchMoved += moved
        lastRunAt = System.currentTimeMillis()
        lastMessage = when {
            rulesRun == 0 -> "没有到期规则"
            else -> "已搬 $moved 张 · 失败 $failed · 跳过 $skipped"
        }
        RunSummary(rulesRun, moved, failed, skipped, lastMessage).also {
            if (sawFailure && s.notifyEnabled && failed >= 5) {
                notifier.event("搬运失败较多", "本轮失败 $failed 个文件，源文件都还在。可在日志页查看原因。")
            }
        }
    }

    /**
     * 快节奏通道：屏幕亮着时由 WatchService 每 fastIntervalSec 秒调一次。
     *
     * 只在"相关目录真的变了"（目录 mtime 变化）或"相册刚有新增"时才真正跑一轮，
     * 所以它把"几分钟才发现"压到"十几秒级"，而开销只是一堆 stat（不列文件、不读内容）。
     * 原有按间隔的调度、WorkManager 兜底一律保留——它们是这条通道失效时的后路。
     */
    suspend fun runFastLane(): RunSummary = runMutex.withLock {
        val s = currentSettings()
        if (!s.fastWhileActive) return@withLock RunSummary()
        if (s.lowBatteryPause && isLowPower(s)) return@withLock RunSummary()
        if (bridge.state() != ShizukuState.READY && !bridge.bindOnce()) return@withLock RunSummary()

        val now = System.currentTimeMillis()
        val hintFresh = now - mediaHintAt < HINT_FRESH_MS
        var rulesRun = 0
        var moved = 0
        var failed = 0
        var skipped = 0

        for (rule in repo.enabledRules()) {
            if (rule.state == RuleState.PAUSED_LOOP ||
                rule.state == RuleState.PAUSED_ERROR ||
                rule.state == RuleState.RUNNING
            ) {
                continue
            }
            if (now - (lastFastRun[rule.id] ?: 0L) < MIN_FAST_GAP_MS) continue

            val dirs = probeDirs(rule)
            if (dirs.isEmpty()) continue
            val probe = probes.getOrPut(rule.id) { DirProbe() }
            val dueForRecheck = pendingRecheck[rule.id]?.let { now >= it } == true
            val hot = hintFresh || dueForRecheck || runCatching {
                probe.changed(dirs) { p -> bridge.stat(p)?.mtimeMillis }.isNotEmpty()
            }.getOrDefault(false)
            if (!hot) continue

            lastFastRun[rule.id] = now
            val outcome = runOne(rule, s)
            rulesRun++
            moved += outcome.moved
            failed += outcome.failed
            skipped += outcome.skipped

            // 搬完目录 mtime 又变了：对齐基线，避免下一拍又白跑一轮
            runCatching { probe.sync(probeDirs(rule)) { p -> bridge.stat(p)?.mtimeMillis } }

            // 看到候选但一个都没搬（多半是没过稳定期）→ 等稳定期过后再来看一次
            if (outcome.moved == 0 && outcome.failed == 0 && outcome.scanned > 0) {
                pendingRecheck[rule.id] = now + s.stableSec * 1000L + RECHECK_SLACK_MS
            } else {
                pendingRecheck.remove(rule.id)
            }
        }

        if (rulesRun > 0) {
            watchMoved += moved
            lastRunAt = System.currentTimeMillis()
            lastMessage = "快速检测：搬 $moved 张"
        }
        RunSummary(rulesRun, moved, failed, skipped, if (rulesRun > 0) lastMessage else null)
    }

    /**
     * 需要盯着"变没变"的目录：规则源目录 + 最近搬成功过的文件所在目录。
     * 图片通常固定在同几个目录里出现，所以这一小撮目录足以代表整棵树。
     */
    private suspend fun probeDirs(rule: RuleEntity): List<String> {
        val dirs = LinkedHashSet<String>()
        val root = rule.srcPath.trimEnd('/')
        if (root.isNotBlank()) dirs += root
        runCatching {
            repo.recentDoneForRule(rule.id, 200).forEach { item ->
                item.srcPath.substringBeforeLast('/', "").takeIf { it.isNotBlank() }?.let { dirs += it }
            }
        }
        return dirs.toList()
    }

    /** 单个规则跑一轮（"立即运行"按钮与调度共用）。 */
    suspend fun runOne(rule: RuleEntity, s: Settings? = null): RuleEngine.RunResult {
        val cfg = s ?: currentSettings()
        repo.setRuleState(rule.id, RuleState.RUNNING, null)
        val engine = RuleEngine(
            bridge = bridge,
            namePolicy = namePolicy,
            settings = cfg,
            sourceAppLabel = ::sourceAppLabel,
        )
        val guard = guardFor(rule, cfg)
        val result = try {
            engine.runOnce(rule, guard, recorder)
        } catch (t: Throwable) {
            RuleEngine.RunResult(error = "运行异常：${t.message}")
        }

        val now = System.currentTimeMillis()
        val consecutive = if (result.failed > 0 && result.moved == 0) rule.consecutiveFailures + 1 else 0
        val paused = when {
            result.loopSuspected -> RuleState.PAUSED_LOOP
            consecutive >= cfg.failThreshold -> RuleState.PAUSED_ERROR
            else -> RuleState.IDLE
        }
        val reason = when (paused) {
            RuleState.PAUSED_LOOP ->
                "该目录里的文件被搬走后又被重新生成，疑似 App 会自动下载。建议把源目录改得更精确，或改用复制模式。"
            RuleState.PAUSED_ERROR -> "连续 $consecutive 轮失败，已自动暂停。请到日志页查看原因。"
            else -> null
        }

        repo.updateRule(
            rule.copy(
                state = paused,
                pauseReason = reason,
                lastRunAt = now,
                lastMoved = result.moved,
                lastFailed = result.failed,
                totalMoved = rule.totalMoved + result.moved,
                totalFailed = rule.totalFailed + result.failed,
                consecutiveFailures = consecutive,
                updatedAt = now,
            ),
        )

        if (paused != RuleState.IDLE && cfg.notifyEnabled) {
            notifier.event(
                if (paused == RuleState.PAUSED_LOOP) "规则已暂停（疑似循环）" else "规则已暂停（连续失败）",
                "「${rule.name}」：$reason",
            )
        }
        return result
    }

    private fun guardFor(rule: RuleEntity, s: Settings): LoopGuard =
        guards.getOrPut(rule.id) { LoopGuard(s.loopWindowMin * 60_000L, s.loopThreshold) }

    fun resetGuard(ruleId: Long) {
        guards.remove(ruleId)
    }

    /** 手动恢复被暂停的规则。 */
    suspend fun resumeRule(rule: RuleEntity) {
        resetGuard(rule.id)
        repo.updateRule(
            rule.copy(
                enabled = true,
                state = RuleState.IDLE,
                pauseReason = null,
                consecutiveFailures = 0,
            ),
        )
    }

    /** 批量开启 / 停止规则（规则列表里多选之后用），返回实际处理的条数。 */
    suspend fun setRulesEnabled(ids: Collection<Long>, enabled: Boolean): Int {
        var count = 0
        for (id in ids) {
            val rule = repo.ruleById(id) ?: continue
            if (enabled) {
                resetGuard(id)
                repo.updateRule(
                    rule.copy(
                        enabled = true,
                        state = RuleState.IDLE,
                        pauseReason = null,
                        consecutiveFailures = 0,
                    ),
                )
            } else {
                repo.setRuleEnabled(rule, false)
            }
            count++
        }
        return count
    }

    /** 「全部停止」：把当前所有启用的规则停掉（通知栏按钮用）。 */
    suspend fun stopAllRules(): Int =
        setRulesEnabled(repo.enabledRules().map { it.id }, enabled = false)

    private val recorder = object : RuleEngine.Recorder {
        override suspend fun onItem(item: ItemEntity) {
            repo.recordItem(item)
        }

        override suspend fun onLog(
            result: LogResult,
            ruleId: Long?,
            srcPath: String?,
            dstPath: String?,
            durationMs: Long,
            message: String?,
        ) {
            repo.log(result, ruleId, srcPath, dstPath, durationMs, message)
        }
    }

    /**
     * 来源 App 标签（规格 §6.5）：Android/data/<包名> → 系统显示名 → 包名末段；
     * 不是 App 私有目录时退回上一层目录名；都取不到就返回空串（不加后缀）。
     */
    fun sourceAppLabel(srcPath: String, ruleName: String): String {
        val pkg = AppLabel.packageFromPath(srcPath)
        if (pkg != null) {
            val info = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
                ?: return AppLabel.lastSegment(pkg)
            val label = runCatching { packageManager.getApplicationLabel(info).toString() }.getOrNull()
            return label?.takeIf { it.isNotBlank() } ?: AppLabel.lastSegment(pkg)
        }
        val parent = srcPath.trimEnd('/').substringBeforeLast('/', "").substringAfterLast('/')
        return if (parent.isBlank() || parent == "sdcard" || parent == "0") "" else parent
    }

    /** 撤回一条规则最近成功搬走的文件（规格 §6.6）。 */
    suspend fun undoRule(ruleId: Long, limit: Int = 500): UndoSummary {
        val undo = UndoService(bridge)
        var done = 0
        var skipped = 0
        val notes = ArrayList<String>()
        for (item in repo.recentDoneForRule(ruleId, limit)) {
            val r = undo.undo(item)
            if (r.ok) {
                done++
                repo.recordItem(
                    item.copy(
                        status = ItemStatus.PENDING,
                        dstPath = null,
                        lastError = "已撤回",
                        processedAt = System.currentTimeMillis(),
                    ),
                )
                repo.log(
                    LogResult.UNDONE, ruleId, item.srcPath, item.dstPath, 0,
                    "已撤回：${item.dstPath} → ${item.srcPath}",
                )
            } else {
                skipped++
                if (notes.size < 20) notes += (r.reason ?: "未知原因")
            }
        }
        return UndoSummary(done, skipped, notes)
    }

    /** 导出日志 CSV：先写 App 自己的外部目录，再用 shell 身份搬到 /sdcard/Download。 */
    suspend fun exportLogs(): String {
        val logs = repo.allLogs(50_000)
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, LogExporter.fileName())
        LogExporter.toCsv(logs, file)

        val target = "/sdcard/Download/${file.name}"
        val ok = runCatching {
            bridge.mkdirs("/sdcard/Download")
            bridge.move(file.absolutePath, target)
        }.getOrDefault(false)
        return if (ok) target else file.absolutePath
    }

    fun isLowPower(s: Settings): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && pm.isPowerSaveMode) return true
        val bm = getSystemService(BatteryManager::class.java) ?: return false
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return pct in 1..s.lowBatteryPct
    }
}
