package com.landslide.shitu.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 深度休眠时的定时唤醒。
 *
 * 前台服务能活着，但锁屏久了 CPU 会进 Doze/深睡，`delay()` 这类单调时钟的定时器会停；
 * 实测锁屏约 4 分钟后通知心跳就断了，新图要等人碰手机/打开 App 才搬。
 *
 * 这里用 `setAndAllowWhileIdle`（能精确闹钟就用 `setExactAndAllowWhileIdle`）每 10 分钟
 * 唤醒一次，让服务跑一轮到期扫描；配合 [WatchService] 扫描时的短时 WakeLock，保证这一轮能跑完。
 */
object ScanAlarm {

    const val ACTION_SCAN = "com.landslide.shitu.action.ALARM_SCAN"

    private const val REQUEST_CODE = 9101
    private const val INTERVAL_MS = 10 * 60_000L

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ScanAlarmReceiver::class.java).setAction(ACTION_SCAN),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + INTERVAL_MS
        runCatching {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pending(context)) }
    }
}

/** 闹钟到点：先排下一次，再叫服务跑一轮（服务已在运行就只是发一个 action）。 */
class ScanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScanAlarm.schedule(context)
        runCatching { WatchService.startForScan(context) }
    }
}
