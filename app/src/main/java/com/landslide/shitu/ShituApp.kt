package com.landslide.shitu

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import com.landslide.shitu.core.AppLabel
import com.landslide.shitu.core.DirProbe
import com.landslide.shitu.core.FileStability
import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.core.RunConditions
import com.landslide.shitu.core.ScanFilter
import com.landslide.shitu.data.LogExporter
import com.landslide.shitu.data.LockClock
import com.landslide.shitu.data.RoomContentLedger
import com.landslide.shitu.data.BackupIo
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
import com.landslide.shitu.local.LocalFileBridge
import com.landslide.shitu.service.FallbackWorker
import com.landslide.shitu.service.Notifier
import com.landslide.shitu.service.UpdateNotice
import com.landslide.shitu.shizuku.FileBridge
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
 * 注意：它不做任何文件操作 —— 文件操作只经 [fileBridge] 交给 shell 身份或本地通道。
 */
class ShituApp : Application() {

    lateinit var db: AppDatabase
        private set
    lateinit var repo: RuleRepository
        private set
    /** 「已经复制过的内容」账本（全局，跨规则） */
    lateinit var ledger: RoomContentLedger
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var bridge: ShizukuBridge
        private set
    /** 没有 Shizuku 时的后备通道：要求系统「所有文件访问」权限。 */
    lateinit var localBridge: LocalFileBridge
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

    /** 连续重查次数：把重查间隔逐步拉长，避免一个"永远在写"的文件把我们拖成忙轮询 */
    private val recheckCount = ConcurrentHashMap<Long, Int>()

    /** 「写完就搬」的判定器：跨轮保存每个文件的 size/mtime 观察 */
    private val stability = FileStability()

    /** 系统媒体库（相册）刚有新增的时间戳——用它兜住"下载到公共目录"的情况 */
    @Volatile private var mediaHintAt = 0L

    private var mediaObserver: ContentObserver? = null

    @Volatile var watchMoved: Long = 0
        private set

    @Volatile var lastRunAt: Long? = null
        private set

    @Volatile var lastMessage: String? = null
        private set

    /**
     * WatchService 重启后从规则表恢复「累计已搬 / 上次运行」，通知里不会退回 0 和「—」。
     * 只影响通知显示，页面用的仍是规则自己存的统计。
     */
    suspend fun restoreWatchStats() {
        val rules = repo.allRules()
        watchMoved = rules.sumOf { it.totalMoved }
        lastRunAt = rules.mapNotNull { it.lastRunAt }.maxOrNull()
    }

    /** 后台/打开 App 时调一下：最多每 24 小时联网查一次更新，有新版发通知（失败静默）。 */
    suspend fun maybeCheckUpdate() = UpdateNotice.maybeNotify(this)

    data class RunSummary(
        val rulesRun: Int = 0,
        val moved: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val message: String? = null,
    )

    data class UndoSummary(val done: Int = 0, val skipped: Int = 0, val notes: List<String> = emptyList())

    /** 现在靠哪条通道搬文件。 */
    enum class Channel {
        /** Shizuku 就绪：能进 Android/data 里的 App 私有目录 */
        SHIZUKU,

        /** 本地「所有文件访问」：普通目录够用，Android/data 进不去 */
        LOCAL,

        /** 两条都没有：规则跑不了 */
        NONE,
    }

    companion object {
        /** 同一规则两次快节奏检测的最小间隔，避免目录一变就反复扫 */
        private const val MIN_FAST_GAP_MS = 5_000L

        /** 相册新增信号的有效期 */
        private const val HINT_FRESH_MS = 5_000L

        /** 稳定期过后再多等一点，确保 mtime 已经不会再变 */
        private const val RECHECK_SLACK_MS = 3_000L

        /** 重查的最小间隔（"写完就搬"模式下用） */
        private const val RECHECK_MIN_MS = 4_000L
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        repo = RuleRepository(db)
        ledger = RoomContentLedger(db)
        settings = SettingsStore(this)
        bridge = ShizukuBridge(this)
        localBridge = LocalFileBridge(this)
        notifier = Notifier(this)
        notifier.ensureChannels()
        FallbackWorker.schedule(this)
        registerShizukuListeners()
        registerMediaObserver()
    }

    // ---------- 通道 ----------

    fun channel(): Channel = when {
        bridge.state() == ShizukuState.READY -> Channel.SHIZUKU
        localBridge.available() -> Channel.LOCAL
        else -> Channel.NONE
    }

    /**
     * 引擎用的桥：Shizuku 就绪就用它（顺带能进 Android/data），否则退回本地通道。
     * 两条都没有时返回 Shizuku 桥——真正的调用方应该先 [ensureChannel]，好把"没有通道"讲清楚。
     */
    val fileBridge: FileBridge
        get() = if (bridge.state() == ShizukuState.READY) bridge else localBridge

    fun hasChannel(): Boolean = channel() != Channel.NONE

