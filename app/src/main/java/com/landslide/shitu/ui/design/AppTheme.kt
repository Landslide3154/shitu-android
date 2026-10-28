package com.landslide.shitu.ui.design

import android.app.WallpaperManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 全 App 主题装配点（对应 FlClash 的 `withAppSurfaces` + `withAppTypography` +
 * `withAppShapes` 三处全局注入）。
 *
 * @param dark 已解析好的明暗（由调用方的 themeMode + 系统设置决定）
 * @param dynamicColor 跟随系统壁纸取种子色（FlClash `primaryColor = null`）
 * @param seedColor 固定种子色；为空且 [dynamicColor] 时用壁纸主色
 * @param colorStyle 四种风格
 * @param pureBlack 纯黑（只对暗色生效）
 * @param textScale 主题中心里的文字缩放（0.8–1.4）
 */
@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    colorStyle: ColorStyle = ColorStyle.VIVID,
    pureBlack: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val systemSeed = rememberSystemSeedColor()
    val seed = seedColor ?: if (dynamicColor) systemSeed else AppPalette.Blue
    val scheme = remember(seed, dark, colorStyle, pureBlack) {
        AppColor.scheme(seed, dark, colorStyle, pureBlack)
    }
    val density = LocalDensity.current
    val scale = remember(density.fontScale, textScale) {
        appFontScale(density.fontScale, textScale)
    }
    val reduceMotion = rememberReduceMotion()

    CompositionLocalProvider(
        LocalDensity provides Density(density.density, scale),
        LocalSurfaceLevel provides SurfaceLevel.PAGE,
        LocalReduceMotion provides reduceMotion,
        LocalAppTextScale provides textScale,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * 系统壁纸主色作为种子。FlClash 通过平台插件取壁纸色；Android 上用 WallpaperManager
 * 的 FLAG_SYSTEM 主色，拿不到再退回 FlClash 默认蓝。
 */
@Composable
fun rememberSystemSeedColor(): Color {
    val context = LocalContext.current
    return remember(context) {
        val platformColor: android.graphics.Color? = runCatching {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
        }.getOrNull()
        platformColor?.let { Color(it.toArgb()) } ?: AppPalette.Blue
    }
}
