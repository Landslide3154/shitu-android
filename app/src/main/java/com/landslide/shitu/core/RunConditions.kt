package com.landslide.shitu.core

import com.landslide.shitu.data.Settings

/**
 * 运行条件判定（纯函数，方便单测）。
 *
 * 统一列表、从上到下先命中先算（顺序见 [ConditionOrder]）：
 * - 允许条件：充电时搬 / 亮屏时搬 / 锁屏时搬；
 * - 阻止条件：锁屏超过 X 分钟不搬 / 电量低于 X% 时不搬。
 *
 * 例：默认顺序是「充电 → 锁屏超时 → 电量低 → 亮屏 → 锁屏」，所以充电时会被第一条放行，
 * 不会因为锁屏久/电量低被后面拦住；把「锁屏超时」拖到充电上面，就变成超时优先。
 *
 * 旧的「熄屏暂停」（[Settings.screenOffPause]）与「低电量暂停」（[Settings.lowBatteryPause]）
 * 已从 UI 移除，这里不再参与判断，只保留字段兼容旧备份。
 *
 * 返回空列表 = 可以跑；否则是给通知看的原因。
 */
object RunConditions {

    fun unmet(
        settings: Settings,
        charging: Boolean,
        screenOn: Boolean,
        batteryPct: Int,
        lockedForMs: Long,
        nowHour: Int,
    ): List<String> {
        val order = ConditionOrder.normalize(settings.conditionOrder)

        for (id in order) {
            when (id) {
                ConditionOrder.CHARGING -> {
                    if (settings.runOnlyCharging && charging) return emptyList()
                }

                ConditionOrder.LOCK_TIMEOUT -> {
                    val min = settings.lockTimeoutMin
                    if (min > 0 && !screenOn && lockedForMs >= min * 60_000L) {
                        return listOf("锁屏超过 $min 分钟")
                    }
                }

                ConditionOrder.BATTERY -> {
                    val min = settings.runMinBatteryPct
                    if (min > 0 && batteryPct in 0 until min) {
                        return listOf("电量低于 $min%")
                    }
                }

                ConditionOrder.SCREEN_ON -> {
                    if (settings.runOnlyScreenOn && screenOn) return emptyList()
                }

                ConditionOrder.SCREEN_OFF -> {
                    if (settings.runOnlyScreenOff && !screenOn) return emptyList()
                }
            }
        }

        // 旧版夜间条件已从 UI 移除；老设置里还开着时，作为最低优先级的允许条件兜底
        if (settings.runOnlyNight &&
            RunWindow.inNightRange(settings.nightStartHour, settings.nightEndHour, nowHour)
        ) {
            return emptyList()
        }

        // 一个条件都没命中：只有开了「允许类」条件才需要继续等
        val wants = ArrayList<String>(4)
        for (id in order) {
            when (id) {
                ConditionOrder.CHARGING -> if (settings.runOnlyCharging) wants += "充电"
                ConditionOrder.SCREEN_ON -> if (settings.runOnlyScreenOn) wants += "亮屏"
                ConditionOrder.SCREEN_OFF -> if (settings.runOnlyScreenOff) wants += "锁屏"
                else -> Unit
            }
        }
        if (settings.runOnlyNight) {
            wants += "夜间 ${settings.nightStartHour}:00–${settings.nightEndHour}:00"
        }
        return if (wants.isEmpty()) {
            emptyList()
        } else {
            listOf("要满足其一：" + wants.joinToString(" / "))
        }
    }
}
