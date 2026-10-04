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
}
