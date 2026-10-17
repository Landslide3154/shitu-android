package com.landslide.shitu.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunWindowTest {

    @Test
    fun `普通区间包含开始小时不包含结束小时`() {
        assertTrue(RunWindow.inNightRange(1, 6, 1))
        assertTrue(RunWindow.inNightRange(1, 6, 3))
        assertTrue(RunWindow.inNightRange(1, 6, 5))
        assertFalse(RunWindow.inNightRange(1, 6, 6))
        assertFalse(RunWindow.inNightRange(1, 6, 0))
    }

    @Test
    fun `普通区间之外的小时不匹配`() {
        assertFalse(RunWindow.inNightRange(9, 18, 8))
        assertFalse(RunWindow.inNightRange(9, 18, 18))
        assertFalse(RunWindow.inNightRange(9, 18, 23))
    }

    @Test
    fun `跨午夜区间覆盖前一天晚上与第二天凌晨`() {
        assertTrue(RunWindow.inNightRange(22, 7, 22))
        assertTrue(RunWindow.inNightRange(22, 7, 23))
        assertTrue(RunWindow.inNightRange(22, 7, 0))
        assertTrue(RunWindow.inNightRange(22, 7, 6))
    }

    @Test
    fun `跨午夜区间不含结束小时也不含白天`() {
        assertFalse(RunWindow.inNightRange(22, 7, 7))
        assertFalse(RunWindow.inNightRange(22, 7, 12))
        assertFalse(RunWindow.inNightRange(22, 7, 21))
    }

    @Test
    fun `开始与结束相同视为不限制`() {
        assertTrue(RunWindow.inNightRange(0, 0, 0))
        assertTrue(RunWindow.inNightRange(0, 0, 12))
        assertTrue(RunWindow.inNightRange(5, 5, 23))
    }

    @Test
    fun `23 与 0 作为边界时按普通半开区间处理`() {
        assertTrue(RunWindow.inNightRange(0, 1, 0))
        assertFalse(RunWindow.inNightRange(0, 1, 1))
        assertTrue(RunWindow.inNightRange(23, 0, 23))
        assertFalse("23–0 跨午夜，0 点属于结束小时不包含", RunWindow.inNightRange(23, 0, 0))
    }

    @Test
    fun `非法的小时先夹到 0 到 23 再判断`() {
        // 99 → 23：落在 22–7 里
        assertTrue(RunWindow.inNightRange(22, 7, 99))
        // -5 → 0：也落在 22–7 里
        assertTrue(RunWindow.inNightRange(22, 7, -5))
        // 25 → 23，不在 1–2 里
        assertFalse(RunWindow.inNightRange(1, 2, 25))
        // -1 → 0，不在 1–2 里
        assertFalse(RunWindow.inNightRange(1, 2, -1))
    }

    @Test
    fun `非法的开始与结束小时先夹到 0 到 23 再判断`() {
        // start 30 → 23，end 5：变成 23–5 的跨午夜区间
        assertTrue(RunWindow.inNightRange(30, 5, 23))
        assertFalse(RunWindow.inNightRange(30, 5, 12))
        // start 99 → 23，end 25 → 23：夹完相等 → 不限制
        assertTrue(RunWindow.inNightRange(99, 25, 3))
        // start -1 → 0，end -5 → 0：夹完相等 → 不限制
        assertTrue(RunWindow.inNightRange(-1, -5, 20))
    }
}
