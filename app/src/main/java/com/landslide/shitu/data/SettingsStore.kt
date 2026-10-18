package com.landslide.shitu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.landslide.shitu.core.RuleTemplate
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
    /** 旧版「熄屏后暂停扫描」开关：UI 已移除，运行条件接管这个判断；字段留着只为兼容旧备份/旧设置 */
    val screenOffPause: Boolean = false,
    /** 规则卡片是否用简略样式（只显示名称 + 开关） */
    val ruleCardsCompact: Boolean = false,
    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色（见 ThemeMode） */
    val themeMode: Int = 0,
    /** Material You 动态取色（Android 12+ 从壁纸取色） */
    val dynamicColor: Boolean = true,
    /** 用户自建的规则模板（存在 DataStore 的一段文本里） */
    val ruleTemplates: List<RuleTemplate> = emptyList(),
    // —— 界面与运行状态项 ——
    val notifyEnabled: Boolean = true,
    val lastSelfCheck: String? = null,
    val lastSelfCheckAt: Long? = null,
    /** 上次自动检查更新的时间（毫秒；0 = 从没查过） */
    val lastUpdateCheckAt: Long = 0L,
    /** 已经提醒过的新版本号（同一版本只提醒一次） */
    val lastNotifiedUpdate: String? = null,
    /** 界面语言：0 跟随系统 / 1 中文 / 2 English（见 AppLocale；真正生效靠 SharedPreferences 同步读取） */
    val language: Int = AppLocale.SYSTEM,
    // —— 运行条件（多选；默认全关 = 和以前一样，随时都能搬） ——
    val runOnlyCharging: Boolean = false,
    /** 只在亮屏（且解锁）时搬；和 [runOnlyScreenOff] 可以同时选（= 任何时候都搬）；锁屏/息屏算同一个状态 */
    val runOnlyScreenOn: Boolean = false,
    /** 只在锁屏（屏幕熄灭或停在锁屏界面）时搬 */
    val runOnlyScreenOff: Boolean = false,
    /** 只在夜间搬运；时段默认 22:00–07:00（跨午夜用 hourInNightRange 判断） */
    val runOnlyNight: Boolean = false,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,
    /** 电量高于这个百分比才搬；0 = 不看电量 */
    val runMinBatteryPct: Int = 0,
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
        val RULE_TEMPLATES = stringPreferencesKey("rule_templates")
        val NOTIFY = booleanPreferencesKey("notify_enabled")
        val SELF_CHECK = stringPreferencesKey("last_self_check")
        val SELF_CHECK_AT = intPreferencesKey("last_self_check_at")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val LAST_NOTIFIED_UPDATE = stringPreferencesKey("last_notified_update")
        val LANGUAGE = intPreferencesKey("language")
        val RUN_ONLY_CHARGING = booleanPreferencesKey("run_only_charging")
        val RUN_ONLY_SCREEN_ON = booleanPreferencesKey("run_only_screen_on")
        val RUN_ONLY_SCREEN_OFF = booleanPreferencesKey("run_only_screen_off")
        val RUN_ONLY_NIGHT = booleanPreferencesKey("run_only_night")
        val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
        val RUN_MIN_BATTERY_PCT = intPreferencesKey("run_min_battery_pct")
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
            ruleTemplates = RuleTemplate.decode(p[K.RULE_TEMPLATES]),
            notifyEnabled = p[K.NOTIFY] ?: true,
            lastSelfCheck = p[K.SELF_CHECK],
            lastSelfCheckAt = p[K.SELF_CHECK_AT]?.toLong(),
            lastUpdateCheckAt = p[K.LAST_UPDATE_CHECK_AT] ?: 0L,
            lastNotifiedUpdate = p[K.LAST_NOTIFIED_UPDATE],
            language = p[K.LANGUAGE] ?: AppLocale.SYSTEM,
            runOnlyCharging = p[K.RUN_ONLY_CHARGING] ?: false,
            // 旧版的「熄屏后暂停扫描」等价于新版的「只在亮屏时搬」；老设置升级后自动带过来
            runOnlyScreenOn = p[K.RUN_ONLY_SCREEN_ON] ?: (p[K.SCREEN_OFF_PAUSE] ?: false),
            runOnlyScreenOff = p[K.RUN_ONLY_SCREEN_OFF] ?: false,
            // 0.12.6 起设置页不再提供「夜间」条件；老用户之前开过的直接失效，
            // 否则会出现「还在只在夜间搬、但界面上没有开关可关」的死角。
            runOnlyNight = false,
            nightStartHour = p[K.NIGHT_START_HOUR] ?: 22,
            nightEndHour = p[K.NIGHT_END_HOUR] ?: 7,
            runMinBatteryPct = p[K.RUN_MIN_BATTERY_PCT] ?: 0,
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
    suspend fun setRuleTemplates(list: List<RuleTemplate>) = put(K.RULE_TEMPLATES, RuleTemplate.encode(list))
    suspend fun setNotifyEnabled(v: Boolean) = put(K.NOTIFY, v)
    suspend fun setLastUpdateCheckAt(v: Long) = put(K.LAST_UPDATE_CHECK_AT, v)
    suspend fun setLastNotifiedUpdate(v: String) = put(K.LAST_NOTIFIED_UPDATE, v)
    suspend fun setRunOnlyCharging(v: Boolean) = put(K.RUN_ONLY_CHARGING, v)
    suspend fun setRunOnlyScreenOn(v: Boolean) = put(K.RUN_ONLY_SCREEN_ON, v)
    suspend fun setRunOnlyScreenOff(v: Boolean) = put(K.RUN_ONLY_SCREEN_OFF, v)
    suspend fun setRunOnlyNight(v: Boolean) = put(K.RUN_ONLY_NIGHT, v)
    suspend fun setNightStartHour(v: Int) = put(K.NIGHT_START_HOUR, v)
    suspend fun setNightEndHour(v: Int) = put(K.NIGHT_END_HOUR, v)
    suspend fun setRunMinBatteryPct(v: Int) = put(K.RUN_MIN_BATTERY_PCT, v)

    /** 语言要能被 `attachBaseContext` 同步读到，所以额外落一份 SharedPreferences（DataStore 是异步的）。 */
    suspend fun setLanguage(v: Int) {
        put(K.LANGUAGE, v)
        AppLocale.set(context, v)
    }

    /** 备份导入：把整份设置写回去（不动 lastSelfCheck 这类纯状态项）。 */
    suspend fun apply(s: Settings) {
        context.dataStore.edit { p ->
            p[K.SCAN_BUDGET_SEC] = s.scanBudgetSec
            p[K.MAX_PER_RUN] = s.maxPerRun
            p[K.MAX_PER_MINUTE] = s.maxPerMinute
            p[K.MAX_RUN_SEC] = s.maxRunSec
            p[K.STABLE_SEC] = s.stableSec
            p[K.LOOP_WINDOW_MIN] = s.loopWindowMin
            p[K.LOOP_THRESHOLD] = s.loopThreshold
            p[K.FAIL_THRESHOLD] = s.failThreshold
            p[K.LOW_BATTERY_PAUSE] = s.lowBatteryPause
            p[K.LOW_BATTERY_PCT] = s.lowBatteryPct
            p[K.LOG_KEEP_DAYS] = s.logKeepDays
            p[K.LOG_KEEP_COUNT] = s.logKeepCount
            p[K.FAST_DRAIN] = s.fastDrain
            p[K.FAST_WHILE_ACTIVE] = s.fastWhileActive
            p[K.FAST_INTERVAL_SEC] = s.fastIntervalSec
            p[K.SETTLE_DETECT] = s.settleDetect
            p[K.SETTLE_GAP_SEC] = s.settleGapSec
            p[K.SCREEN_OFF_PAUSE] = s.runOnlyScreenOn
            p[K.RULE_CARDS_COMPACT] = s.ruleCardsCompact
            p[K.THEME_MODE] = s.themeMode
            p[K.DYNAMIC_COLOR] = s.dynamicColor
            p[K.NOTIFY] = s.notifyEnabled
            p[K.LANGUAGE] = s.language
            p[K.RUN_ONLY_CHARGING] = s.runOnlyCharging
            p[K.RUN_ONLY_SCREEN_ON] = s.runOnlyScreenOn
            p[K.RUN_ONLY_SCREEN_OFF] = s.runOnlyScreenOff
            p[K.RUN_ONLY_NIGHT] = s.runOnlyNight
            p[K.NIGHT_START_HOUR] = s.nightStartHour
            p[K.NIGHT_END_HOUR] = s.nightEndHour
            p[K.RUN_MIN_BATTERY_PCT] = s.runMinBatteryPct
            p[K.RULE_TEMPLATES] = RuleTemplate.encode(s.ruleTemplates)
        }
        AppLocale.set(context, s.language)
    }

    suspend fun setLastSelfCheck(text: String) = context.dataStore.edit {
        it[K.SELF_CHECK] = text
        it[K.SELF_CHECK_AT] = (System.currentTimeMillis() / 1000L).toInt()
    }

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) {
        context.dataStore.edit { it[key] = v }
    }
}
