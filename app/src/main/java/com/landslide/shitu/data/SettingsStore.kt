package com.landslide.shitu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** 规格 §8：全部运行参数 + 通知/自检等界面项。默认值照抄规格。 */
data class Settings(
    val scanBudgetSec: Int = 20,
    val maxPerRun: Int = 20,
    val maxPerMinute: Int = 120,
    val maxRunSec: Int = 60,
    val stableSec: Int = 30,
    val loopWindowMin: Int = 30,
    val loopThreshold: Int = 3,
    val failThreshold: Int = 5,
    val lowBatteryPause: Boolean = true,
    val lowBatteryPct: Int = 15,
    val logKeepDays: Int = 30,
    val logKeepCount: Int = 50_000,
    val fastDrain: Boolean = false,
    /** 快节奏检测：屏幕亮着且解锁时，每隔 fastIntervalSec 秒看一眼目录有没有变（变了才真扫） */
    val fastWhileActive: Boolean = true,
    val fastIntervalSec: Int = 10,
    /** 写完就搬：用"大小/mtime 不再变化"判定文件写完了，不必等满稳定期 */
    val settleDetect: Boolean = true,
    /** 判定窗口：两次观察至少间隔这么多秒才算确认写完 */
    val settleGapSec: Int = 3,
    /** 熄屏后是否暂停扫描（默认 false = 继续按规则间隔工作） */
    val screenOffPause: Boolean = false,
    /** 规则卡片是否用简略样式（只显示名称 + 开关） */
    val ruleCardsCompact: Boolean = false,
    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色（见 ThemeMode） */
    val themeMode: Int = 0,
    /** Material You 动态取色（Android 12+ 从壁纸取色） */
    val dynamicColor: Boolean = true,
    // —— 界面与运行状态项 ——
    val notifyEnabled: Boolean = true,
    val lastSelfCheck: String? = null,
    val lastSelfCheckAt: Long? = null,
)

class SettingsStore(private val context: Context) {

    private object K {
        val SCAN_BUDGET_SEC = intPreferencesKey("scan_budget_sec")
        val MAX_PER_RUN = intPreferencesKey("max_per_run")
        val MAX_PER_MINUTE = intPreferencesKey("max_per_minute")
        val MAX_RUN_SEC = intPreferencesKey("max_run_sec")
        val STABLE_SEC = intPreferencesKey("stable_sec")
        val LOOP_WINDOW_MIN = intPreferencesKey("loop_window_min")
        val LOOP_THRESHOLD = intPreferencesKey("loop_threshold")
        val FAIL_THRESHOLD = intPreferencesKey("fail_threshold")
        val LOW_BATTERY_PAUSE = booleanPreferencesKey("low_battery_pause")
        val LOW_BATTERY_PCT = intPreferencesKey("low_battery_pct")
        val LOG_KEEP_DAYS = intPreferencesKey("log_keep_days")
        val LOG_KEEP_COUNT = intPreferencesKey("log_keep_count")
        val FAST_DRAIN = booleanPreferencesKey("fast_drain")
        val FAST_WHILE_ACTIVE = booleanPreferencesKey("fast_while_active")
        val FAST_INTERVAL_SEC = intPreferencesKey("fast_interval_sec")
        val SETTLE_DETECT = booleanPreferencesKey("settle_detect")
        val SETTLE_GAP_SEC = intPreferencesKey("settle_gap_sec")
        val SCREEN_OFF_PAUSE = booleanPreferencesKey("screen_off_pause")
        val RULE_CARDS_COMPACT = booleanPreferencesKey("rule_cards_compact")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTIFY = booleanPreferencesKey("notify_enabled")
        val SELF_CHECK = stringPreferencesKey("last_self_check")
        val SELF_CHECK_AT = intPreferencesKey("last_self_check_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            scanBudgetSec = p[K.SCAN_BUDGET_SEC] ?: 20,
            maxPerRun = p[K.MAX_PER_RUN] ?: 20,
            maxPerMinute = p[K.MAX_PER_MINUTE] ?: 120,
            maxRunSec = p[K.MAX_RUN_SEC] ?: 60,
            stableSec = p[K.STABLE_SEC] ?: 30,
            loopWindowMin = p[K.LOOP_WINDOW_MIN] ?: 30,
            loopThreshold = p[K.LOOP_THRESHOLD] ?: 3,
            failThreshold = p[K.FAIL_THRESHOLD] ?: 5,
            lowBatteryPause = p[K.LOW_BATTERY_PAUSE] ?: true,
            lowBatteryPct = p[K.LOW_BATTERY_PCT] ?: 15,
            logKeepDays = p[K.LOG_KEEP_DAYS] ?: 30,
            logKeepCount = p[K.LOG_KEEP_COUNT] ?: 50_000,
            fastDrain = p[K.FAST_DRAIN] ?: false,
            fastWhileActive = p[K.FAST_WHILE_ACTIVE] ?: true,
            fastIntervalSec = p[K.FAST_INTERVAL_SEC] ?: 10,
            settleDetect = p[K.SETTLE_DETECT] ?: true,
            settleGapSec = p[K.SETTLE_GAP_SEC] ?: 3,
            screenOffPause = p[K.SCREEN_OFF_PAUSE] ?: false,
            ruleCardsCompact = p[K.RULE_CARDS_COMPACT] ?: false,
            themeMode = p[K.THEME_MODE] ?: 0,
            dynamicColor = p[K.DYNAMIC_COLOR] ?: true,
            notifyEnabled = p[K.NOTIFY] ?: true,
            lastSelfCheck = p[K.SELF_CHECK],
            lastSelfCheckAt = p[K.SELF_CHECK_AT]?.toLong(),
        )
    }

