package com.landslide.shitu.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.SurfaceLevel
import com.landslide.shitu.ui.design.chromeColor
import com.landslide.shitu.ui.design.hairline

/**
 * 桌面/平板侧栏（FlClash `AppSidebar`）：展开 220 / 折叠 48，项高 40，图标 24。
 * 选中态 = 中性填充 + 左侧 3dp 主色条，不铺主色。
 */
@Composable
fun ShituSidebar(
    selected: Int,
    onSelect: (Int) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(if (expanded) AppMetrics.SidebarExpanded else AppMetrics.SidebarCollapsed)
                .fillMaxHeight()
                .background(chromeColor(scheme))
                .padding(vertical = 8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(AppMetrics.SidebarItemHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    com.landslide.shitu.ui.design.ShituIcons.Image,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                if (expanded) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "拾图",
                        style = AppType.Headline.style(emphasized = true),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ShituDestination.entries.forEachIndexed { index, destination ->
                SidebarItem(
                    destination = destination,
                    label = destinationLabel(destination),
                    selected = selected == index,
                    expanded = expanded,
                    onClick = { onSelect(index) },
                )
                Spacer(Modifier.height(AppMetrics.SidebarItemGap))
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(scheme.hairline),
        )
    }
}

@Composable
private fun SidebarItem(
    destination: ShituDestination,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(AppMetrics.SidebarItemHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceLevel.BLOCK.colorOf(scheme) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; role = Role.Tab }
            .padding(horizontal = if (expanded) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.primary),
            )
            Spacer(Modifier.width(if (expanded) 7.dp else 5.dp))
        } else if (expanded) {
            Spacer(Modifier.width(10.dp))
        }
        Icon(
            destination.icon,
            contentDescription = null,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(AppMetrics.SidebarIcon),
        )
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = AppType.Body.style(emphasized = selected),
                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
