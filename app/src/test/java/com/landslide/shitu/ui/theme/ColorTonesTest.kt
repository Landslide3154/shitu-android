package com.landslide.shitu.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FlClash 迁移 P1：暗色表面 tone 15/21/27 + onSurface 97 的纯逻辑校验。 */
class ColorTonesTest {

    private val base = Color(0xFF141218)

    @Test
    fun `tonal 把颜色压到指定 L 星`() {
        listOf(15f, 21f, 27f, 97f).forEach { tone ->
            val actual = lightnessOf(tonal(base, tone))
            assertEquals("tone=$tone 实际=$actual", tone, actual, 2f)
        }
    }

    @Test
    fun `暗色表面层级递增`() {
        val low = lightnessOf(tonal(base, 12f))
        val container = lightnessOf(tonal(base, 15f))
        val high = lightnessOf(tonal(base, 21f))
        val highest = lightnessOf(tonal(base, 27f))
        assertTrue("$low < $container", low < container)
        assertTrue("$container < $high", container < high)
        assertTrue("$high < $highest", high < highest)
    }

    @Test
    fun `暗色覆写不动 surface 与 primary`() {
        val before = darkColorScheme(primary = Color(0xFF8AB4F8))
        val after = before.withShituDarkTones()
        assertEquals(before.surface, after.surface)
        assertEquals(before.primary, after.primary)
        assertTrue(lightnessOf(after.surfaceContainer) in 13f..17f)
        assertTrue(lightnessOf(after.surfaceContainerHigh) in 19f..23f)
        assertTrue(lightnessOf(after.surfaceContainerHighest) in 25f..29f)
        assertTrue(lightnessOf(after.onSurface) > 95f)
    }
}
