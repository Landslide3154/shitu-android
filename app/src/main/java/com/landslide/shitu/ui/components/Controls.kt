package com.landslide.shitu.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppMotion
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.SurfaceLevel
import com.landslide.shitu.ui.design.destructive
import com.landslide.shitu.ui.design.squircle
import com.landslide.shitu.ui.design.AppCorner

/**
 * 分段控件（FlClash `SegmentedControl`）：高 44、内衬 4、指示器 250ms。
 * 选中段实心主色 + onPrimary 文字；整组作为一个可访问的选项组。
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (options.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(AppMetrics.SegmentedHeight)
            .clip(CircleShape)
            .background(SurfaceLevel.INSET.colorOf(scheme))
            .semantics {
                role = Role.Tab
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        val segment = (maxWidth - AppMetrics.SegmentedInset * 2) / options.size
        val offset by animateDpAsState(
            targetValue = AppMetrics.SegmentedInset + segment * index,
            animationSpec = tween(AppMotion.SegmentedMs, easing = AppMotion.StandardEasing),
            label = "segmentOffset",
        )
        Box(
            Modifier
                .padding(vertical = AppMetrics.SegmentedInset)
                .offset(x = offset)
                .width(segment)
                .height(AppMetrics.SegmentedHeight - AppMetrics.SegmentedInset * 2)
                .clip(CircleShape)
                .background(scheme.primary),
        )
        Row(Modifier.fillMaxWidth().height(AppMetrics.SegmentedHeight)) {
            options.forEach { option ->
                val active = option == selected
                val color by animateColorAsState(
                    targetValue = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    animationSpec = tween(AppMotion.SegmentedTextMs),
                    label = "segmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(AppMetrics.SegmentedHeight)
                        .clickable { onSelect(option) }
                        .semantics { this.selected = active; role = Role.Tab },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = AppType.Subheadline.style(emphasized = active),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 胶囊 chip（FlClash `CommonChip`，高 36）。 */
@Composable
fun CommonChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.primary else SurfaceLevel.INSET.colorOf(scheme)
    val fg = if (selected) scheme.onPrimary else scheme.onSurface
    Row(
        modifier
            .height(AppMetrics.ChipHeight)
            .clip(CircleShape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = AppType.Subheadline.style(), color = fg, maxLines = 1)
    }
}

/** 小色块标签（FlClash `TonalChip`，8 圆角）。 */
@Composable
fun TonalChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text,
        style = AppType.Caption2.style(emphasized = true),
        color = color,
        modifier = modifier
            .clip(squircle(AppCorner.sm))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 主按钮（胶囊、44 高）；`primary=false` 为次按钮。 */
@Composable
fun ShituButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    if (primary) {
        val scheme = MaterialTheme.colorScheme
        Button(
            onClick = onClick,
            modifier = modifier.height(AppMetrics.ButtonHeightBar),
            enabled = enabled,
            shape = CircleShape,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(
                    containerColor = scheme.destructive,
                    contentColor = Color.White,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(text, style = AppType.Subheadline.style(emphasized = true))
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(AppMetrics.ButtonHeightBar),
            enabled = enabled,
            shape = CircleShape,
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(text, style = AppType.Subheadline.style())
        }
    }
}

/** 工具栏/行尾图标按钮：48 触控区，图标 24。 */
@Composable
fun ShituIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selected: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
