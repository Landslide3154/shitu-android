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
        lockTimeout: Int = 0,
        night: Boolean = false,
        order: List<String> = ConditionOrder.DEFAULT,
        lowBatteryPause: Boolean = false,
        screenOffPause: Boolean = false,
    ) = Settings(
        runOnlyCharging = charging,
        runOnlyScreenOn = screenOn,
        runOnlyScreenOff = screenOff,
        runOnlyNight = night,
        runMinBatteryPct = minBattery,
        lockTimeoutMin = lockTimeout,
        conditionOrder = order,
        lowBatteryPause = lowBatteryPause,
        screenOffPause = screenOffPause,
    )

    private fun unmet(
        s: Settings,
        charging: Boolean = false,
        screenOn: Boolean = false,
        batteryPct: Int = 50,
        lockedForMs: Long = 0L,
        nowHour: Int = 12,
    ) = RunConditions.unmet(
        settings = s,
        charging = charging,
        screenOn = screenOn,
        batteryPct = batteryPct,
        lockedForMs = lockedForMs,
        nowHour = nowHour,
    )

    @Test
    fun `全关时随时可跑`() {
        assertTrue(unmet(settings()).isEmpty())
    }

    @Test
    fun `默认顺序下充电优先，锁屏很久也照搬`() {
        val s = settings(charging = true, lockTimeout = 30)
        assertTrue(unmet(s, charging = true, screenOn = false, lockedForMs = 31 * 60_000L).isEmpty())
    }

    @Test
    fun `把锁屏超时排到充电上面，超时就拦住充电`() {
        val s = settings(
            charging = true,
            lockTimeout = 30,
            order = listOf(
                ConditionOrder.LOCK_TIMEOUT,
                ConditionOrder.CHARGING,
                ConditionOrder.BATTERY,
                ConditionOrder.SCREEN_ON,
                ConditionOrder.SCREEN_OFF,
            ),
        )
        val blocked = unmet(s, charging = true, screenOn = false, lockedForMs = 31 * 60_000L)
        assertEquals(1, blocked.size)
        assertTrue(blocked[0].contains("锁屏超过 30 分钟"))
    }

    @Test
    fun `把电量低排到充电上面，充电时也会被电量拦住`() {
        val s = settings(
            charging = true,
            minBattery = 20,
            order = listOf(
                ConditionOrder.LOCK_TIMEOUT,
                ConditionOrder.BATTERY,
                ConditionOrder.CHARGING,
                ConditionOrder.SCREEN_ON,
                ConditionOrder.SCREEN_OFF,
            ),
        )
        val blocked = unmet(s, charging = true, batteryPct = 5)
        assertEquals(1, blocked.size)
        assertTrue(blocked[0].contains("20"))
    }

    @Test
    fun `默认顺序下充电越过电量限制`() {
        val s = settings(charging = true, minBattery = 20)
        assertTrue(unmet(s, charging = true, batteryPct = 5).isEmpty())
    }

    @Test
    fun `没充电且电量低时会被拦住`() {
        val s = settings(screenOn = true, minBattery = 20)
        val blocked = unmet(s, charging = false, screenOn = true, batteryPct = 12)
        assertEquals(1, blocked.size)
        assertTrue(blocked[0].contains("电量低于 20%"))
    }

    @Test
    fun `亮屏时搬是允许条件`() {
        val s = settings(screenOn = true, lockTimeout = 30)
        assertTrue(unmet(s, screenOn = true, lockedForMs = 0L).isEmpty())
    }

    @Test
    fun `锁屏不久且开了锁屏时搬则正常搬`() {
        val s = settings(screenOff = true, lockTimeout = 30)
        assertTrue(unmet(s, screenOn = false, lockedForMs = 5 * 60_000L).isEmpty())
    }

    @Test
    fun `没开任何允许条件时，阻止条件不命中就照搬`() {
        val s = settings(lockTimeout = 30, minBattery = 20)
        assertTrue(unmet(s, batteryPct = 50, screenOn = true, lockedForMs = 0L).isEmpty())
    }

    @Test
    fun `开了允许条件但都没命中时给出原因`() {
        val s = settings(screenOn = true)
        val blocked = unmet(s, screenOn = false)
        assertEquals(1, blocked.size)
        assertTrue(blocked[0].contains("亮屏"))
    }

    @Test
    fun `旧熄屏暂停与低电量暂停字段不再参与判断`() {
        val s = settings(lowBatteryPause = true, screenOffPause = true)
        assertTrue(unmet(s, screenOn = false, batteryPct = 5).isEmpty())
    }

    @Test
    fun `隐藏的夜间条件仍可兜底`() {
        val s = settings(night = true)
        assertTrue(unmet(s, nowHour = 23).isEmpty())
        assertTrue(unmet(s, nowHour = 12).isNotEmpty())
    }

    @Test
    fun `原因列表按优先级顺序排列`() {
        val s = settings(charging = true, screenOn = true)
        val blocked = unmet(s, charging = false, screenOn = false)
        assertEquals(1, blocked.size)
        assertTrue(blocked[0].indexOf("充电") < blocked[0].indexOf("亮屏"))
    }
}
