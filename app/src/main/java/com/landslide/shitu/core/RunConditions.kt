package com.landslide.shitu.core

import com.landslide.shitu.data.Settings

/**
 * 运行条件判定（纯函数，方便单测）。
 *
 * 语义（和设置页文案一致）：
 * - **场景条件**：充电时搬 / 亮屏时搬 / 熄屏时搬 / 夜间，是多选；同时开多个 = **满足任一即可**，
 *   全关 = 任何时候都搬。旧实现是「每项都必须满足」，导致「充电 + 亮屏」两开时，
 *   熄屏放一晚上都等不到「亮屏」、图片一张不搬。
 * - **电量**是否决条件：不充电时，电量低于阈值或系统省电模式就暂停；**插着充电器时不受电量限制**。
 *
 * 返回空列表 = 可以跑；否则是给通知看的原因。
 */
object RunConditions {

    fun unmet(
        settings: Settings,
        charging: Boolean,
        screenOn: Boolean,
        batteryPct: Int,
        powerSaveMode: Boolean,
        nowHour: Int,
    ): List<String> {
        val out = ArrayList<String>(3)

        // 电量否决：充电优先，插着电时不受电量/省电条件限制
        if (!charging) {
            if (settings.lowBatteryPause &&
                (powerSaveMode || batteryPct in 1..settings.lowBatteryPct)
            ) {
                out += "低电量/省电模式暂停"
            }
            if (settings.runMinBatteryPct > 0 && batteryPct in 0 until settings.runMinBatteryPct) {
                out += "电量低于 ${settings.runMinBatteryPct}%"
            }
        }

        // 场景条件：多选时是「或」，不是「且」
        val anySelected = settings.runOnlyCharging || settings.runOnlyScreenOn ||
            settings.runOnlyScreenOff || settings.runOnlyNight
        if (anySelected) {
            val nightOk = RunWindow.inNightRange(
                settings.nightStartHour,
                settings.nightEndHour,
                nowHour,
            )
            val ok = (settings.runOnlyCharging && charging) ||
                (settings.runOnlyScreenOn && screenOn) ||
                (settings.runOnlyScreenOff && !screenOn) ||
                (settings.runOnlyNight && nightOk)
            if (!ok) {
                val wants = buildList {
                    if (settings.runOnlyCharging) add("充电")
                    if (settings.runOnlyScreenOn) add("亮屏")
                    if (settings.runOnlyScreenOff) add("熄屏")
                    if (settings.runOnlyNight) {
                        add("夜间 ${settings.nightStartHour}:00–${settings.nightEndHour}:00")
                    }
                }
                out += "要满足其一：" + wants.joinToString(" / ")
            }
        }
        return out
    }
}
