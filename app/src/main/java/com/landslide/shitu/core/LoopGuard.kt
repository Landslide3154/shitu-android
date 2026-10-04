package com.landslide.shitu.core

/**
 * 重复抑制（规格 §9.5）：同一 basename 在滚动窗口内被搬走次数达到阈值，
 * 判定为「App 会自动重建」，应暂停该规则。
 */
class LoopGuard(private val windowMillis: Long, private val threshold: Int) {
    private val hits = HashMap<String, ArrayDeque<Long>>()

    /** 记录一次成功搬移，返回是否已达阈值。 */
    fun record(basename: String, t: Long): Boolean {
        val q = hits.getOrPut(basename) { ArrayDeque() }
        while (q.isNotEmpty() && t - q.first() > windowMillis) q.removeFirst()
        q.addLast(t)
        return q.size >= threshold
    }

    fun reset() = hits.clear()
}
