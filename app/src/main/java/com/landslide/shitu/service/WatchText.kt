package com.landslide.shitu.service

/**
 * 常驻通知正文（纯函数，方便单测）。
 *
 * 之前的通知在服务刚启动时先挂一句「正在启动…」，而屏幕亮着时 WatchService 走的是
 * 「快节奏检测」分支 —— 那条分支只有真的搬了东西才会刷新通知，于是通知会一直停在
 * 「正在启动…」。现在启动时就写真实状态，并且每个检测分支每轮都会刷新。
 *
 * @param moved 累计已搬张数（服务启动时从规则表恢复，进程重启后不会退回 0）
 * @param lastRunLabel 上次运行时间（HH:mm）；从没跑过传 null
 * @param status 本轮附加状态（「没有到期规则」「等运行条件」等），可为 null
 */
internal fun watchText(moved: Long, lastRunLabel: String?, status: String? = null): String {
    val base = when {
        lastRunLabel != null -> "已搬 $moved 张 · 上次 $lastRunLabel"
        moved > 0 -> "已搬 $moved 张"
        else -> null
    }
    val extra = status?.takeIf { it.isNotBlank() }
    return listOfNotNull(base, extra).joinToString(" · ").ifEmpty { "等待图片变化" }
}
