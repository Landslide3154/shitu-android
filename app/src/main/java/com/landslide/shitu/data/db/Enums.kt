package com.landslide.shitu.data.db

import android.content.Context
import com.landslide.shitu.R

enum class Mode { MOVE, COPY }

enum class RuleState { IDLE, RUNNING, PAUSED_MANUAL, PAUSED_ERROR, PAUSED_LOOP }

enum class ItemStatus { PENDING, MOVED, COPIED, FAILED, SKIPPED }

enum class LogResult { MOVED, COPIED, SKIPPED, FAILED, UNDONE, INFO }

/** 供界面显示的本地化标签。 */
object Labels {
    fun mode(context: Context, m: Mode): String = context.getString(
        if (m == Mode.MOVE) R.string.rules_mode_move else R.string.rules_mode_copy,
    )

    fun state(context: Context, s: RuleState): String = context.getString(
        when (s) {
            RuleState.IDLE -> R.string.rules_state_idle
            RuleState.RUNNING -> R.string.rules_state_running
            RuleState.PAUSED_MANUAL -> R.string.rules_state_paused_manual
            RuleState.PAUSED_ERROR -> R.string.rules_state_paused_error
            RuleState.PAUSED_LOOP -> R.string.rules_state_paused_loop
        },
    )

    fun result(context: Context, r: LogResult): String = context.getString(
        when (r) {
            LogResult.MOVED -> R.string.rules_result_moved
            LogResult.COPIED -> R.string.rules_result_copied
            LogResult.SKIPPED -> R.string.rules_result_skipped
            LogResult.FAILED -> R.string.rules_result_failed
            LogResult.UNDONE -> R.string.rules_result_undone
            LogResult.INFO -> R.string.rules_result_info
        },
    )

    fun itemStatus(context: Context, s: ItemStatus): String = context.getString(
        when (s) {
            ItemStatus.PENDING -> R.string.rules_item_pending
            ItemStatus.MOVED -> R.string.rules_item_moved
            ItemStatus.COPIED -> R.string.rules_item_copied
            ItemStatus.FAILED -> R.string.rules_item_failed
            ItemStatus.SKIPPED -> R.string.rules_item_skipped
        },
    )
}
