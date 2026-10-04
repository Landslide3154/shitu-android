package com.landslide.shitu.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主题模式解析：跟随系统 / 强制浅色 / 强制深色。 */
class ThemeModeTest {

    @Test
    fun `跟随系统时听系统的`() {
        assertTrue(resolveDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveDark(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `选浅色就永远是浅色`() {
        assertFalse(resolveDark(ThemeMode.LIGHT, systemDark = true))
        assertFalse(resolveDark(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun `选深色就永远是深色`() {
        assertTrue(resolveDark(ThemeMode.DARK, systemDark = true))
        assertTrue(resolveDark(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun `不认识的取值按跟随系统处理`() {
        assertTrue(resolveDark(99, systemDark = true))
        assertFalse(resolveDark(-1, systemDark = false))
    }
}
