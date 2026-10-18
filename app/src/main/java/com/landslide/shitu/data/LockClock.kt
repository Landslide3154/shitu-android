package com.landslide.shitu.data

import android.content.Context

/**
 * 锁屏起始时间。
 *
 * 用户口径：锁屏和息屏是同一个状态。这里在屏幕熄灭时记下时间，解锁时清除；
 * 用 SharedPreferences 存，进程被杀/服务重启后还能接着算「锁屏多久了」。
 */
object LockClock {

    private const val PREFS = "lock_clock"
    private const val KEY_LOCKED_AT = "locked_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markLocked(context: Context, at: Long = System.currentTimeMillis()) {
        runCatching { prefs(context).edit().putLong(KEY_LOCKED_AT, at).apply() }
    }

    fun markUnlocked(context: Context) {
        runCatching { prefs(context).edit().remove(KEY_LOCKED_AT).apply() }
    }

    fun lockedAt(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_LOCKED_AT, 0L) }.getOrDefault(0L)

    fun isTracking(context: Context): Boolean = lockedAt(context) > 0L

    fun lockedForMs(context: Context, now: Long = System.currentTimeMillis()): Long {
        val at = lockedAt(context)
        if (at <= 0L) return 0L
        return (now - at).coerceAtLeast(0L)
    }
}
