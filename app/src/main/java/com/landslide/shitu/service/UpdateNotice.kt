package com.landslide.shitu.service

import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 后台自动检查更新：有新版就发一条「事件提醒」通知，点开直接进 App 的更新弹窗。
 *
 * 节流：内存里 1 小时内最多尝试一次；成功与否都会把「上次检查时间」落盘，24 小时内不再联网。
 * 仍然只有「检查更新」这一处联网；失败一律静默，不打扰用户。
 */
object UpdateNotice {

    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val MIN_ATTEMPT_GAP_MS = 60L * 60 * 1000

    @Volatile private var lastAttemptAt = 0L
    private val mutex = Mutex()

    suspend fun maybeNotify(app: ShituApp) {
        // 快路径：循环每几秒就会调一次，不能每次都读 DataStore / 抢锁
        if (System.currentTimeMillis() - lastAttemptAt < MIN_ATTEMPT_GAP_MS) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastAttemptAt < MIN_ATTEMPT_GAP_MS) return@withLock
            lastAttemptAt = now

            val s = app.settings.settings.first()
            // 更新提醒不跟旧的「事件提醒」开关走：那个开关已经从界面移除，
            // 若旧设置里是关的，会让用户永远收不到「有新版本」。
            if (!UpdateChecker.shouldAutoCheck(now, s.lastUpdateCheckAt, CHECK_INTERVAL_MS)) {
                return@withLock
            }
            // 先落盘时间：离线时也 24 小时内不再反复唤醒网络
            app.settings.setLastUpdateCheckAt(now)

            val info = withContext(Dispatchers.IO) {
                runCatching { UpdateChecker.fetchLatest(8_000) }.getOrNull()
            } ?: return@withLock

            val local = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull() ?: return@withLock

            if (!UpdateChecker.shouldNotify(local, info.version, s.lastNotifiedUpdate)) {
                return@withLock
            }
            app.settings.setLastNotifiedUpdate(info.version)
            app.notifier.event(
                title = app.getString(R.string.update_notif_title, info.tagName),
                text = app.getString(R.string.update_notif_text, local, info.version),
                openUpdate = true,
            )
        }
    }
}
