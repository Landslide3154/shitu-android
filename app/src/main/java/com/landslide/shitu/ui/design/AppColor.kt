package com.landslide.shitu.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.TonalPalette
import kotlin.math.abs
import kotlin.math.min

/** FlClash 的四种配色风格（`lib/ui/color.dart:17-28`）。 */
enum class ColorStyle(val labelZh: String, val labelEn: String) {
    VIVID("鲜明", "Vivid"),
    TINTED("着色", "Tinted"),
    SOFT("柔和", "Soft"),
    MONOCHROME("单色", "Monochrome"),
}

/** 预设色板（FlClash `lib/common/constant.dart:177-188`）。 */
object AppPalette {
    val Blue = Color(0xFF007AFF)
    val Magenta = Color(0xFFCB30E0)
    val Pink = Color(0xFFFF2D55)
    val Red = Color(0xFFFF383C)
    val Orange = Color(0xFFFF8D28)
    val Yellow = Color(0xFFFFCC00)
    val Green = Color(0xFF34C759)
    val Gray = Color(0xFF8E8E93)

    /** 默认主题色：FlClash 的绿色。 */
    val Default = Green

    val all = listOf(Blue, Magenta, Pink, Red, Orange, Yellow, Green, Gray)
}

/**
 * 全部颜色角色的唯一生成入口，按 FlClash `seededColorScheme` 1:1 移植。
 *
 * 关键不是「用 Material You」，而是：种子 → HCT → 风格 Variant → 只覆写表面与主色，
 * 主色 tone 夹在亮 35–60 / 暗 50–75，暗色表面写死 15/21/27、onSurface 97。
 */
object AppColor {

    private val dynamic = MaterialDynamicColors()

    private val successPalette = TonalPalette.fromHct(Hct.fromInt(0xFF4CAF50.toInt()))
    private val warningPalette = TonalPalette.fromHct(Hct.fromInt(0xFFFF9800.toInt()))

    fun scheme(
        seed: Color,
        dark: Boolean,
        style: ColorStyle = ColorStyle.VIVID,
        pureBlack: Boolean = false,
    ): ColorScheme = cache.getOrPut(Key(seed.toArgb(), dark, style, pureBlack)) {
        build(seed, dark, style).toPureBlack(pureBlack)
    }

    private fun build(seed: Color, dark: Boolean, style: ColorStyle): ColorScheme {
        val source = Hct.fromInt(seed.toArgb())
        val scheme = when (style) {
            ColorStyle.VIVID, ColorStyle.TINTED ->
                SchemeContent(source, dark, 0.0)
            ColorStyle.SOFT ->
                SchemeTonalSpot(source, dark, 0.0)
            ColorStyle.MONOCHROME ->
                SchemeMonochrome(source, dark, 0.0)
        }
        val primaryPalette = scheme.primaryPalette
        val containerPalette = TonalPalette.fromHueAndChroma(
            primaryPalette.hue,
            min(primaryPalette.chroma, PrimaryContainerChroma),
        )
        val tint = if (style == ColorStyle.TINTED) {
            min(source.chroma / TintDivisor, MaxTintChroma)
        } else {
            null
        }

        fun neutral(role: DynamicColor, maxChroma: Double, darkTone: Double? = null): Color {
            val palette = role.palette.apply(scheme)
            val tone = if (dark) darkTone ?: role.getTone(scheme) else role.getTone(scheme)
            val chroma = tint ?: min(palette.chroma, maxChroma)
            return Color(Hct.from(palette.hue, chroma, tone).toInt())
        }

        fun role(color: DynamicColor): Color = Color(color.getArgb(scheme))

        val accent = when (style) {
            ColorStyle.VIVID, ColorStyle.TINTED -> accent(seed, source, dark)
            else -> null
        }
        val primary = accent?.first ?: role(dynamic.primary())
        val onPrimary = accent?.second ?: role(dynamic.onPrimary())
        val paperChroma = if (dark) SurfaceChroma else PaperChroma

        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = Color(containerPalette.tone(if (dark) 30 else 90)),
            onPrimaryContainer = Color(containerPalette.tone(if (dark) 90 else 30)),
            inversePrimary = role(dynamic.inversePrimary()),
            secondary = role(dynamic.secondary()),
            onSecondary = role(dynamic.onSecondary()),
            secondaryContainer = role(dynamic.secondaryContainer()),
            onSecondaryContainer = role(dynamic.onSecondaryContainer()),
            tertiary = role(dynamic.tertiary()),
            onTertiary = role(dynamic.onTertiary()),
            tertiaryContainer = role(dynamic.tertiaryContainer()),
            onTertiaryContainer = role(dynamic.onTertiaryContainer()),
            error = role(dynamic.error()),
            onError = role(dynamic.onError()),
            errorContainer = role(dynamic.errorContainer()),
            onErrorContainer = role(dynamic.onErrorContainer()),
            outline = role(dynamic.outline()),
            outlineVariant = neutral(dynamic.outlineVariant(), 4.0),
            scrim = role(dynamic.scrim()),
            surfaceTint = primary,
            inverseSurface = role(dynamic.inverseSurface()),
            inverseOnSurface = role(dynamic.inverseOnSurface()),
            surface = neutral(dynamic.surface(), paperChroma),
            surfaceBright = neutral(dynamic.surfaceBright(), paperChroma),
            surfaceDim = neutral(dynamic.surfaceDim(), SurfaceChroma),
            surfaceContainerLowest = neutral(dynamic.surfaceContainerLowest(), SurfaceChroma),
            surfaceContainerLow = neutral(dynamic.surfaceContainerLow(), SurfaceChroma),
            surfaceContainer = neutral(
                dynamic.surfaceContainer(),
                SurfaceChroma,
                if (dark) DarkCardTone else null,
            ),
            surfaceContainerHigh = neutral(
                dynamic.surfaceContainerHigh(),
                SurfaceChroma,
                if (dark) DarkBlockTone else null,
            ),
            surfaceContainerHighest = neutral(
                dynamic.surfaceContainerHighest(),
                SurfaceChroma,
                if (dark) DarkInsetTone else null,
            ),
            onSurface = if (dark) {
                Color(
                    dynamic.onSurface().palette.apply(scheme).tone(DarkOnSurfaceTone.toInt()),
                )
            } else {
                role(dynamic.onSurface())
            },
            onSurfaceVariant = role(dynamic.onSurfaceVariant()),
        )
    }

    private fun accent(seed: Color, source: Hct, dark: Boolean): Pair<Color, Color> {
        val tone = if (dark) {
            (source.tone + DarkAccentLift).coerceIn(MinDarkAccentTone, MaxDarkAccentTone)
        } else {
            source.tone.coerceIn(MinAccentTone, MaxAccentTone)
        }
        val primary = if (abs(tone - source.tone) < 0.001) {
            seed
        } else {
            Color(Hct.from(source.hue, source.chroma, tone).toInt())
        }
        val onPrimary = if (tone <= MaxAccentTone) {
            Color.White
        } else {
            Color(TonalPalette.fromHueAndChroma(source.hue, source.chroma).tone(10))
        }
        return primary to onPrimary
    }

    /** 纯黑：page 归零，其余表面整体下移 page 的 tone（FlClash `toPureBlack`）。 */
    private fun ColorScheme.toPureBlack(enabled: Boolean): ColorScheme {
        if (!enabled || surface.luminance() >= 0.5f) return this
        val shift = toneOf(surface)
        fun lower(color: Color) = shiftTone(color, -shift)
        return copy(
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = lower(surfaceContainerLow),
            surfaceContainer = lower(surfaceContainer),
            surfaceContainerHigh = lower(surfaceContainerHigh),
            surfaceContainerHighest = lower(surfaceContainerHighest),
            surfaceBright = lower(surfaceBright),
        )
    }

    private val cache = object : LinkedHashMap<Key, ColorScheme>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ColorScheme>?): Boolean =
            size > 64
    }

    private data class Key(
        val seed: Int,
        val dark: Boolean,
        val style: ColorStyle,
        val pureBlack: Boolean,
    )
}

