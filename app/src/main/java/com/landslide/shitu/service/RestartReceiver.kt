package com.landslide.shitu.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 被杀恢复（规格 §6.7）：服务 onDestroy 时排一次 5 分钟后的重启；
 * 系统若拒绝（省电策略）则依赖 WorkManager 兜底或用户打开 App。
 */
class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WatchService.start(context)
    }

    companion object {
        private const val REQUEST_CODE = 9001
        private const val DELAY_MS = 5 * 60_000L

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RestartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            runCatching {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + DELAY_MS,
                    pending(context),
                )
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            runCatching { am.cancel(pending(context)) }
        }
    }
}
