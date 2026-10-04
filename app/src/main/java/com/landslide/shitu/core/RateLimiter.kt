package com.landslide.shitu.core

/**
 * 三重限速闸门（规格 §9.4）：单轮数量、单轮时长、每分钟数量。
 * t 单位为毫秒（单调时钟）。任一触发即返回 false，本轮结束。
 */
class RateLimiter(
    private val maxPerRun: Int,
    private val maxPerMinute: Int,
    private val maxRunMillis: Long,
) {
    private var runStart = -1L
    private var runCount = 0
    private val recent = ArrayDeque<Long>()

    fun tryAcquire(t: Long): Boolean {
        if (runStart < 0) {
            runStart = t
            runCount = 0
        }
        if (t - runStart > maxRunMillis) return false
        if (runCount >= maxPerRun) return false
        while (recent.isNotEmpty() && t - recent.first() >= 60_000) recent.removeFirst()
        if (recent.size >= maxPerMinute) return false
        runCount++
        recent.addLast(t)
        return true
    }

    fun endRun() {
        runStart = -1L
        runCount = 0
    }
}
