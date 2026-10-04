package com.landslide.shitu.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test
    fun `单轮 20 张后拒绝`() {
        val rl = RateLimiter(maxPerRun = 20, maxPerMinute = 120, maxRunMillis = 60_000)
        repeat(20) { assertTrue(rl.tryAcquire(t = it * 1000L)) }
        assertFalse(rl.tryAcquire(t = 20_000L))
    }

    @Test
    fun `单轮 60 秒超时`() {
        val rl = RateLimiter(20, 120, 60_000)
        assertTrue(rl.tryAcquire(0))
        assertFalse(rl.tryAcquire(60_001))
    }

    @Test
    fun `分钟窗口 120 张后拒绝`() {
        val rl = RateLimiter(maxPerRun = 500, maxPerMinute = 120, maxRunMillis = 600_000)
        repeat(120) { assertTrue(rl.tryAcquire(it.toLong())) }
        assertFalse(rl.tryAcquire(120L))
    }

    @Test
    fun `窗口滑动后恢复`() {
        val rl = RateLimiter(500, 120, 600_000)
        repeat(120) { rl.tryAcquire(it.toLong()) }
        assertTrue(rl.tryAcquire(60_001L))
    }

    @Test
    fun `endRun 重置单轮计数但保留分钟窗口`() {
        val rl = RateLimiter(2, 120, 60_000)
        assertTrue(rl.tryAcquire(0))
        assertTrue(rl.tryAcquire(1))
        assertFalse(rl.tryAcquire(2))
        rl.endRun()
        assertTrue(rl.tryAcquire(3))
    }
}
