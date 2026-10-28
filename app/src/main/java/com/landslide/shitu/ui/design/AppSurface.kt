package com.landslide.shitu.ui.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 四级表面（FlClash `lib/ui/surface.dart`）：结构靠填充深浅表达，不靠描边。
 * 子级永远比父级深/浅一档；卡片内部自动取下一层，调用点不指定颜色。
 */
enum class SurfaceLevel {
    PAGE,
    CARD,
    BLOCK,
    INSET,
    ;

    fun next(): SurfaceLevel = when (this) {
        PAGE -> CARD
        CARD -> BLOCK
        BLOCK, INSET -> INSET
    }

    fun colorOf(colorScheme: ColorScheme): Color {
        val dark = colorScheme.isDark
        return when (this) {
            PAGE -> if (dark) colorScheme.surface else colorScheme.surfaceContainer
            CARD -> if (dark) colorScheme.surfaceContainer else colorScheme.surfaceBright
            BLOCK -> if (dark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainer
            INSET -> colorScheme.surfaceContainerHighest
        }
    }
}

val LocalSurfaceLevel = compositionLocalOf { SurfaceLevel.PAGE }
val LocalReduceMotion = compositionLocalOf { false }
val LocalAppTextScale = compositionLocalOf { 1f }

/** 浮层（对话框/菜单/弹层/底栏）：亮 card、暗 block。 */
fun floatingColor(colorScheme: ColorScheme): Color =
    if (colorScheme.isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceBright

/** 窗口 chrome（侧栏/标题栏）：比页面深/浅一档。 */
fun chromeColor(colorScheme: ColorScheme): Color =
    if (colorScheme.isDark) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHighest

/**
 * 通用表面：默认取当前层级的下一个层级。
 *
 * 卡片默认无边框、无阴影；只有浮层需要阴影时由调用方加 `Modifier.shadow`。
 */
@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    level: SurfaceLevel = LocalSurfaceLevel.current.next(),
    shape: Shape = squircle(AppCorner.md),
    color: Color = level.colorOf(MaterialTheme.colorScheme),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSurfaceLevel provides level) {
        if (onClick != null) {
            Surface(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                color = color,
                enabled = enabled,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.padding(contentPadding)) { content() }
            }
        } else {
            Surface(modifier = modifier, shape = shape, color = color) {
                androidx.compose.foundation.layout.Box(Modifier.padding(contentPadding)) { content() }
            }
        }
    }
}

/** 页面根：铺满并声明当前层级为 PAGE。 */
@Composable
fun AppPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSurfaceLevel provides SurfaceLevel.PAGE) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = SurfaceLevel.PAGE.colorOf(MaterialTheme.colorScheme),
        ) { content() }
    }
}
