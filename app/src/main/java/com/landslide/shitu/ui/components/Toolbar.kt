package com.landslide.shitu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons

/**
 * 页面工具栏（FlClash `CommonScaffold`）：72 高、pageTitle 24/30 w600、右侧动作。
 * 页面标题不再自拼 Row，统一走这里。
 */
@Composable
fun ShituToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(AppMetrics.PageToolbarHeight)
            .padding(start = if (onBack != null) 4.dp else AppMetrics.PagePadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onBack != null) {
            ShituIconButton(
                icon = ShituIcons.ChevronLeft,
                contentDescription = "",
                onClick = onBack,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            title,
            style = AppType.PageTitle.style(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
