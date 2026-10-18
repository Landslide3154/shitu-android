package com.landslide.shitu.core

import com.landslide.shitu.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunConditionsTest {

    private fun settings(
        charging: Boolean = false,
        screenOn: Boolean = false,
        screenOff: Boolean = false,
        minBattery: Int = 0,
        lowBatteryPause: Boolean = false,
        lowBatteryPct: Int = 15,
        screenOffPause: Boolean = false,
    ) = Settings(
        runOnlyCharging = charging,
        runOnlyScreenOn = screenOn,
        runOnlyScreenOff = screenOff,
        runMinBatteryPct = minBattery,
        lowBatteryPause = lowBatteryPause,
        lowBatteryPct = lowBatteryPct,
        screenOffPause = screenOffPause,
    )

    @Test
    fun `全关时随时可跑`() {
        val unmet = RunConditions.unmet(
            settings(), charging = false, screenOn = false, batteryPct = 10,
            powerSaveMode = false, nowHour = 12,
        )
        assertTrue(unmet.isEmpty())
    }

    @Test
    fun `充电和亮屏同时开时满足任一即可`() {
        val s = settings(charging = true, screenOn = true)
        // 熄屏但插着电：充电这一条满足，应该可以跑（旧版会卡在「要等亮屏」）
        assertTrue(
            RunConditions.unmet(s, charging = true, screenOn = false, batteryPct = 50, powerSaveMode = false, nowHour = 12).isEmpty(),
        )
        // 没充电但亮屏：亮屏这一条满足
        assertTrue(
            RunConditions.unmet(s, charging = false, screenOn = true, batteryPct = 50, powerSaveMode = false, nowHour = 12).isEmpty(),
        )
    }

    @Test
    fun `充电和亮屏同时开且两个都不满足时给出原因`() {
        val s = settings(charging = true, screenOn = true)
        val unmet = RunConditions.unmet(
            s, charging = false, screenOn = false, batteryPct = 50,
            powerSaveMode = false, nowHour = 12,
        )
        assertEquals(1, unmet.size)
        assertTrue(unmet[0].contains("充电"))
        assertTrue(unmet[0].contains("亮屏"))
    }

    @Test
    fun `亮屏和熄屏同时开等于任何时候`() {
        val s = settings(screenOn = true, screenOff = true)
        assertTrue(
            RunConditions.unmet(s, charging = false, screenOn = true, batteryPct = 50, powerSaveMode = false, nowHour = 12).isEmpty(),
        )
        assertTrue(
            RunConditions.unmet(s, charging = false, screenOn = false, batteryPct = 50, powerSaveMode = false, nowHour = 12).isEmpty(),
        )
    }

    @Test
    fun `只开充电时没插电就等条件`() {
        val s = settings(charging = true)
        val unmet = RunConditions.unmet(
            s, charging = false, screenOn = true, batteryPct = 50,
            powerSaveMode = false, nowHour = 12,
        )
        assertEquals(1, unmet.size)
        assertTrue(unmet[0].contains("充电"))
    }

    @Test
    fun `电量低于阈值且没充电时暂停`() {
        val s = settings(minBattery = 20)
        val unmet = RunConditions.unmet(
            s, charging = false, screenOn = true, batteryPct = 12,
            powerSaveMode = false, nowHour = 12,
        )
        assertTrue(unmet.any { it.contains("20") })
    }

    @Test
    fun `插着充电器时不受电量阈值限制`() {
        val s = settings(minBattery = 20)
        assertTrue(
            RunConditions.unmet(s, charging = true, screenOn = false, batteryPct = 5, powerSaveMode = false, nowHour = 12).isEmpty(),
        )
    }

    @Test
    fun `省电模式且没充电时暂停，充电时豁免`() {
        val s = settings(lowBatteryPause = true, lowBatteryPct = 15)
        val blocked = RunConditions.unmet(
            s, charging = false, screenOn = true, batteryPct = 50,
            powerSaveMode = true, nowHour = 12,
        )
        assertTrue(blocked.any { it.contains("省电") })
        assertTrue(
            RunConditions.unmet(s, charging = true, screenOn = true, batteryPct = 50, powerSaveMode = true, nowHour = 12).isEmpty(),
        )
    }

    @Test
    fun `夜间条件按时间判断`() {
        val s = Settings(runOnlyNight = true, nightStartHour = 22, nightEndHour = 7)
        assertTrue(
            RunConditions.unmet(s, charging = false, screenOn = false, batteryPct = 50, powerSaveMode = false, nowHour = 23).isEmpty(),
        )
        assertTrue(
            RunConditions.unmet(s, charging = false, screenOn = false, batteryPct = 50, powerSaveMode = false, nowHour = 12).isNotEmpty(),
        )
    }

    @Test
    fun `旧的熄屏暂停字段不再影响运行条件`() {
        // UI 早就没有这个开关了；旧设置里可能还留着 true，不能让它悄悄盖掉「全关 = 任何时候都搬」
        val s = settings(screenOffPause = true)
        assertTrue(
            RunConditions.unmet(s, charging = false, screenOn = false, batteryPct = 50, powerSaveMode = false, nowHour = 12).isEmpty(),
        )
    }
}
