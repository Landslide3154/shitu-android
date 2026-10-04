package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLabelTest {

    @Test
    fun `从 Android data 路径取包名`() {
        assertEquals(
            "com.qidian.QDReader",
            AppLabel.packageFromPath("/sdcard/Android/data/com.qidian.QDReader/files/QDReader/download"),
        )
    }

    @Test
    fun `兼容 storage 前缀与结尾斜杠`() {
        assertEquals(
            "com.a.b",
            AppLabel.packageFromPath("/storage/emulated/0/Android/data/com.a.b"),
        )
    }

    @Test
    fun `普通目录取不到包名`() {
        assertNull(AppLabel.packageFromPath("/sdcard/Pictures/拾图"))
        assertNull(AppLabel.packageFromPath("/sdcard/Android/data/notapkg"))
    }

    @Test
    fun `包名最后一段作为兜底标签`() {
        assertEquals("QDReader", AppLabel.lastSegment("com.qidian.QDReader"))
    }

    @Test
    fun `后缀模板 app 用来源 App 名`() {
        assertEquals("起点读书", AppLabel.expandSuffix("{app}", "起点读书"))
        assertEquals("封面素材", AppLabel.expandSuffix("封面素材", "起点读书"))
        assertEquals(null, AppLabel.expandSuffix("", "起点读书"))
        assertEquals(null, AppLabel.expandSuffix("   ", "起点读书"))
        // 取不到 App 名时不加后缀，而不是加个空段
        assertEquals(null, AppLabel.expandSuffix("{app}", ""))
    }

    @Test
    fun `自定义后缀会吃掉多余的连接符`() {
        assertEquals("拾图", AppLabel.expandSuffix("_拾图", "起点读书"))
        assertEquals("拾图", AppLabel.expandSuffix("拾图", "起点读书"))
        assertEquals("my-tag", AppLabel.expandSuffix("-my-tag", "起点读书"))
        assertEquals(null, AppLabel.expandSuffix("_", "起点读书"))
    }
}
