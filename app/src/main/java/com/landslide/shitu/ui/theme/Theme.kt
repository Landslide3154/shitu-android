package com.landslide.shitu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 主题模式：0 = 跟随系统（默认），1 = 一直浅色，2 = 一直深色。 */
object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/** 纯函数，方便单测：把「用户选择 + 系统当前是否深色」算成「这次要不要用深色」。 */
fun resolveDark(mode: Int, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    else -> systemDark
}

private val Blue = Color(0xFF1F6FEB)

private val Light = lightColorScheme(
    primary = Blue,
    secondary = Color(0xFF3B82F6),
    tertiary = Color(0xFFF5A623),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF93C5FD),
    tertiary = Color(0xFFFFC46B),
)

/**
 * @param themeMode 跟随系统 / 浅色 / 深色（见 [ThemeMode]）
 * @param dynamicColor Material You 动态取色（Android 12+，从壁纸取色；关掉就用上面的蓝）
 */
@Composable
fun ShituTheme(
    themeMode: Int = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = resolveDark(themeMode, isSystemInDarkTheme())
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
