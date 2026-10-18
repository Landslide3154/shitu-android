package com.landslide.shitu.service

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.landslide.shitu.ShituApp
import com.landslide.shitu.data.LockClock
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.shizuku.display
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 常驻服务（规格 §6.7）：specialUse 类型前台服务。
 *
 * 两种节奏：
 * - **屏幕亮着且解锁** → 每 fastIntervalSec 秒跑一次「快节奏检测」（只 stat 目录，真变了才扫）；
 * - **锁屏/息屏** → 每 30 秒跑一次同样的快节奏检测 + 按规则间隔的完整扫描，另加 WorkManager 15 分钟兜底。
 */
class WatchService : Service() {

    companion object {
        const val ACTION_STOP_ALL = "com.landslide.shitu.action.STOP_ALL"
        const val ACTION_RUN_NOW = "com.landslide.shitu.action.RUN_NOW"
        const val ACTION_ALARM_SCAN = "com.landslide.shitu.action.ALARM_SCAN"
        const val TICK_MS = 30_000L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
            }
        }

        /** 闹钟到点：叫醒服务跑一轮扫描（服务已在运行时就只是发一个 action）。 */
        fun startForScan(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WatchService::class.java).setAction(ACTION_ALARM_SCAN),
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: ShituApp

    @Volatile private var busy = false

    @Volatile private var screenUsable = false

    /** 上一次发出去的通知正文：内容没变就不重复 startForeground（快节奏分支每几秒会刷新一次）。 */
    @Volatile private var lastNotified: String? = null

    /** 上一次「到期规则」完整扫描的时间：快节奏只 stat 目录，完整扫描必须独立按节奏跑。 */
    @Volatile private var lastDueScanAt = 0L

    /** 屏幕开关/解锁：只在服务存活期间用运行时注册（清单里注册收不到这类广播）。 */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                screenUsable = false
                LockClock.markLocked(this@WatchService)
                return
            }
            // 刚亮屏那一瞬间 KeyguardManager 还没报告"已锁"，等 0.7 秒再问一次真实状态：
            // 有锁屏 → 继续计时，等 USER_PRESENT；没锁屏 → 清掉锁屏计时并补跑一次
            scope.launch {
                delay(700)
                val was = screenUsable
                screenUsable = computeScreenUsable()
                if (screenUsable) {
                    LockClock.markUnlocked(this@WatchService)
                    if (!was) tick(force = false)
                }
            }
        }
    }

    /** 亮屏且已解锁才算"用户在用手机"。 */
    private fun computeScreenUsable(): Boolean = runCatching {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        pm?.isInteractive == true && km?.isKeyguardLocked != true
    }.getOrDefault(false)

    override fun onCreate() {
        super.onCreate()
        app = application as ShituApp
        app.notifier.ensureChannels()
        // 启动时写真实状态：以前这里固定写「正在启动…」，但屏幕亮着时走快节奏分支、
        // 只有搬了东西才刷新通知，结果已经跑起来了通知还一直停在「正在启动」。
        val ok = runCatching {
            ServiceCompat.startForeground(
                this,
                Notifier.ID_WATCH,
                app.notifier.watchNotification(watchText(moved = 0, lastRunLabel = null)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.isSuccess
        if (!ok) {
            // 系统拒绝前台化（例如从后台直接拉起被限制）：交给 WorkManager 兜底
            app.notifier.event("拾图未能常驻", "系统拒绝了常驻服务，已改用 15 分钟兜底任务。打开 App 可恢复正常。")
            stopSelf()
            return
        }
        screenUsable = computeScreenUsable()
        if (screenUsable) {
            LockClock.markUnlocked(this)
        } else if (!LockClock.isTracking(this)) {
            // 服务在锁屏后才起来：没有更早的起点，就从现在开始算
            LockClock.markLocked(this)
        }
        runCatching {
            registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
            )
        }
        scope.launch { loop() }
        // 深度休眠时 delay() 会停：用闹钟每 10 分钟叫醒一次，保证锁屏很久后新图也能搬
        ScanAlarm.schedule(this)
        // 进程重启后先把「累计已搬 / 上次运行」从规则表恢复，通知不会先显示 0 张
        scope.launch {
            runCatching { app.restoreWatchStats() }
            refresh()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> scope.launch {
                val n = app.stopAllRules()
                refresh("已全部停止（$n 条规则）")
            }
            ACTION_RUN_NOW -> scope.launch { tick(force = true) }
            ACTION_ALARM_SCAN -> scope.launch { tick(force = false) }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val s = app.currentSettings()
            // 每次都用系统的真实状态：锁屏/亮屏广播偶尔会漏，缓存的 screenUsable 一旦错了会整段跑偏
            screenUsable = computeScreenUsable()
            // 锁屏计时同步：解锁就清掉；锁屏/息屏期间保留起点
            if (screenUsable) {
                if (LockClock.isTracking(this)) LockClock.markUnlocked(this)
            } else if (!LockClock.isTracking(this)) {
                LockClock.markLocked(this)
            }
            // 快节奏检测：只 stat 目录，真变了才真扫。
            // 锁屏/息屏也跑（30 秒一拍）：新图不用再干等规则间隔（最长 5 分钟），更不用打开 App。
            if (s.fastWhileActive && app.hasChannel()) {
                runCatching { app.runFastLane() }
                // 原来只有搬了东西才刷新通知，屏幕亮着时通知会一直停在启动文案；
                // 现在每轮都刷新（正文没变时 refresh 内部跳过，不会重复发通知）。
                refresh()
            }
            // 不管屏幕状态，每 TICK_MS 至少做一次「到期规则」的完整扫描：
            // 快节奏只看目录 mtime，快节奏没盯到的变化（嵌套目录、条件不满足期间产生的文件）
            // 只有完整扫描能兜住；旧版亮屏时永远不跑定时扫描，后台就一直不搬。
            val now = System.currentTimeMillis()
            if (now - lastDueScanAt >= TICK_MS) {
                lastDueScanAt = now
                runCatching { tick(force = false) }
            }
            // 自动检查更新（内部有节流：内存 1 小时、落盘 24 小时；失败静默）
            runCatching { app.maybeCheckUpdate() }
            val delayMs = if (screenUsable && s.fastWhileActive) {
                s.fastIntervalSec.coerceIn(3, 60) * 1000L
            } else {
                TICK_MS
            }
            delay(delayMs)
        }
    }

    private suspend fun tick(force: Boolean) {
        if (busy) return
        busy = true
        // 锁屏/Doze 里 CPU 随时可能睡：扫描期间拿一个短时 WakeLock，别搬一半被冻住
        val wake = runCatching {
            getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shitu:scan")
                ?.apply { acquire(120_000L) }
        }.getOrNull()
        try {
            // 通道可以是 Shizuku，也可以是「所有文件访问」的本地通道；两条都不通才提示未就绪
            if (!app.ensureChannel()) {
                refresh("没有搬图通道：${app.bridge.state().display()}，也没给文件权限")
                return
            }
            val summary = app.runDueRules(force)
            // 统计信息已经在正文的「已搬 N 张 · 上次 …」里，避免出现「已搬 660 张 · … · 已搬 1 张」
            refresh(summary.message?.takeUnless { it.startsWith("已搬") })
        } finally {
            runCatching { wake?.release() }
            busy = false
        }
    }

    /**
     * 刷新常驻通知：正文由 [watchText] 统一生成。
     *
     * 内容没变就跳过，所以快节奏分支每几秒调一次也不会反复发通知；进程重启后会先
     * 把历史统计恢复再刷新，不会出现「0 张」。
     */
    private fun refresh(status: String? = null) {
        val text = watchText(
            moved = app.watchMoved,
            lastRunLabel = app.lastRunAt?.let { fmt(it) },
            status = status,
        )
        if (text == lastNotified) return
        val ok = runCatching {
            ServiceCompat.startForeground(
                this,
                Notifier.ID_WATCH,
                app.notifier.watchNotification(text),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.isSuccess
        if (ok) lastNotified = text
    }

    private fun fmt(ms: Long) = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        // 被杀恢复（规格 §6.7）：5 分钟后尝试重启，失败也有 WorkManager 兜底
        runCatching { RestartReceiver.schedule(this) }
        super.onDestroy()
    }
}