/** 固定状态色：success / warning 不随种子漂移（FlClash `color.dart:235-248`）。 */
val ColorScheme.success: Color
    get() = statusTone(success = true, tone = if (isDark) 80 else 40)

val ColorScheme.successContainer: Color
    get() = statusTone(success = true, tone = if (isDark) 30 else 90)

val ColorScheme.warning: Color
    get() = statusTone(success = false, tone = if (isDark) 80 else 40)

val ColorScheme.warningContainer: Color
    get() = statusTone(success = false, tone = if (isDark) 30 else 90)

/** iOS 系统红：告警里的破坏性动作，与 error 分开。 */
val ColorScheme.destructive: Color
    get() = Color(if (isDark) 0xFFFF4245.toInt() else 0xFFFF383C.toInt())

val ColorScheme.modalScrim: Color get() = scrim.copy(alpha = 0.32f)
val ColorScheme.alertScrim: Color get() = scrim.copy(alpha = if (isDark) 0.48f else 0.20f)

/** 结构分隔线：onSurface 14%，不用 outlineVariant。 */
val ColorScheme.hairline: Color get() = onSurface.copy(alpha = 0.14f)

val ColorScheme.isDark: Boolean get() = surface.luminance() < 0.5f

private fun ColorScheme.statusTone(success: Boolean, tone: Int): Color {
    val palette = TonalPalette.fromHct(Hct.fromInt(if (success) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt()))
    return Color(palette.tone(tone))
}

fun toneOf(color: Color): Double = Hct.fromInt(color.toArgb()).tone

fun shiftTone(color: Color, delta: Double): Color {
    val hct = Hct.fromInt(color.toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, (hct.tone + delta).coerceIn(0.0, 100.0)).toInt())
}

// FlClash `lib/ui/color.dart:144-170`
private const val SurfaceChroma = 3.0
private const val PaperChroma = 1.5
private const val TintDivisor = 6.0
private const val MaxTintChroma = 10.0
private const val PrimaryContainerChroma = 48.0
private const val DarkOnSurfaceTone = 97.0
private const val DarkCardTone = 15.0
private const val DarkBlockTone = 21.0
private const val DarkInsetTone = 27.0
private const val MinAccentTone = 35.0
private const val MaxAccentTone = 60.0
private const val DarkAccentLift = 3.0
private const val MinDarkAccentTone = 50.0
private const val MaxDarkAccentTone = 75.0
