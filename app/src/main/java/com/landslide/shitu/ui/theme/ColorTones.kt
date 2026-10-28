package com.landslide.shitu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 按 M3 的「tone」把颜色压到指定明度（0–100，近似 CIELAB L*）。
 *
 * 只是给表面色用的：保留原色相的色相和饱和度，只换明度。近中性的表面色里
 * HSL 明度与 L* 在暗部几乎重合（tone 15 ≈ HSL 0.15），足够用来做 15/21/27 的层级。
 *
 * 为什么不直接用预置 ARGB：动态取色会换色相，写死一组灰会让卡片和页面「不是一个色系」。
 */
fun tonal(base: Color, tone: Float): Color {
    val r = base.red
    val g = base.green
    val b = base.blue
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val l = (hi + lo) / 2f
    val d = hi - lo
    val s = when {
        d == 0f -> 0f
        l > 0.5f -> d / (2f - hi - lo)
        else -> d / (hi + lo)
    }
    val h = when {
        d == 0f -> 0f
        hi == r -> 60f * (((g - b) / d) % 6f)
        hi == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Color.hsl(h, s, (tone / 100f).coerceIn(0f, 1f))
}

/** 颜色的 CIELAB L*（0–100），单测里用它核对 tone 覆写的结果。 */
fun lightnessOf(color: Color): Float {
    val y = color.luminance()
    return (116f * y.pow(1f / 3f) - 16f).coerceIn(0f, 100f)
}

/**
 * 暗色主题的表面覆写：卡片 15 / 卡内块 21 / 槽 27，正文 `onSurface` 提到 97。
 *
 * 只改这几个角色，不动种子色和 primary，所以动态取色仍然生效；
 * 页面底仍是 `surface`（tone 6），[isDarkScheme] 靠它判断，不会被这里带偏。
 */
fun ColorScheme.withShituDarkTones(): ColorScheme {
    val surface = surface
    return copy(
        surfaceContainerLowest = surface,
        surfaceContainerLow = tonal(surface, 12f),
        surfaceContainer = tonal(surface, 15f),
        surfaceContainerHigh = tonal(surface, 21f),
        surfaceContainerHighest = tonal(surface, 27f),
        surfaceDim = surface,
        surfaceBright = tonal(surface, 24f),
        onSurface = tonal(surface, 97f),
    )
}