    suspend fun setScanBudgetSec(v: Int) = put(K.SCAN_BUDGET_SEC, v)
    suspend fun setMaxPerRun(v: Int) = put(K.MAX_PER_RUN, v)
    suspend fun setMaxPerMinute(v: Int) = put(K.MAX_PER_MINUTE, v)
    suspend fun setMaxRunSec(v: Int) = put(K.MAX_RUN_SEC, v)
    suspend fun setStableSec(v: Int) = put(K.STABLE_SEC, v)
    suspend fun setLoopWindowMin(v: Int) = put(K.LOOP_WINDOW_MIN, v)
    suspend fun setLoopThreshold(v: Int) = put(K.LOOP_THRESHOLD, v)
    suspend fun setFailThreshold(v: Int) = put(K.FAIL_THRESHOLD, v)
    suspend fun setLowBatteryPause(v: Boolean) = put(K.LOW_BATTERY_PAUSE, v)
    suspend fun setLowBatteryPct(v: Int) = put(K.LOW_BATTERY_PCT, v)
    suspend fun setLogKeepDays(v: Int) = put(K.LOG_KEEP_DAYS, v)
    suspend fun setLogKeepCount(v: Int) = put(K.LOG_KEEP_COUNT, v)
    suspend fun setFastDrain(v: Boolean) = put(K.FAST_DRAIN, v)
    suspend fun setFastWhileActive(v: Boolean) = put(K.FAST_WHILE_ACTIVE, v)
    suspend fun setFastIntervalSec(v: Int) = put(K.FAST_INTERVAL_SEC, v)
    suspend fun setSettleDetect(v: Boolean) = put(K.SETTLE_DETECT, v)
    suspend fun setSettleGapSec(v: Int) = put(K.SETTLE_GAP_SEC, v)
    suspend fun setScreenOffPause(v: Boolean) = put(K.SCREEN_OFF_PAUSE, v)
    suspend fun setRuleCardsCompact(v: Boolean) = put(K.RULE_CARDS_COMPACT, v)
    suspend fun setThemeMode(v: Int) = put(K.THEME_MODE, v)
    suspend fun setDynamicColor(v: Boolean) = put(K.DYNAMIC_COLOR, v)
    suspend fun setNotifyEnabled(v: Boolean) = put(K.NOTIFY, v)

    suspend fun setLastSelfCheck(text: String) = context.dataStore.edit {
        it[K.SELF_CHECK] = text
        it[K.SELF_CHECK_AT] = (System.currentTimeMillis() / 1000L).toInt()
    }

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) {
        context.dataStore.edit { it[key] = v }
    }
}
