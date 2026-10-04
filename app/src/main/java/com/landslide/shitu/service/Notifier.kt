package com.landslide.shitu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.landslide.shitu.MainActivity
import com.landslide.shitu.R

/** 常驻通知 + 事件通知（规格 §6.8）。 */
class Notifier(private val context: Context) {

    companion object {
        const val CH_WATCH = "watch"
        const val CH_EVENT = "event"
        const val ID_WATCH = 1
    }

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, "监控状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "常驻通知：显示已搬数量与最近运行时间"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_EVENT, "事件提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "规则暂停、Shizuku 未就绪、撤回结果等"
            },
        )
    }

    fun watchNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = action(WatchService.ACTION_PAUSE_ALL, 1)
        val scan = action(WatchService.ACTION_RUN_NOW, 2)
        return NotificationCompat.Builder(context, CH_WATCH)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("拾图正在监控")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(0, "暂停全部", pause)
            .addAction(0, "立即扫一次", scan)
            .build()
    }

    private fun action(name: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WatchService::class.java).setAction(name)
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun event(title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_EVENT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(title.hashCode(), n) }
    }
}
