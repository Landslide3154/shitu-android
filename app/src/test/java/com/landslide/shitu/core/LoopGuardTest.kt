package com.landslide.shitu.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopGuardTest {
    @Test
    fun `30 分钟内同名 3 次触发暂停`() {
        val g = LoopGuard(windowMillis = 30 * 60_000, threshold = 3)
        assertFalse(g.record("a.png", 0))
        assertFalse(g.record("a.png", 10_000))
        assertTrue(g.record("a.png", 20_000))
    }

    @Test
    fun `超出窗口的不计数`() {
        val g = LoopGuard(30 * 60_000, 3)
        g.record("a.png", 0)
        g.record("a.png", 10_000)
        assertFalse(g.record("a.png", 31 * 60_000))
    }

    @Test
    fun `不同文件名互不影响`() {
        val g = LoopGuard(30 * 60_000, 3)
        assertFalse(g.record("a.png", 0))
        assertFalse(g.record("b.png", 0))
        assertFalse(g.record("a.png", 1))
        assertTrue(g.record("a.png", 2)) // a 达到阈值
        assertFalse(g.record("b.png", 1)) // b 只有 2 次，不受 a 影响
    }
}
