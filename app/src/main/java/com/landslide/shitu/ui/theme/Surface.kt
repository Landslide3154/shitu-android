package com.landslide.shitu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.landslide.shitu.ui.design.SurfaceLevel

/**
 * 兼容层：旧页面调用 `ShituSurface.page()/card()` 仍可用。
 *
 * 新组件应改用 [com.landslide.shitu.ui.design.AppSurface] + `LocalSurfaceLevel`，
 * 让卡片内部自动取下一层；这两个函数只做「按当前主题算颜色」，不做作用域推进。
 */
object ShituSurface {
    @Composable
    @ReadOnlyComposable
    fun page(): Color = SurfaceLevel.PAGE.colorOf(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun card(): Color = SurfaceLevel.CARD.colorOf(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun block(): Color = SurfaceLevel.BLOCK.colorOf(MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun inset(): Color = SurfaceLevel.INSET.colorOf(MaterialTheme.colorScheme)
}
