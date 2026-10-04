package com.landslide.shitu.ui.status

import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.theme.Tone

/**
 * 状态 → 界面语义的纯映射（放这里是为了能写单测）。
 * 界面只负责把 Tone 画成颜色，不要再自己写 if/else 判断状态。
 */

// ---------- Shizuku ----------

fun shizukuTone(state: ShizukuState): Tone = when (state) {
    ShizukuState.READY -> Tone.OK
    ShizukuState.NOT_INSTALLED -> Tone.ERROR
    ShizukuState.NOT_RUNNING, ShizukuState.NO_PERMISSION, ShizukuState.BIND_FAILED -> Tone.WARN
}

fun shizukuTitle(state: ShizukuState): String = when (state) {
    ShizukuState.READY -> "Shizuku 已就绪"
    ShizukuState.NOT_INSTALLED -> "没有安装 Shizuku"
    ShizukuState.NOT_RUNNING -> "Shizuku 没有在运行"
    ShizukuState.NO_PERMISSION -> "还没授权给「拾图」"
    ShizukuState.BIND_FAILED -> "Shizuku 连接失败"
}

fun shizukuAdvice(state: ShizukuState): String = when (state) {
    ShizukuState.READY -> "点一下可以跑自检"
    ShizukuState.NOT_INSTALLED -> "先装 Shizuku（应用商店搜，或官网 shizuku.rikka.app），装好回来点「重试」"
    ShizukuState.NOT_RUNNING -> "打开 Shizuku，按它的提示启动服务（无线调试或 adb），然后点「重试」"
    ShizukuState.NO_PERMISSION -> "点右边按钮，在弹出的框里选「允许」"
    ShizukuState.BIND_FAILED -> "点「重试」重新连接；一直失败就跑一次自检看细节"
}

/** 状态卡右侧按钮的文案；READY 时不需要按钮，返回 null。 */
fun shizukuActionLabel(state: ShizukuState): String? = when (state) {
    ShizukuState.READY -> null
    ShizukuState.NOT_INSTALLED -> "去安装"
    ShizukuState.NOT_RUNNING -> "打开 Shizuku"
    ShizukuState.NO_PERMISSION -> "请求授权"
    ShizukuState.BIND_FAILED -> "重试"
}

// ---------- 规则 ----------

fun ruleTone(rule: RuleEntity): Tone = when {
    !rule.enabled -> Tone.IDLE
    rule.state == RuleState.RUNNING -> Tone.BUSY
    rule.state == RuleState.PAUSED_ERROR -> Tone.ERROR
    rule.state == RuleState.PAUSED_MANUAL || rule.state == RuleState.PAUSED_LOOP -> Tone.WARN
    else -> Tone.OK
}

fun ruleStatusText(rule: RuleEntity): String = when {
    !rule.enabled -> "已停止"
    rule.state == RuleState.RUNNING -> "运行中"
    rule.state == RuleState.PAUSED_ERROR -> "失败暂停"
    rule.state == RuleState.PAUSED_MANUAL -> "已暂停"
    rule.state == RuleState.PAUSED_LOOP -> "循环暂停"
    else -> "待命"
}

/** 被自动暂停的规则才显示「恢复」按钮。 */
fun canResume(rule: RuleEntity): Boolean =
    rule.enabled && (rule.state == RuleState.PAUSED_ERROR || rule.state == RuleState.PAUSED_LOOP)

// ---------- 日志 ----------

fun logTone(result: LogResult): Tone = when (result) {
    LogResult.MOVED, LogResult.COPIED -> Tone.OK
    LogResult.SKIPPED -> Tone.WARN
    LogResult.FAILED -> Tone.ERROR
    LogResult.UNDONE -> Tone.BUSY
    LogResult.INFO -> Tone.IDLE
}
