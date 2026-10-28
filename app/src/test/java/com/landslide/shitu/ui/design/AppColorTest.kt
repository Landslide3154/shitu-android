package com.landslide.shitu.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FlClash 配色模型的回归测试：主色 tone 夹取、暗色表面、纯黑、四种风格。 */
class AppColorTest {

    @Test
    fun `亮色 vivid 主色就是种子色`() {
        val scheme = AppColor.scheme(AppPalette.Blue, dark = false, style = ColorStyle.VIVID)
        assertEquals(0xFF007AFF.toInt(), scheme.primary.toArgb())
    }

    @Test
    fun `暗色主色提亮 3 tone 且落在 50-75`() {
        val scheme = AppColor.scheme(AppPalette.Blue, dark = true, style = ColorStyle.VIVID)
        val tone = toneOf(scheme.primary)
        assertTrue("tone=$tone", tone in 50.0..75.0)
        assertTrue("应比亮色种子亮", tone > toneOf(AppPalette.Blue))
    }

    @Test
    fun `暗色表面 15-21-27 与 onSurface 97`() {
        val scheme = AppColor.scheme(AppPalette.Blue, dark = true, style = ColorStyle.VIVID)
        assertTrue(toneOf(scheme.surfaceContainer) in 13.0..17.0)
        assertTrue(toneOf(scheme.surfaceContainerHigh) in 19.0..23.0)
        assertTrue(toneOf(scheme.surfaceContainerHighest) in 25.0..29.0)
        assertTrue(toneOf(scheme.onSurface) in 95.0..99.0)
    }

    @Test
    fun `纯黑只改暗色表面`() {
        val dark = AppColor.scheme(AppPalette.Blue, dark = true, pureBlack = true)
        assertEquals(Color.Black, dark.surface)
        assertEquals(Color.Black, dark.surfaceContainerLowest)
        assertTrue(toneOf(dark.surfaceContainer) < toneOf(
            AppColor.scheme(AppPalette.Blue, dark = true).surfaceContainer,
        ))

        val light = AppColor.scheme(AppPalette.Blue, dark = false, pureBlack = true)
        assertNotEquals(Color.Black, light.surface)
    }

    @Test
    fun `tinted 表面带种子色相`() {
        val vivid = AppColor.scheme(AppPalette.Blue, dark = false, style = ColorStyle.VIVID)
        val tinted = AppColor.scheme(AppPalette.Blue, dark = false, style = ColorStyle.TINTED)
        assertTrue(
            chromaOf(tinted.surfaceContainer) > chromaOf(vivid.surfaceContainer),
        )
    }

    @Test
    fun `monochrome 表面是无彩色`() {
        val scheme = AppColor.scheme(AppPalette.Blue, dark = false, style = ColorStyle.MONOCHROME)
        val chroma = chromaOf(scheme.surfaceContainer)
        assertTrue("chroma=$chroma", chroma < 3.0)
    }

    private fun chromaOf(color: Color): Double =
        com.google.android.material.color.utilities.Hct
            .fromInt(color.toArgb())
            .chroma
}
