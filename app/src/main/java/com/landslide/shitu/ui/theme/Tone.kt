package com.landslide.shitu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.landslide.shitu.ui.design.isDark
import com.landslide.shitu.ui.design.success
import com.landslide.shitu.ui.design.successContainer
import com.landslide.shitu.ui.design.warning
import com.landslide.shitu.ui.design.warningContainer

/**
 * 全 App 统一的状态语义：颜色 + 图标 + 文字三件套一起用。
 * 状态色只上文字/图标/小胶囊，不铺整张卡（FlClash 原则）。
 */
enum class Tone { OK, BUSY, WARN, ERROR, IDLE, COPY }

// 复制色固定：和移动绿（OK）一眼区分，也不受动态取色影响。
private val CopyLight = Color(0xFF1F5FBF)
private val CopyDark = Color(0xFF9DC0FF)

/** 当前配色是不是深色（跟随主题模式与动态取色）。 */
@Composable
@ReadOnlyComposable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.isDark

/** 状态主色：图标、文字、圆点、进度。 */
@Composable
@ReadOnlyComposable
fun Tone.color(): Color = when (this) {
    Tone.OK -> MaterialTheme.colorScheme.success
    Tone.BUSY -> MaterialTheme.colorScheme.primary
    Tone.WARN -> MaterialTheme.colorScheme.warning
    Tone.ERROR -> MaterialTheme.colorScheme.error
    Tone.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    Tone.COPY -> if (isDarkScheme()) CopyDark else CopyLight
}

/** 状态底色：胶囊/提示卡的底。整卡大面积仍用淡色，避免列表变红绿灯。 */
@Composable
@ReadOnlyComposable
fun Tone.container(): Color = color().copy(alpha = if (isDarkScheme()) 0.20f else 0.10f)
