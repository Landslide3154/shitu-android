package com.landslide.shitu.data.db

enum class Mode { MOVE, COPY }

enum class RuleState { IDLE, RUNNING, PAUSED_MANUAL, PAUSED_ERROR, PAUSED_LOOP }

enum class ItemStatus { PENDING, MOVED, COPIED, FAILED, SKIPPED }

enum class LogResult { MOVED, COPIED, SKIPPED, FAILED, UNDONE, INFO }

/** 供界面显示的中文标签。 */
object Labels {
    fun mode(m: Mode) = if (m == Mode.MOVE) "移动" else "复制"

    fun state(s: RuleState) = when (s) {
        RuleState.IDLE -> "待命"
        RuleState.RUNNING -> "运行中"
        RuleState.PAUSED_MANUAL -> "已暂停（手动）"
        RuleState.PAUSED_ERROR -> "已暂停（连续失败）"
        RuleState.PAUSED_LOOP -> "已暂停（疑似循环）"
    }

    fun result(r: LogResult) = when (r) {
        LogResult.MOVED -> "已移动"
        LogResult.COPIED -> "已复制"
        LogResult.SKIPPED -> "已跳过"
        LogResult.FAILED -> "失败"
        LogResult.UNDONE -> "已撤回"
        LogResult.INFO -> "信息"
    }

    fun itemStatus(s: ItemStatus) = when (s) {
        ItemStatus.PENDING -> "待处理"
        ItemStatus.MOVED -> "已移动"
        ItemStatus.COPIED -> "已复制"
        ItemStatus.FAILED -> "失败"
        ItemStatus.SKIPPED -> "已跳过"
    }
}
