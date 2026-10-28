package com.landslide.shitu.data

import com.landslide.shitu.core.ConditionOrder
import com.landslide.shitu.data.db.RuleEntity
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「设置管理」的备份文件：规则 + 各项设置放在同一个文本里。
 *
 * 格式分两段，各有表头：
 * - `# shitu rules v1` 后面是 [RuleIo] 的规则行（一行一条、tab 分隔、URL 编码）；
 * - `# shitu settings v1` 后面是 `key=值` 的设置行。
 *
 * 这样老版本的「只导出规则」文件仍能导入（没有设置段就只导规则）。
 */
object BackupIo {

    private const val RULES_HEADER = "# shitu rules v1"
    private const val SETTINGS_HEADER = "# shitu settings v1"

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun fileName(now: Long = System.currentTimeMillis()): String =
        "shitu-backup-${stamp.format(Date(now))}.txt"

    fun encode(rules: List<RuleEntity>, s: Settings): String = buildString {
        appendLine(RULES_HEADER)
        RuleIo.encode(rules).lineSequence()
            .drop(1) // 丢掉 RuleIo 自己的表头，统一用本文件的
            .filter { it.isNotBlank() }
            .forEach { appendLine(it) }
        appendLine()
        appendLine(SETTINGS_HEADER)
        settingsPairs(s).forEach { (k, v) -> appendLine("$k=${enc(v)}") }
    }

    data class Backup(val rules: List<RuleEntity>, val settings: Settings?)

    fun decode(text: String): Backup {
        if (text.isBlank()) return Backup(emptyList(), null)

        val rulesText = StringBuilder()
        val settingsMap = HashMap<String, String>()
        var inSettings = false
        for (line in text.lines()) {
            val t = line.trim()
            when {
                t == SETTINGS_HEADER -> inSettings = true
                t == RULES_HEADER -> inSettings = false
                t.isEmpty() || t.startsWith("#") -> Unit
                inSettings -> {
                    val i = t.indexOf('=')
                    if (i > 0) settingsMap[t.substring(0, i)] = dec(t.substring(i + 1))
                }
                else -> rulesText.appendLine(t)
            }
        }
        val settings = if (settingsMap.isEmpty()) null else settingsFrom(settingsMap)
        return Backup(RuleIo.decode(rulesText.toString()), settings)
    }

    private fun settingsPairs(s: Settings): List<Pair<String, String>> = listOf(
        "scanBudgetSec" to s.scanBudgetSec,
        "maxPerRun" to s.maxPerRun,
        "maxPerMinute" to s.maxPerMinute,
        "maxRunSec" to s.maxRunSec,
        "stableSec" to s.stableSec,
        "loopWindowMin" to s.loopWindowMin,
        "loopThreshold" to s.loopThreshold,
        "failThreshold" to s.failThreshold,
        "lowBatteryPause" to s.lowBatteryPause,
        "lowBatteryPct" to s.lowBatteryPct,
        "logKeepDays" to s.logKeepDays,
        "logKeepCount" to s.logKeepCount,
        "fastDrain" to s.fastDrain,
        "fastWhileActive" to s.fastWhileActive,
        "fastIntervalSec" to s.fastIntervalSec,
        "settleDetect" to s.settleDetect,
        "settleGapSec" to s.settleGapSec,
        "ruleCardsCompact" to s.ruleCardsCompact,
        "themeMode" to s.themeMode,
        "dynamicColor" to s.dynamicColor,
        "colorStyle" to s.colorStyle,
        "seedColor" to s.seedColor,
        "pureBlack" to s.pureBlack,
        "navStyle" to s.navStyle,
        "tabAnimation" to s.tabAnimation,
        "textScale" to s.textScale,
        "ruleOrder" to s.ruleOrder.joinToString(","),
        "notifyEnabled" to s.notifyEnabled,
        "language" to s.language,
        "runOnlyCharging" to s.runOnlyCharging,
        "runOnlyScreenOn" to s.runOnlyScreenOn,
        "runOnlyScreenOff" to s.runOnlyScreenOff,
        "runOnlyNight" to s.runOnlyNight,
        "nightStartHour" to s.nightStartHour,
        "nightEndHour" to s.nightEndHour,
        "runMinBatteryPct" to s.runMinBatteryPct,
        "lockTimeoutMin" to s.lockTimeoutMin,
        "conditionOrder" to ConditionOrder.encode(s.conditionOrder),
    ).map { (k, v) -> k to v.toString() }

