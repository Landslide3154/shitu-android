package com.landslide.shitu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.landslide.shitu.ui.design.AppCorner
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppSurface
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons
import com.landslide.shitu.ui.design.SurfaceLevel
import com.landslide.shitu.ui.design.hairline
import com.landslide.shitu.ui.design.destructive
import com.landslide.shitu.ui.design.squircle

/**
 * 设置/列表的分组容器（FlClash `Section`）：组头 + 一张通栏卡 + 组内行。
 * 卡圆角 24、无边框、无阴影；行与行之间只有 14% 发丝线。
 */
@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionHeader(title)
        AppSurface(
            level = SurfaceLevel.CARD,
            shape = squircle(AppCorner.xl),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** 组标题：上 24 / 下 8 / 左右 16。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppType.Headline.style(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = AppMetrics.PagePadding,
            end = AppMetrics.PagePadding,
            top = AppMetrics.SectionHeaderTop,
            bottom = AppMetrics.SectionHeaderBottom,
        ),
    )
}

/**
 * 七种行类型的公共骨架（FlClash `ListItem`）：最小高 56、左 16 右 8、纵 10。
 *
 * 页面里不要直接拼 Row；用下面的具名构造函数，保证行高/内衬/发丝线一致。
 */
@Composable
fun ListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    titleColor: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = AppMetrics.ListRowMinHeight)
                .padding(
                    start = AppMetrics.ListRowStartPad,
                    end = AppMetrics.ListRowEndPad,
                    top = AppMetrics.ListRowVPad,
                    bottom = AppMetrics.ListRowVPad,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(Modifier.size(AppMetrics.IconSize), contentAlignment = Alignment.Center) {
                    leading()
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = AppType.Body.style(),
                    color = titleColor ?: if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = AppType.Footnote.style(),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (leading != null) 52.dp else AppMetrics.ListRowStartPad),
                thickness = 1.dp,
                color = scheme.hairline,
            )
        }
    }
}

/** 跳转子页：尾部 chevron。 */
@Composable
fun ListItemNext(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) = ListItem(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    leading = leading,
    onClick = onClick,
    enabled = enabled,
    divider = divider,
    trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    value,
                    style = AppType.Subheadline.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                ShituIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    },
)

/** 开关行：尾部开关。 */
@Composable
fun ListItemToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) = ListItem(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    leading = leading,
    enabled = enabled,
    divider = divider,
    trailing = {
        ShituSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    },
)

/** 单选/菜单行：尾部当前值 + 菜单图标。 */
@Composable
fun ListItemOptions(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
) = ListItem(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    leading = leading,
    onClick = onClick,
    divider = divider,
    trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = AppType.Subheadline.style(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                ShituIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    },
)

/** 输入行：尾部当前值，点一下弹编辑框。 */
@Composable
fun ListItemInput(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
) = ListItem(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    onClick = onClick,
    divider = divider,
    trailing = {
        Text(
            value,
            style = AppType.Subheadline.style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
)

/** 复选行：尾部选中标记。 */
@Composable
fun ListItemCheckbox(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
) = ListItem(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    onClick = { onCheckedChange(!checked) },
    divider = divider,
    trailing = { SelectionMark(checked) },
)

/** 纯动作行：标题居中偏左，尾部可给图标。 */
@Composable
fun ListItemAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
) {
    val color = if (destructive) {
        MaterialTheme.colorScheme.destructive
    } else {
        MaterialTheme.colorScheme.primary
    }
    ListItem(
        title = title,
        modifier = modifier,
        leading = leading,
        onClick = onClick,
        divider = divider,
        titleColor = color,
    )
}

/** 行尾/多选用的圆形勾选标记（FlClash `SelectionMark`）。 */
@Composable
fun SelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (selected) scheme.primary else scheme.outline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                ShituIcons.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}
