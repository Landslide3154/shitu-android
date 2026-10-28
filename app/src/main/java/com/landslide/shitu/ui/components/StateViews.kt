package com.landslide.shitu.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.SurfaceLevel

/**
 * 空态（FlClash `NullStatus`）：插画 + 标题 + 说明 + 一个动作。
 * 插画是自绘矢量（文件夹 + 图片），不用位图，也不复制 FlClash 素材。
 */
@Composable
fun EmptyState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration(Modifier.size(140.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = AppType.Headline.style(emphasized = true),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = AppType.Subheadline.style(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            ShituButton(actionLabel, onAction, primary = true)
        }
    }
}

@Composable
private fun EmptyIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val back = scheme.surfaceContainerHigh
    val front = scheme.surfaceContainerHighest
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.008f
        // 文件夹后片
        drawRoundRect(
            color = back,
            topLeft = Offset(w * 0.16f, h * 0.28f),
            size = Size(w * 0.68f, h * 0.2f),
            cornerRadius = CornerRadius(w * 0.05f),
        )
        // 文件夹前片
        drawRoundRect(
            color = front,
            topLeft = Offset(w * 0.12f, h * 0.38f),
            size = Size(w * 0.76f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.07f),
        )
        // 照片卡
        drawRoundRect(
            color = scheme.surface,
            topLeft = Offset(w * 0.28f, h * 0.46f),
            size = Size(w * 0.44f, h * 0.26f),
            cornerRadius = CornerRadius(w * 0.035f),
        )
        // 山与太阳
        drawCircle(
            color = accent.copy(alpha = 0.7f),
            radius = w * 0.035f,
            center = Offset(w * 0.62f, h * 0.53f),
        )
        drawLine(
            color = accent.copy(alpha = 0.55f),
            start = Offset(w * 0.32f, h * 0.66f),
            end = Offset(w * 0.46f, h * 0.55f),
            strokeWidth = stroke * 3,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent.copy(alpha = 0.55f),
            start = Offset(w * 0.46f, h * 0.55f),
            end = Offset(w * 0.56f, h * 0.62f),
            strokeWidth = stroke * 3,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent.copy(alpha = 0.55f),
            start = Offset(w * 0.56f, h * 0.62f),
            end = Offset(w * 0.68f, h * 0.52f),
            strokeWidth = stroke * 3,
            cap = StrokeCap.Round,
        )
    }
}

/** 页面级加载：48 的彗星环，1000ms 一圈。 */
@Composable
fun LoadingRing(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "loading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension / 12f
            drawArc(
                color = color.copy(alpha = 0.12f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            rotate(angle) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** 未知结构的占位：胶囊呼吸 1600ms。 */
@Composable
fun SkeletonPill(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 12.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(
        modifier
            .width(width)
            .height(height)
            .background(
                SurfaceLevel.INSET.colorOf(MaterialTheme.colorScheme).copy(alpha = alpha),
                CircleShape,
            ),
    )
}
