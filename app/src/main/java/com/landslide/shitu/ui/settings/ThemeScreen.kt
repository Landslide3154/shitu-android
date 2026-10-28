package com.landslide.shitu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landslide.shitu.R
import com.landslide.shitu.data.AppLocale
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.ui.components.CommonChip
import com.landslide.shitu.ui.components.CommonDialog
import com.landslide.shitu.ui.components.ListItem
import com.landslide.shitu.ui.components.ListItemAction
import com.landslide.shitu.ui.components.ListItemToggle
import com.landslide.shitu.ui.components.Section
import com.landslide.shitu.ui.components.SegmentedControl
import com.landslide.shitu.ui.components.ShituToolbar
import com.landslide.shitu.ui.design.AppColor
import com.landslide.shitu.ui.design.AppCorner
import com.landslide.shitu.ui.design.AppPalette
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ColorStyle
import com.landslide.shitu.ui.design.ShituIcons
import com.landslide.shitu.ui.design.SurfaceLevel
import com.landslide.shitu.ui.design.appFontScale
import com.landslide.shitu.ui.design.floatingColor
import com.landslide.shitu.ui.design.hairline
import com.landslide.shitu.ui.design.shiftTone
import com.landslide.shitu.ui.design.squircle
import com.landslide.shitu.ui.design.tabular
import com.landslide.shitu.ui.design.toneOf
import com.landslide.shitu.ui.theme.ThemeMode
import com.landslide.shitu.ui.theme.resolveDark
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 主题中心（FlClash `ThemeView`）：实时预览 → 模式 → 纯黑 → 主题色 → 风格 →
 * 导航样式 → 标签动画 → 文字缩放。
 */
