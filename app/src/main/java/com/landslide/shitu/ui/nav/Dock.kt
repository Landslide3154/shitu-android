package com.landslide.shitu.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppMotion
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons
import com.landslide.shitu.ui.design.SurfaceLevel
import com.landslide.shitu.ui.design.floatingColor

@Composable
fun destinationLabel(destination: ShituDestination): String = androidx.compose.ui.res.stringResource(
    when (destination) {
        ShituDestination.RULES -> com.landslide.shitu.R.string.app_nav_rules
        ShituDestination.LOGS -> com.landslide.shitu.R.string.app_nav_logs
        ShituDestination.SETTINGS -> com.landslide.shitu.R.string.app_nav_settings
    },
)

/** 三个顶层目的地；侧栏与底栏共用同一份数据源。 */
enum class ShituDestination(
    val labelZh: String,
    val labelEn: String,
    val icon: ImageVector,
) {
    RULES("规则", "Rules", ShituIcons.Rules),
    LOGS("日志", "Logs", ShituIcons.Logs),
    SETTINGS("设置", "Settings", ShituIcons.Settings),
}

/**
 * 浮起 Dock（FlClash `NavigationDock` 的简化版）：高 62、内衬 4、图标 28、标签 10。
 * 选中项是比 Dock 高一层的圆角板 + 主色图标；指示板用 spring 跟随。
 */
@Composable
fun ShituDock(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val count = ShituDestination.entries.size
    val interactions = remember { List(count) { MutableInteractionSource() } }
    val itemWidth = 72.dp
    Surface(
        modifier = modifier
            .width(itemWidth * count + AppMetrics.DockInset * 2)
            .height(AppMetrics.DockHeight),
        shape = CircleShape,
        color = floatingColor(scheme),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Box {
            val offset by animateDpAsState(
                targetValue = AppMetrics.DockInset + itemWidth * selected,
                animationSpec = AppMotion.travelSpring(),
                label = "dockIndicator",
            )
            Box(
                Modifier
                    .padding(AppMetrics.DockInset)
                    .offset(x = offset - AppMetrics.DockInset)
                    .width(itemWidth)
                    .height(AppMetrics.DockHeight - AppMetrics.DockInset * 2)
                    .clip(CircleShape)
                    .background(SurfaceLevel.BLOCK.colorOf(scheme)),
            )
            Row(Modifier.fillMaxWidth().height(AppMetrics.DockHeight)) {
                ShituDestination.entries.forEachIndexed { index, destination ->
                    val active = selected == index
                    val tint by animateColorAsState(
                        targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
                        animationSpec = tween(AppMotion.MidMs),
                        label = "dockTint",
                    )
                    Column(
                        Modifier
                            .width(itemWidth)
                            .height(AppMetrics.DockHeight)
                            .clickable(
                                interactionSource = interactions[index],
                                indication = null,
                                onClick = { onSelect(index) },
                            )
                            .semantics { this.selected = active; role = Role.Tab },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(AppMetrics.DockIcon),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            destinationLabel(destination),
                            style = AppType.Caption2.style(emphasized = active).copy(
                                fontSize = AppMetrics.DockLabelSize.value.sp,
                                lineHeight = 14.5.sp,
                            ),
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 停靠底栏（用户选「停靠」时）：M3 NavigationBar + 中性指示板 + 主色图标。 */
@Composable
fun ShituDockedBar(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = floatingColor(scheme)) {
        ShituDestination.entries.forEachIndexed { index, destination ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        destination.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(destinationLabel(destination), style = AppType.Caption2.style()) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = SurfaceLevel.BLOCK.colorOf(scheme),
                    selectedIconColor = scheme.primary,
                    selectedTextColor = scheme.primary,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Suppress("unused")
private val unused = Unit