    /** 缺字段就用默认值：备份文件可能来自旧版本。 */
    private fun settingsFrom(m: Map<String, String>): Settings {
        val d = Settings()
        fun b(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
        fun i(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
        fun f(k: String, def: Float) = m[k]?.toFloatOrNull() ?: def
        return Settings(
            scanBudgetSec = i("scanBudgetSec", d.scanBudgetSec).coerceIn(5, 120),
            maxPerRun = i("maxPerRun", d.maxPerRun).coerceIn(1, 500),
            maxPerMinute = i("maxPerMinute", d.maxPerMinute).coerceIn(10, 600),
            maxRunSec = i("maxRunSec", d.maxRunSec).coerceIn(10, 300),
            stableSec = i("stableSec", d.stableSec).coerceIn(0, 300),
            loopWindowMin = i("loopWindowMin", d.loopWindowMin).coerceIn(5, 120),
            loopThreshold = i("loopThreshold", d.loopThreshold).coerceIn(2, 10),
            failThreshold = i("failThreshold", d.failThreshold).coerceIn(1, 20),
            lowBatteryPause = b("lowBatteryPause", d.lowBatteryPause),
            lowBatteryPct = i("lowBatteryPct", d.lowBatteryPct).coerceIn(0, 50),
            logKeepDays = i("logKeepDays", d.logKeepDays).coerceIn(1, 365),
            logKeepCount = i("logKeepCount", d.logKeepCount).coerceIn(1_000, 200_000),
            fastDrain = b("fastDrain", d.fastDrain),
            fastWhileActive = b("fastWhileActive", d.fastWhileActive),
            fastIntervalSec = i("fastIntervalSec", d.fastIntervalSec).coerceIn(3, 60),
            settleDetect = b("settleDetect", d.settleDetect),
            settleGapSec = i("settleGapSec", d.settleGapSec).coerceIn(2, 15),
            ruleCardsCompact = b("ruleCardsCompact", d.ruleCardsCompact),
            themeMode = i("themeMode", d.themeMode).coerceIn(0, 2),
            dynamicColor = b("dynamicColor", d.dynamicColor),
            colorStyle = i("colorStyle", d.colorStyle).coerceIn(0, 3),
            seedColor = i("seedColor", d.seedColor),
            pureBlack = b("pureBlack", d.pureBlack),
            navStyle = i("navStyle", d.navStyle).coerceIn(0, 1),
            tabAnimation = i("tabAnimation", d.tabAnimation).coerceIn(0, 1),
            textScale = f("textScale", d.textScale).coerceIn(0.8f, 1.4f),
            ruleOrder = m["ruleOrder"]
                ?.split(',')
                ?.mapNotNull { it.toLongOrNull() }
                ?: d.ruleOrder,
            notifyEnabled = b("notifyEnabled", d.notifyEnabled),
            language = i("language", d.language).coerceIn(0, 2),
            runOnlyCharging = b("runOnlyCharging", d.runOnlyCharging),
            runOnlyScreenOn = b("runOnlyScreenOn", d.runOnlyScreenOn),
            runOnlyScreenOff = b("runOnlyScreenOff", d.runOnlyScreenOff),
            runOnlyNight = b("runOnlyNight", d.runOnlyNight),
            nightStartHour = i("nightStartHour", d.nightStartHour).coerceIn(0, 23),
            nightEndHour = i("nightEndHour", d.nightEndHour).coerceIn(0, 23),
            runMinBatteryPct = i("runMinBatteryPct", d.runMinBatteryPct).coerceIn(0, 100),
            lockTimeoutMin = i("lockTimeoutMin", d.lockTimeoutMin).coerceIn(0, 240),
            conditionOrder = ConditionOrder.decode(m["conditionOrder"]),
        )
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun dec(v: String): String = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
}
