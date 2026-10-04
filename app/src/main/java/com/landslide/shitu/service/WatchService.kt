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
 * - **其他时候** → 每 30 秒按规则自己的间隔跑（设置里开了「熄屏后暂停扫描」则跳过），
 *   另加 WorkManager 15 分钟兜底；亮屏解锁会立刻补跑一次。
 */
class WatchService : Service() {

    companion object {
        const val ACTION_STOP_ALL = "com.landslide.shitu.action.STOP_ALL"
        const val ACTION_RUN_NOW = "com.landslide.shitu.action.RUN_NOW"
        const val TICK_MS = 30_000L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: ShituApp

    @Volatile private var busy = false

    @Volatile private var screenUsable = false

    /** 屏幕开关/解锁：只在服务存活期间用运行时注册（清单里注册收不到这类广播）。 */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                screenUsable = false
                return
            }
            // 刚亮屏那一瞬间 KeyguardManager 还没报告"已锁"，等 0.7 秒再问一次真实状态：
            // 有锁屏 → 继续暂停，等 USER_PRESENT；没锁屏 → 立刻补跑一次
            scope.launch {
                delay(700)
                val was = screenUsable
                screenUsable = computeScreenUsable()
                if (screenUsable && !was) tick(force = false)
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
        val ok = runCatching {
            ServiceCompat.startForeground(
                this,
                Notifier.ID_WATCH,
                app.notifier.watchNotification("正在启动…"),
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> scope.launch {
                val n = app.stopAllRules()
                notify("已全部停止（$n 条规则）")
            }
            ACTION_RUN_NOW -> scope.launch { tick(force = true) }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val s = app.currentSettings()
            if (screenUsable && s.fastWhileActive) {
                if (app.bridge.state() != ShizukuState.READY) {
                    // 未就绪：走常规节奏，顺便把"未就绪"提示刷新到通知上
                    runCatching { tick(force = false) }
                } else {
                    // 快节奏：只 stat 目录，真变了才扫（屏幕亮着时唤醒不额外耗电）
                    val fast = runCatching { app.runFastLane() }.getOrNull()
                    if ((fast?.moved ?: 0) > 0) notify("快速检测：搬 ${fast?.moved} 张")
                }
                delay(s.fastIntervalSec.coerceIn(3, 60) * 1000L)
            } else {
                runCatching { tick(force = false) }
                delay(TICK_MS)
            }
        }
    }

    private suspend fun tick(force: Boolean) {
        if (busy) return
        busy = true
        try {
            var state = app.bridge.state()
            if (state != ShizukuState.READY) {
                notify("未就绪：${state.display()}")
                if (app.bridge.bindWithRetry(1)) state = app.bridge.state()
            }
            if (state == ShizukuState.READY) {
                val summary = app.runDueRules(force)
                notify(summary.message ?: "已搬 ${app.watchMoved} 张")
            }
        } finally {
            busy = false
        }
    }

    private fun notify(text: String) {
        val time = app.lastRunAt?.let { fmt(it) } ?: "—"
        ServiceCompat.startForeground(
            this,
            Notifier.ID_WATCH,
            app.notifier.watchNotification("已搬 ${app.watchMoved} 张 · 上次 $time · $text"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
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
