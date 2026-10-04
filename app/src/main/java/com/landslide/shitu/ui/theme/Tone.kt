package com.landslide.shitu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 全 App 统一的状态语义：颜色 + 图标 + 文字三件套一起用。
 * 任何状态都不允许只靠一句灰字表达 —— 见 docs/superpowers/specs/2026-10-04-shitu-ui-优化-design.md
 */
enum class Tone { OK, BUSY, WARN, ERROR, IDLE }

// Material3 的 ColorScheme 没有 success 色，这里自己定一组；
// 不用主题色拼，免得 Material You 从壁纸取到红/黄时「正常」看着像「出错」。
private val OkLight = Color(0xFF1B7F4B)
private val OkDark = Color(0xFF6FD79B)

/** 当前配色是不是深色（用背景色亮度判断，跟随主题模式与动态取色）。 */
@Composable
@ReadOnlyComposable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** 状态主色：图标、文字、圆点、进度。 */
@Composable
@ReadOnlyComposable
fun Tone.color(): Color = when (this) {
    Tone.OK -> if (isDarkScheme()) OkDark else OkLight
    Tone.BUSY -> MaterialTheme.colorScheme.primary
    Tone.WARN -> MaterialTheme.colorScheme.tertiary
    Tone.ERROR -> MaterialTheme.colorScheme.error
    Tone.IDLE -> MaterialTheme.colorScheme.outline
}

/** 状态底色：卡片、胶囊背景（叠在页面底色上的一层淡色）。 */
@Composable
@ReadOnlyComposable
fun Tone.container(): Color = color().copy(alpha = if (isDarkScheme()) 0.20f else 0.10f)
