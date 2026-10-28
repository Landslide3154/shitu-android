package com.landslide.shitu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 全 App 表面层级的唯一来源（FlClash 迁移 P0）。
 *
 * 以前卡片直接写 `surfaceContainerLow`：亮色下它比页面还深，看起来像「凹进去」；
 * 这里统一成「页面 → 卡片 → 卡内块 → 槽」四级，亮色是「灰页 + 近白卡」，暗色是「暗页 + 亮一档的卡」。
 *
 * | 层级 | 亮色 | 暗色 | 用途 |
 * | --- | --- | --- | --- |
 * | [page] | `surfaceContainer` (94) | `surface` (6) | 页面底、Scaffold 容器 |
 * | [card] | `surfaceBright` (98) | `surfaceContainer` (15) | 规则卡 / 设置卡 / 日志卡 |
 * | [block] | `surfaceContainer` (94) | `surfaceContainerHigh` (21) | 卡片里的分组块、对话框内分区 |
 * | [inset] | `surfaceContainerHighest` (90) | `surfaceContainerHighest` (27) | 进度条轨道、缩略图槽、选中底 |
 *
 * 不要在页面里再裸写 `surfaceContainer*`；改层级只改这一个文件。
 * 暗色的 15/21/27 与 `onSurface` 97 在 [shituDarkTones] 里覆写（见 Theme.kt）。
 */
object ShituSurface {

    /** 页面底：Scaffold / 整页 Surface 用。 */
    @Composable
    @ReadOnlyComposable
    fun page(): Color = if (isDarkScheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    /** 卡片：比页面「抬起来」一层。 */
    @Composable
    @ReadOnlyComposable
    fun card(): Color = if (isDarkScheme()) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

    /** 卡内分组块：卡片里的第二层容器。 */
    @Composable
    @ReadOnlyComposable
    fun block(): Color = if (isDarkScheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    /** 槽 / 轨道：进度条底、缩略图底、展开项的选中底。 */
    @Composable
    @ReadOnlyComposable
    fun inset(): Color = MaterialTheme.colorScheme.surfaceContainerHighest
}
