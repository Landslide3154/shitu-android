package com.landslide.shitu.core

/**
 * 运行条件的优先级顺序。
 *
 * 统一列表、从上到下先命中先算：
 * - 允许条件：充电时搬 / 亮屏时搬 / 锁屏时搬；
 * - 阻止条件：锁屏超过 X 分钟不搬 / 电量低于 X% 时不搬。
 *
 * 默认顺序是「充电 → 锁屏超时 → 电量低 → 亮屏 → 锁屏」：
 * 充电时怎么都搬；不充电时锁屏久了或电量低就停；亮屏、锁屏不久时正常搬。
 */
object ConditionOrder {
    const val CHARGING = "charging"
    const val LOCK_TIMEOUT = "lock_timeout"
    const val BATTERY = "battery"
    const val SCREEN_ON = "screen_on"
    const val SCREEN_OFF = "screen_off"

    val DEFAULT = listOf(CHARGING, LOCK_TIMEOUT, BATTERY, SCREEN_ON, SCREEN_OFF)

    private val known = DEFAULT.toSet()

    /** 只保留已知 id、去重，缺的按默认顺序补到最后。 */
    fun normalize(raw: List<String>): List<String> {
        val out = ArrayList<String>(DEFAULT.size)
        for (id in raw) {
            if (id in known && id !in out) out += id
        }
        for (id in DEFAULT) {
            if (id !in out) out += id
        }
        return out
    }

    fun decode(raw: String?): List<String> =
        normalize(raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })

    fun encode(order: List<String>): String = normalize(order).joinToString(",")
}
