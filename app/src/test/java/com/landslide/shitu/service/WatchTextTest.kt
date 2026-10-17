package com.landslide.shitu.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTextTest {

    @Test
    fun `刚启动还没跑过时显示等待图片变化`() {
        assertEquals("等待图片变化", watchText(0, null))
    }

    @Test
    fun `有上次运行时间时显示累计张数与时间`() {
        assertEquals("已搬 658 张 · 上次 22:46", watchText(658, "22:46"))
    }

    @Test
    fun `有张数但没时间时只显示张数`() {
        assertEquals("已搬 658 张", watchText(658, null))
    }

    @Test
    fun `状态追加在正文后面`() {
        assertEquals(
            "已搬 660 张 · 上次 22:58 · 快速检测：搬 2 张",
            watchText(660, "22:58", "快速检测：搬 2 张"),
        )
    }

    @Test
    fun `空白状态不产生多余分隔符`() {
        assertEquals("等待图片变化", watchText(0, null, "  "))
    }

    @Test
    fun `没有历史时有状态就只显示状态`() {
        assertEquals("没有搬图通道：Shizuku 未运行", watchText(0, null, "没有搬图通道：Shizuku 未运行"))
    }
}
