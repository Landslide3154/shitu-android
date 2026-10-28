package com.landslide.shitu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.landslide.shitu.ui.design.AppPalette
import com.landslide.shitu.ui.design.AppTheme
import com.landslide.shitu.ui.design.ColorStyle

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

/**
 * 主题装配入口（保留旧签名，内部走 FlClash 令牌体系）。
 *
 * @param themeMode 跟随系统 / 浅色 / 深色（见 [ThemeMode]）
 * @param dynamicColor 跟随系统壁纸取种子色（FlClash `primaryColor = null`）
 * @param seedColor 固定种子色；为空且 dynamicColor 时用壁纸主色
 * @param colorStyle 四种配色风格（vivid/tinted/soft/monochrome）
 * @param pureBlack 纯黑（仅暗色）
 * @param textScale 主题中心里的文字缩放
 */
@Composable
fun ShituTheme(
    themeMode: Int = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    colorStyle: ColorStyle = ColorStyle.VIVID,
    pureBlack: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = resolveDark(themeMode, isSystemInDarkTheme())
    AppTheme(
        dark = dark,
        dynamicColor = dynamicColor,
        seedColor = seedColor,
        colorStyle = colorStyle,
        pureBlack = pureBlack,
        textScale = textScale,
        content = content,
    )
}

/** 默认种子色：FlClash 的 iOS 蓝。 */
val DefaultSeedColor = AppPalette.Blue

/** 主题模式是否支持动态取色（Android 12+ 才有系统壁纸色）。 */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
