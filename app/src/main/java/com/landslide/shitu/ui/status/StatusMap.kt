package com.landslide.shitu.ui.status

import androidx.annotation.StringRes
import com.landslide.shitu.R
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.theme.Tone

/**
 * 状态 → 界面语义的纯映射（放这里是为了能写单测）。
 * 文字类函数返回 @StringRes 资源 id，由界面取本地化文案；界面只负责把 Tone 画成颜色，
 * 不要再自己写 if/else 判断状态。
 */

// ---------- Shizuku ----------

fun shizukuTone(state: ShizukuState): Tone = when (state) {
    ShizukuState.READY -> Tone.OK
    ShizukuState.NOT_INSTALLED -> Tone.ERROR
    ShizukuState.NOT_RUNNING, ShizukuState.NO_PERMISSION, ShizukuState.BIND_FAILED -> Tone.WARN
}

@StringRes
fun shizukuTitle(state: ShizukuState): Int = when (state) {
    ShizukuState.READY -> R.string.rules_shizuku_title_ready
    ShizukuState.NOT_INSTALLED -> R.string.rules_shizuku_title_not_installed
    ShizukuState.NOT_RUNNING -> R.string.rules_shizuku_title_not_running
    ShizukuState.NO_PERMISSION -> R.string.rules_shizuku_title_no_permission
    ShizukuState.BIND_FAILED -> R.string.rules_shizuku_title_bind_failed
}

@StringRes
fun shizukuAdvice(state: ShizukuState): Int = when (state) {
    ShizukuState.READY -> R.string.rules_shizuku_advice_ready
    ShizukuState.NOT_INSTALLED -> R.string.rules_shizuku_advice_not_installed
    ShizukuState.NOT_RUNNING -> R.string.rules_shizuku_advice_not_running
    ShizukuState.NO_PERMISSION -> R.string.rules_shizuku_advice_no_permission
    ShizukuState.BIND_FAILED -> R.string.rules_shizuku_advice_bind_failed
}

/** 状态卡右侧按钮的文案资源；READY 时不需要按钮，返回 null。 */
@StringRes
fun shizukuActionLabel(state: ShizukuState): Int? = when (state) {
    ShizukuState.READY -> null
    ShizukuState.NOT_INSTALLED -> R.string.rules_shizuku_action_install
    ShizukuState.NOT_RUNNING -> R.string.rules_shizuku_action_open
    ShizukuState.NO_PERMISSION -> R.string.rules_shizuku_action_request_permission
    ShizukuState.BIND_FAILED -> R.string.rules_shizuku_action_retry
}

// ---------- 规则 ----------

fun ruleTone(rule: RuleEntity): Tone = when {
    !rule.enabled -> Tone.IDLE
    rule.state == RuleState.RUNNING -> Tone.BUSY
    rule.state == RuleState.PAUSED_ERROR -> Tone.ERROR
    rule.state == RuleState.PAUSED_MANUAL || rule.state == RuleState.PAUSED_LOOP -> Tone.WARN
    else -> Tone.OK
}

@StringRes
fun ruleStatusText(rule: RuleEntity): Int = when {
    !rule.enabled -> R.string.rules_status_stopped
    rule.state == RuleState.RUNNING -> R.string.rules_status_running
    rule.state == RuleState.PAUSED_ERROR -> R.string.rules_status_paused_error
    rule.state == RuleState.PAUSED_MANUAL -> R.string.rules_status_paused_manual
    rule.state == RuleState.PAUSED_LOOP -> R.string.rules_status_paused_loop
    else -> R.string.rules_status_idle
}

/** 被自动暂停的规则才显示「恢复」按钮。 */
fun canResume(rule: RuleEntity): Boolean =
    rule.enabled && (rule.state == RuleState.PAUSED_ERROR || rule.state == RuleState.PAUSED_LOOP)

// ---------- 日志 ----------

fun logTone(result: LogResult): Tone = when (result) {
    LogResult.MOVED -> Tone.OK
    LogResult.COPIED -> Tone.COPY
    LogResult.SKIPPED -> Tone.WARN
    LogResult.FAILED -> Tone.ERROR
    LogResult.UNDONE -> Tone.BUSY
    LogResult.INFO -> Tone.IDLE
}