    /**
     * 跑之前确认通道：先试着连 Shizuku，连不上再退回本地「所有文件访问」。
     * 返回 false 表示两条路都不通（没装/没启动 Shizuku，也没给文件权限）。
     */
    suspend fun ensureChannel(): Boolean {
        if (bridge.state() == ShizukuState.READY) return true
        if (bridge.bindOnce()) return true
        return localBridge.available()
    }

    /** 通道的界面文案（自检、提示条共用）。 */
    fun channelLine(): String = when (channel()) {
        Channel.SHIZUKU -> "Shizuku 通道（可读写 Android/data）"
        Channel.LOCAL -> "本地通道（仅普通目录，Android/data 需要 Shizuku）"
        Channel.NONE -> "没有可用的通道：请连上 Shizuku，或在设置里打开「所有文件访问」"
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

    // ---------- 运行条件（设置里多选；默认全关 = 随时都搬） ----------

    /**
     * 当前有哪些条件没满足。空列表 = 可以跑。
     * 手动触发（打开 App、点「立即扫一次」）走 force 分支，不受这里限制。
     */
    fun unmetConditions(s: Settings, nowHour: Int = currentHour()): List<String> {
        val screenOn = isScreenUsable()
        return RunConditions.unmet(
            settings = s,
            charging = isCharging(),
            screenOn = screenOn,
            batteryPct = batteryPct(),
            lockedForMs = if (screenOn) 0L else LockClock.lockedForMs(this),
            nowHour = nowHour,
        )
    }

    private fun currentHour(): Int =
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

    /**
     * 是否「插着充电器」。
     *
     * 注意不能只看 [BatteryManager.isCharging]：本机实测充电保护/旁路供电时它会返回 false，
     * 但电池状态仍是 plugged=1 / status=2。所以以「是否插电」为准（这也是设置页文案的意思）。
     */
    fun isCharging(): Boolean = runCatching {
        val bm = getSystemService(BatteryManager::class.java)
        if (bm?.isCharging == true) return true
        val sticky = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }.getOrDefault(false)

    fun batteryPct(): Int = runCatching {
        getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }.getOrDefault(-1)

    // ---------- 试运行预览 / 统计 / 规则导入导出 ----------

    data class PreviewResult(
        val candidates: Int = 0,
        val bytes: Long = 0,
        val existingTarget: Int = 0,
        val limited: Boolean = false,
        val error: String? = null,
    )

    /**
     * 保存前先看一眼「这条规则大概会搬多少张」。
     * 只做列举与过滤，不算内容指纹（不然几万张图要算很久）；目标重名只按原文件名粗查。
     */
    suspend fun previewRule(rule: RuleEntity, maxList: Int = 2_000, maxCheck: Int = 200): PreviewResult {
        val cfg = currentSettings()
        val filter = ScanFilter(
            extensions = rule.extensions.split(',').toSet(),
            stableSeconds = cfg.stableSec,
            maxAgeDays = rule.minAgeDays,
            minSizeKb = rule.minSizeKb,
        )
        val srcRoot = rule.srcPath.trimEnd('/')
        val dstRoot = rule.dstPath.trimEnd('/')
        var candidates = 0
        var bytes = 0L
        var existing = 0
        var checked = 0
        var after: String? = null
        val deadline = System.currentTimeMillis() + cfg.scanBudgetSec * 1000L
        try {
            do {
                val page = fileBridge.list(srcRoot, rule.includeSubdirs, rule.maxDepth ?: 0, 200, after)
                if (page.isEmpty()) break
                after = page.last().path
                val t = System.currentTimeMillis()
                for (f in page) {
                    if (!filter.matches(f, t) || RuleEngine.isInside(f.path, dstRoot)) continue
                    candidates++
                    bytes += f.size
                    if (checked < maxCheck) {
                        checked++
                        if (runCatching { fileBridge.exists("$dstRoot/${f.name}") }.getOrDefault(false)) {
                            existing++
                        }
                    }
                }
            } while (page.size == 200 && candidates < maxList && System.currentTimeMillis() < deadline)
        } catch (t: Throwable) {
            return PreviewResult(error = "扫描失败：${t.message}")
        }
        return PreviewResult(
            candidates = candidates,
            bytes = bytes,
            existingTarget = existing,
            limited = candidates >= maxList,
        )
    }

    data class Stats(
        val weekMoved: Int = 0,
        val weekFailed: Int = 0,
        val weekBytes: Long = 0,
        val topApps: List<Pair<String, Int>> = emptyList(),
    )

    /** 本周（周一起算）的搬运统计（按内容来源目录粗分组，够看个大概）。 */
    suspend fun stats(): Stats {
        val since = weekStartMillis()
        val done = runCatching { repo.recentDone(2_000) }.getOrDefault(emptyList())
            .filter { (it.processedAt ?: 0) >= since }
        val apps = done.groupingBy { sourceAppLabel(it.srcPath, "") .ifBlank { "其它" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key to it.value }
        return Stats(
            weekMoved = done.size,
            weekFailed = runCatching { repo.countFailedSince(since) }.getOrDefault(0),
            weekBytes = done.sumOf { it.srcSize },
            topApps = apps,
        )
    }

    /** 本周一 00:00（本地时区）的时间戳；算不出来就退回「最近 7 天」。 */
    private fun weekStartMillis(): Long = runCatching {
        val cal = java.util.Calendar.getInstance()
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.getOrDefault(System.currentTimeMillis() - 7L * 24 * 3600_000L)

    /** 导出规则 + 各项设置到 /sdcard/Download（拿不到路径时退回 App 自己的外部目录）。 */
    suspend fun exportBackup(): String {
        val text = BackupIo.encode(repo.allRules(), currentSettings())
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, BackupIo.fileName())
        file.writeText(text)
        val target = "/sdcard/Download/${file.name}"
        val ok = runCatching {
            fileBridge.mkdirs("/sdcard/Download")
            fileBridge.move(file.absolutePath, target)
        }.getOrDefault(false)
        return if (ok) target else file.absolutePath
    }

    /** 导入备份：规则一律建成暂停状态；文件里带设置就一并恢复。返回导入的规则条数。 */
    suspend fun importBackup(text: String): Int {
        val backup = BackupIo.decode(text)
        backup.settings?.let { runCatching { settings.apply(it) } }
        var n = 0
        for (r in backup.rules) {
            repo.insertRule(r)
            n++
        }
        return n
    }

    /** 供 WatchService / Worker 调用：跑所有"到期且启用"的规则。 */
    suspend fun runDueRules(force: Boolean = false): RunSummary = runMutex.withLock {
        val s = currentSettings()
        // 运行条件（多选场景 = 满足任一；电量是否决）：手动触发（打开 App、「立即扫一次」）不受限
        if (!force) {
            val unmet = unmetConditions(s)
            if (unmet.isNotEmpty()) {
                val msg = "等运行条件：" + unmet.joinToString("、")
                lastMessage = msg
                return@withLock RunSummary(message = msg)
            }
        }

        if (!ensureChannel()) {
            val msg = "没有可用的搬图通道：Shizuku 没就绪，也没给「所有文件访问」权限"
            lastMessage = msg
            if (s.notifyEnabled) {
                notifier.event("还没有搬图通道", "连上 Shizuku，或在拾图「设置 → 存储访问」里打开「所有文件访问」。")
            }
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
        if (unmetConditions(s).isNotEmpty()) return@withLock RunSummary()
        if (!ensureChannel()) return@withLock RunSummary()

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
                probe.changed(dirs) { p -> fileBridge.stat(p)?.mtimeMillis }.isNotEmpty()
            }.getOrDefault(false)
            if (!hot) continue

            lastFastRun[rule.id] = now
            val outcome = runOne(rule, s)
            rulesRun++
            moved += outcome.moved
            failed += outcome.failed
            skipped += outcome.skipped

            // 搬完目录 mtime 又变了：对齐基线，避免下一拍又白跑一轮
            runCatching { probe.sync(probeDirs(rule)) { p -> fileBridge.stat(p)?.mtimeMillis } }

            // 看到候选但一个都没搬（多半是文件还在写）→ 过一会儿再看一次
            if (outcome.moved == 0 && outcome.failed == 0 && outcome.scanned > 0) {
                val n = (recheckCount[rule.id] ?: 0) + 1
                recheckCount[rule.id] = n
                val base = if (s.settleDetect) {
                    maxOf(s.settleGapSec * 1000L + 1_500L, RECHECK_MIN_MS)
                } else {
                    s.stableSec * 1000L + RECHECK_SLACK_MS
                }
                // 逐次翻倍（4s、8s、16s、32s…），但不超过"稳定期兜底"那条线，
                // 免得一个永远在长大的文件把我们拖成忙轮询
                val grown = base * (1L shl minOf(n - 1, 3))
                val cap = maxOf(s.stableSec * 1000L + RECHECK_SLACK_MS, base)
                pendingRecheck[rule.id] = now + grown.coerceAtMost(cap)
            } else {
                pendingRecheck.remove(rule.id)
                recheckCount.remove(rule.id)
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
            bridge = fileBridge,
            namePolicy = namePolicy,
            settings = cfg,
            sourceAppLabel = ::sourceAppLabel,
            stability = if (cfg.settleDetect) stability else null,
            ledger = ledger,
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
        val undo = UndoService(fileBridge)
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

    /** 导出日志 CSV：先写 App 自己的外部目录，再搬到 /sdcard/Download。 */
    suspend fun exportLogs(): String {
        val logs = repo.allLogs(50_000)
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, LogExporter.fileName())
        LogExporter.toCsv(logs, file)

        val target = "/sdcard/Download/${file.name}"
        val ok = runCatching {
            fileBridge.mkdirs("/sdcard/Download")
            fileBridge.move(file.absolutePath, target)
        }.getOrDefault(false)
        return if (ok) target else file.absolutePath
    }

    /** 屏幕亮着**且已解锁**：只有这种状态才算"用户在用手机"。 */
    fun isScreenUsable(): Boolean = runCatching {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        pm?.isInteractive == true && km?.isKeyguardLocked != true
    }.getOrDefault(true)
}
