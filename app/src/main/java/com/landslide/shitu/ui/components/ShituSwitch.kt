package com.landslide.shitu.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * 全 App 唯一的开关：M3 Switch + 切换时的 spring 回弹（FlClash 迁移 P4）。
 *
 * 只是外面套一层 0.9 → 1 的缩放弹簧：不改开关本身的形状/尺寸/点击区域，
 * 但按下时有一点「实体咔哒」的物理感，而不是纯色块跳变。
 *
 * 系统关掉动画（`ANIMATOR_DURATION_SCALE == 0` / 移除动画）时直接到位，不做回弹。
 */
@Composable
fun ShituSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(checked) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (!animationsEnabled) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(0.9f)
        scale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
    }

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.scale(scale.value),
    )
}
