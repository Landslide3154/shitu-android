package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConditionOrderTest {

    @Test
    fun `默认顺序是充电、锁屏超时、电量、亮屏、锁屏`() {
        assertEquals(
            listOf("charging", "lock_timeout", "battery", "screen_on", "screen_off"),
            ConditionOrder.DEFAULT,
        )
    }

    @Test
    fun `decode 保留用户顺序，缺的补默认，未知项丢掉`() {
        val order = ConditionOrder.decode("battery,charging,unknown,battery")
        assertEquals(
            listOf("battery", "charging", "lock_timeout", "screen_on", "screen_off"),
            order,
        )
    }

    @Test
    fun `空值或脏数据回落到默认顺序`() {
        assertEquals(ConditionOrder.DEFAULT, ConditionOrder.decode(null))
        assertEquals(ConditionOrder.DEFAULT, ConditionOrder.decode(""))
        assertEquals(ConditionOrder.DEFAULT, ConditionOrder.decode(",,,,"))
    }

    @Test
    fun `encode 与 decode 往返一致`() {
        val order = listOf("screen_on", "charging", "battery", "lock_timeout", "screen_off")
        assertEquals(order, ConditionOrder.decode(ConditionOrder.encode(order)))
    }
}
