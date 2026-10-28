package com.landslide.shitu.ui.design

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * 动效参数（FlClash `lib/ui/motion.dart` + 各组件常量）。
 *
 * 原则：结构动作用弹簧/强调曲线，状态反馈用 150–300ms 短曲线；系统关动画时全部 0。
 */
object AppMotion {
    const val MidMs = 200
    const val CommonMs = 300
    const val PageMs = 450
    const val TabFadeMs = 220
    const val SwitchMs = 300
    const val SwitchPressMs = 150
    const val SwitchReleaseMs = 200
    const val SegmentedMs = 250
    const val SegmentedTextMs = 200
    const val DialogMs = 404
    const val SheetMs = 300
    const val EmptyStaggerMs = 60L
    const val LoadingDelayMs = 400L
    const val SkeletonMs = 1600

    /** FlClash `easeInOutCubicEmphasized` 的等价曲线。 */
    val PageEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardEasing: Easing = FastOutSlowInEasing
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Android 上的 `easeOutBack` 近似。 */
    val EaseOutBack: Easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)

    /** 开关回弹；FlClash 用 300ms easeOutBack，这里保留弹簧给手势打断。 */
    fun <T> switchSpring() = spring<T>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 底栏选中板移动。 */
    fun <T> travelSpring() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 380f,
    )

    fun duration(ms: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else ms
}

/** 系统「移除动画 / 动画时长 0」时返回 true；值在组合期固定，避免每帧读系统设置。 */
@Composable
fun rememberReduceMotion(): Boolean = remember { !ValueAnimator.areAnimatorsEnabled() }
