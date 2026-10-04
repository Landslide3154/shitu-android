package com.landslide.shitu.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
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
 * 常驻服务（规格 §6.7）：specialUse 类型前台服务，每 30 秒探测一次 Shizuku，
 * 到期规则直接跑一轮；被厂商清理后由 WorkManager 兜底。
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
            runCatching { tick(force = false) }
            delay(TICK_MS)
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
        // 被杀恢复（规格 §6.7）：5 分钟后尝试重启，失败也有 WorkManager 兜底
        runCatching { RestartReceiver.schedule(this) }
        super.onDestroy()
    }
}
