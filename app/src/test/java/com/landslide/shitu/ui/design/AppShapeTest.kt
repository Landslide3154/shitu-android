package com.landslide.shitu.ui.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 圆角阶梯与超椭圆路径的回归测试。 */
class AppShapeTest {

    @Test
    fun `fit 不超过最短边三分之一`() {
        assertEquals(0.dp, AppCorner.fit(9.dp))
        assertEquals(4.dp, AppCorner.fit(12.dp))
        assertEquals(8.dp, AppCorner.fit(30.dp))
        assertEquals(20.dp, AppCorner.fit(60.dp))
        assertEquals(24.dp, AppCorner.fit(75.dp))
        assertEquals(28.dp, AppCorner.fit(90.dp))
        assertEquals(28.dp, AppCorner.fit(120.dp))
    }

    @Test
    fun `full 退化为胶囊`() {
        val outline = squircle(AppCorner.full)
            .createOutline(Size(200f, 40f), LayoutDirection.Ltr, Density(1f))
        assertTrue(outline is Outline.Rounded)
    }
}
