package com.landslide.shitu.core

/**
 * 「夜间时段」判断（设置里的运行条件）。
 *
 * 跨午夜是常态（22:00–07:00），所以不能写成 `hour in start until end`；
 * 开始等于结束时视为不限制（返回 true），避免用户手滑设成 0–0 导致永远不搬。
 */
object RunWindow {

    fun inNightRange(startHour: Int, endHour: Int, hour: Int): Boolean {
        val s = startHour.coerceIn(0, 23)
        val e = endHour.coerceIn(0, 23)
        val h = hour.coerceIn(0, 23)
        return when {
            s == e -> true
            s < e -> h in s until e
            else -> h >= s || h < e
        }
    }
}
