package com.landslide.shitu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.landslide.shitu.ui.design.AppCorner
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.SheetTopShape
import com.landslide.shitu.ui.design.destructive
import com.landslide.shitu.ui.design.floatingColor
import com.landslide.shitu.ui.design.squircle

/**
 * 告警（FlClash `CommonDialog`）：宽 348、圆角 34、动作高 48。
 * 单值提示/破坏性确认走这里；多个字段的表单请用页面或 [AppSheet]。
 */
@Composable
fun CommonDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .widthIn(max = AppMetrics.DialogWidth)
                    .fillMaxWidth(),
                shape = squircle(AppCorner.alert),
                color = floatingColor(scheme),
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
            ) {
                Column {
                    Text(
                        title,
                        style = AppType.Headline.style(),
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 30.dp, top = 21.dp, end = 30.dp),
                    )
                    if (body != null) {
                        Text(
                            body,
                            style = AppType.Subheadline.style(),
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 30.dp, top = 6.dp, end = 30.dp),
                        )
                    }
                    content?.let {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                                .heightIn(max = 320.dp),
                            content = it,
                        )
                    }
                    Spacer(Modifier.height(if (content != null || body != null) 16.dp else 20.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissLabel != null) {
                            TextButton(onClick = onDismiss, modifier = Modifier.height(AppMetrics.DialogActionHeight)) {
                                Text(
                                    dismissLabel,
                                    style = AppType.Body.style(),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(
                            onClick = onConfirm,
                            modifier = Modifier.height(AppMetrics.DialogActionHeight),
                        ) {
                            Text(
                                confirmLabel,
                                style = AppType.Body.style(emphasized = true),
                                color = if (destructive) scheme.destructive else scheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部弹层（FlClash `SnapSheet` 的轻量版）：顶部 28 圆角、6×28 把手、68 高标题栏。
 * 多值选择/长列表/详情都走这里，不再塞进 AlertDialog。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = state,
        shape = SheetTopShape,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(AppMetrics.SheetHandleWidth, AppMetrics.SheetHandleHeight)
                        .background(scheme.onSurface.copy(alpha = 0.2f), CircleShape),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            if (title != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(AppMetrics.SheetHeaderHeight)
                        .padding(horizontal = AppMetrics.PagePadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        title,
                        style = AppType.PageTitle.style(),
                        color = scheme.onSurface,
                    )
                }
            }
            content()
        }
    }
}
