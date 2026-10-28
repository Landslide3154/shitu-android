package com.landslide.shitu.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * FlClash 的 iOS Dynamic Type 13 档（`lib/ui/typography.dart:6-19`）。
 *
 * 不直接用 Material 的 TextTheme 槽位表达设计意图：新组件一律取 [AppType]；
 * 旧代码仍可用 M3 槽位，但槽位已经被重映射到同一套字号（见 [AppTypography]）。
 */
enum class AppType(
    val size: Float,
    val leading: Float,
    val weight: FontWeight = FontWeight.Normal,
    val emphasizedWeight: FontWeight = FontWeight.SemiBold,
) {
    Display(40f, 44f, emphasizedWeight = FontWeight.Bold),
    LargeTitle(34f, 41f, emphasizedWeight = FontWeight.Bold),
    Title1(28f, 34f, emphasizedWeight = FontWeight.Bold),
    PageTitle(24f, 30f, weight = FontWeight.SemiBold, emphasizedWeight = FontWeight.Bold),
    Title2(22f, 28f, emphasizedWeight = FontWeight.Bold),
    Title3(20f, 25f),
    Headline(17f, 22f, weight = FontWeight.SemiBold),
    Body(17f, 22f),
    Callout(16f, 21f),
    Subheadline(15f, 20f),
    Footnote(13f, 18f),
    Caption1(12f, 16f),
    Caption2(11f, 13f),
    ;

    fun style(emphasized: Boolean = false): TextStyle = TextStyle(
        fontSize = size.sp,
        lineHeight = leading.sp,
        fontWeight = if (emphasized) emphasizedWeight else weight,
        letterSpacing = 0.sp,
    )
}

/** 统计数字/时间用等宽数字，避免行内数字跳动。 */
const val TabularNumbers = "tnum"

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TabularNumbers)

/**
 * 把 13 档映射到 M3 的 15 个槽位（对应 FlClash `_onAppScale`）。
 * 旧页面不改代码也能立刻获得 iOS 字号；新组件仍建议直接用 [AppType]。
 */
val AppTypography = Typography(
    displayLarge = AppType.LargeTitle.style(),
    displayMedium = AppType.LargeTitle.style(),
    displaySmall = AppType.LargeTitle.style(),
    headlineLarge = AppType.LargeTitle.style(),
    headlineMedium = AppType.Title1.style(),
    headlineSmall = AppType.Title2.style(),
    titleLarge = AppType.Title3.style(),
    titleMedium = AppType.Headline.style(),
    titleSmall = AppType.Subheadline.style(emphasized = true),
    bodyLarge = AppType.Body.style(),
    bodyMedium = AppType.Subheadline.style(),
    bodySmall = AppType.Footnote.style(),
    labelLarge = AppType.Subheadline.style(emphasized = true),
    labelMedium = AppType.Caption1.style(emphasized = true),
    labelSmall = AppType.Caption2.style(emphasized = true),
)

/**
 * FlClash 的文字缩放：系统缩放夹在 0.8–1.4，并且只跟随一半幅度（`metrics.dart:11-12`）。
 * 主题中心再乘一次用户选择的倍率，最终仍夹在 0.8–1.4。
 */
fun appFontScale(systemFontScale: Float, userScale: Float = 1f): Float {
    val system = systemFontScale.coerceIn(0.8f, 1.4f)
    val half = 1f + (system - 1f) * 0.5f
    return (half * userScale).coerceIn(0.8f, 1.4f)
}