@Composable
fun ThemeScreen(settings: Settings, store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showCustom by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    val style = ColorStyle.entries[settings.colorStyle.coerceIn(0, 3)]
    val navLabels = listOf(
        stringResource(R.string.set_nav_floating),
        stringResource(R.string.set_nav_docked),
    )
    val tabLabels = listOf(
        stringResource(R.string.set_tab_slide),
        stringResource(R.string.set_tab_fade),
    )
    val languageLabels = listOf(
        stringResource(R.string.set_theme_follow_system),
        stringResource(R.string.set_language_chinese),
        stringResource(R.string.set_language_english),
    )
    // 0 = 默认绿色；-1 = 跟随系统；其余是 ARGB
    val effectiveSeed = when (settings.seedColor) {
        0 -> AppPalette.Default.toArgbInt()
        -1 -> MaterialTheme.colorScheme.primary.toArgbInt()
        else -> settings.seedColor
    }

    Column(Modifier.fillMaxSize()) {
        ShituToolbar(stringResource(R.string.set_section_appearance_language), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ThemePreviewCard(settings) }
            item {
                Section(stringResource(R.string.set_theme_mode)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2).forEach { mode ->
                            ThemeModeChoice(
                                mode = mode,
                                settings = settings,
                                selected = settings.themeMode == mode,
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { store.setThemeMode(mode) } },
                            )
                        }
                    }
                }
            }
            item {
                Section {
                    ListItemToggle(
                        title = stringResource(R.string.set_theme_pure_black),
                        subtitle = stringResource(R.string.set_theme_pure_black_desc),
                        checked = settings.pureBlack,
                        onCheckedChange = { scope.launch { store.setPureBlack(it) } },
                        divider = false,
                    )
                }
            }
            item {
                Section(stringResource(R.string.set_primary_color)) {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            Modifier.width(64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ColorSwatch(
                                color = MaterialTheme.colorScheme.primary,
                                selected = settings.seedColor == -1,
                                icon = true,
                                onClick = {
                                    scope.launch {
                                        store.setSeedColor(-1)
                                        store.setDynamicColor(true)
                                    }
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.set_theme_follow_system),
                                style = AppType.Caption1.style(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        AppPalette.all.forEach { color ->
                            ColorSwatch(
                                color = color,
                                selected = settings.seedColor != -1 &&
                                    effectiveSeed == color.toArgbInt(),
                                onClick = {
                                    scope.launch {
                                        store.setSeedColor(color.toArgbInt())
                                        store.setDynamicColor(false)
                                    }
                                },
                            )
                        }
                        Column(
                            Modifier.width(64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ColorSwatch(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                selected = false,
                                custom = true,
                                onClick = { showCustom = true },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.set_theme_custom),
                                style = AppType.Caption1.style(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.set_color_style)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ColorStyle.entries.forEach { entry ->
                            CommonChip(
                                text = colorStyleLabel(entry),
                                selected = entry == style,
                                modifier = Modifier.weight(1f),
                                center = true,
                                onClick = { scope.launch { store.setColorStyle(entry.ordinal) } },
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.set_nav_style)) {
                    Column(Modifier.padding(12.dp)) {
                        SegmentedControl(
                            options = listOf(0, 1),
                            selected = settings.navStyle,
                            onSelect = { scope.launch { store.setNavStyle(it) } },
                            label = { navLabels[it] },
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.set_tab_animation)) {
                    Column(Modifier.padding(12.dp)) {
                        SegmentedControl(
                            options = listOf(0, 1),
                            selected = settings.tabAnimation,
                            onSelect = { scope.launch { store.setTabAnimation(it) } },
                            label = { tabLabels[it] },
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.set_text_scale)) {
                    TextScaleRow(settings, store)
                }
            }
            item {
                Section(stringResource(R.string.set_language)) {
                    Column(Modifier.padding(12.dp)) {
                        SegmentedControl(
                            options = listOf(AppLocale.SYSTEM, AppLocale.CHINESE, AppLocale.ENGLISH),
                            selected = settings.language,
                            onSelect = { value ->
                                if (settings.language != value) {
                                    scope.launch {
                                        store.setLanguage(value)
                                        AppLocale.findActivity(context)?.recreate()
                                    }
                                }
                            },
                            label = { languageLabels[it] },
                        )
                    }
                }
            }
            item {
                Section {
                    ListItemAction(
                        title = stringResource(R.string.set_theme_reset),
                        destructive = true,
                        onClick = { showReset = true },
                        divider = false,
                    )
                }
            }
        }
    }

    if (showCustom) {
        ColorPickerDialog(
            initial = Color(effectiveSeed),
            onDismiss = { showCustom = false },
            onConfirm = { color ->
                scope.launch {
                    store.setSeedColor(color.toArgbInt())
                    store.setDynamicColor(false)
                }
                showCustom = false
            },
        )
    }
    if (showReset) {
        CommonDialog(
            title = stringResource(R.string.set_reset_confirm_title),
            body = stringResource(R.string.set_reset_confirm_body),
            confirmLabel = stringResource(R.string.set_theme_reset),
            dismissLabel = stringResource(R.string.set_cancel),
            destructive = true,
            onDismiss = { showReset = false },
            onConfirm = {
                showReset = false
                scope.launch {
                    store.setThemeMode(ThemeMode.SYSTEM)
                    store.setSeedColor(0)
                    store.setDynamicColor(false)
                    store.setColorStyle(0)
                    store.setPureBlack(false)
                    store.setNavStyle(0)
                    store.setTabAnimation(0)
                    store.setTextScale(1f)
                }
            },
        )
    }
}

@Composable
private fun colorStyleLabel(entry: ColorStyle): String = stringResource(
    when (entry) {
        ColorStyle.VIVID -> R.string.set_style_vivid
        ColorStyle.TINTED -> R.string.set_style_tinted
        ColorStyle.SOFT -> R.string.set_style_soft
        ColorStyle.MONOCHROME -> R.string.set_style_mono
    },
)

private val ThemeMode.value: Int
    get() = 0

@Composable
private fun ThemePreviewCard(settings: Settings) {
    val dark = resolveDark(settings.themeMode, isSystemInDarkTheme())
    val seed = when (settings.seedColor) {
        -1 -> MaterialTheme.colorScheme.primary
        0 -> AppPalette.Default
        else -> Color(settings.seedColor)
    }
    val style = ColorStyle.entries[settings.colorStyle.coerceIn(0, 3)]
    val scheme = remember(seed, dark, style, settings.pureBlack) {
        AppColor.scheme(seed, dark, style, settings.pureBlack)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.set_theme_preview),
            style = AppType.Headline.style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(squircle(AppCorner.lg))
                .background(SurfaceLevel.PAGE.colorOf(scheme))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("拾图 Shitu", style = AppType.Headline.style(), color = scheme.onSurface)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(squircle(AppCorner.md))
                        .background(SurfaceLevel.CARD.colorOf(scheme))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .width(140.dp)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurface.copy(alpha = 0.85f)),
                        )
                        Box(
                            Modifier
                                .width(220.dp)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.55f)),
                        )
                        Box(
                            Modifier
                                .width(120.dp)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeChoice(
    mode: Int,
    settings: Settings,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = resolveDark(mode, isSystemInDarkTheme())
    val seed = when (settings.seedColor) {
        -1 -> MaterialTheme.colorScheme.primary
        0 -> AppPalette.Default
        else -> Color(settings.seedColor)
    }
    val style = ColorStyle.entries[settings.colorStyle.coerceIn(0, 3)]
    val preview = remember(seed, dark, style, settings.pureBlack) {
        AppColor.scheme(seed, dark, style, settings.pureBlack)
    }
    Column(
        modifier
            .clip(squircle(AppCorner.md))
            .background(if (selected) SurfaceLevel.BLOCK.colorOf(scheme) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.hairline,
                shape = squircle(AppCorner.md),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(squircle(AppCorner.sm))
                .background(SurfaceLevel.PAGE.colorOf(preview)),
        ) {
            Box(
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(squircle(AppCorner.xs))
                    .background(SurfaceLevel.CARD.colorOf(preview)),
            )
            Box(
                Modifier
                    .padding(start = 8.dp, top = 36.dp)
                    .width(24.dp)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(preview.primary),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (mode) {
                1 -> stringResource(R.string.set_theme_light)
                2 -> stringResource(R.string.set_theme_dark)
                else -> stringResource(R.string.set_theme_system)
            },
            style = AppType.Caption1.style(emphasized = selected),
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    icon: Boolean = false,
    custom: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (icon || custom) SurfaceLevel.INSET.colorOf(scheme) else color)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.hairline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon -> Icon(ShituIcons.Monitor, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            custom -> Icon(ShituIcons.Plus, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            selected -> Icon(ShituIcons.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TextScaleRow(settings: Settings, store: SettingsStore) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentScale = LocalDensity.current.fontScale
    val systemScale = context.resources.configuration.fontScale
    var value by remember(settings.textScale) { mutableStateOf(settings.textScale) }
    // 主题中心整体已经按 settings.textScale 缩过一次；这里按滑块实时算出「最终会多大」，
    // 再除掉当前密度倍率，保证示例文字预览和右上角数字一致。
    val previewScale = appFontScale(systemScale, value)
    val previewSp = (17 * previewScale).roundToInt()
    val sampleScale = if (currentScale > 0f) previewScale / currentScale else 1f
    Column(Modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${(value * 100).toInt()}%",
                style = AppType.Subheadline.style(emphasized = true),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.set_text_scale_value, previewSp),
                style = AppType.Caption1.style().tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { scope.launch { store.setTextScale(value) } },
            valueRange = 0.8f..1.4f,
            steps = 5,
        )
        Text(
            stringResource(R.string.set_text_scale_sample),
            style = AppType.Body.style().copy(
                fontSize = (17 * sampleScale).sp,
                lineHeight = (22 * sampleScale).sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgbInt(), it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    val color = Color.hsv(hue, saturation, value)
    // 预览条和滑轨用原始颜色；文字/确认按钮用同色相的可读版本，
    // 否则拖到纯白或纯黑时数字会消失在对话框底色里。
    val accent = readableAccent(color, floatingColor(MaterialTheme.colorScheme))
    CommonDialog(
        title = stringResource(R.string.set_custom_color_title),
        confirmLabel = stringResource(R.string.set_confirm),
        dismissLabel = stringResource(R.string.set_cancel),
        confirmColor = accent,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(color) },
        content = {
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(squircle(AppCorner.md))
                        .background(color),
                )
                ColorSlider(
                    label = stringResource(R.string.set_hue),
                    valueLabel = "${hue.roundToInt()}°",
                    value = hue,
                    valueRange = 0f..360f,
                    accent = color,
                    valueColor = accent,
                    onValueChange = { hue = it },
                )
                ColorSlider(
                    label = stringResource(R.string.set_saturation),
                    valueLabel = "${(saturation * 100).roundToInt()}%",
                    value = saturation,
                    valueRange = 0f..1f,
                    accent = color,
                    valueColor = accent,
                    onValueChange = { saturation = it },
                )
                ColorSlider(
                    label = stringResource(R.string.set_brightness),
                    valueLabel = "${(value * 100).roundToInt()}%",
                    value = value,
                    valueRange = 0f..1f,
                    accent = color,
                    valueColor = accent,
                    onValueChange = { value = it },
                )
            }
        },
    )
}

@Composable
private fun ColorSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    valueColor: Color,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = AppType.Footnote.style(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                valueLabel,
                style = AppType.Footnote.style(emphasized = true).tabular(),
                color = valueColor,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.22f),
                activeTickColor = Color.White.copy(alpha = 0.9f),
                inactiveTickColor = accent.copy(alpha = 0.45f),
            ),
        )
    }
}

/**
 * 把当前自定义色压到当前主题可读的色调：亮色主题 ≤40，暗色主题 ≥80。
 * 只改明度、保留色相与饱和度，所以拖动色相/饱和度时文字仍然跟着变，
 * 拖到纯白/纯黑也不会和对话框底色糊在一起。
 */
private fun readableAccent(accent: Color, background: Color): Color {
    val dark = toneOf(background) < 50.0
    val tone = toneOf(accent)
    val target = if (dark) tone.coerceAtLeast(80.0) else tone.coerceAtMost(40.0)
    return if (abs(target - tone) < 0.5) accent else shiftTone(accent, target - tone)
}

private fun Color.toArgbInt(): Int = toArgb()
