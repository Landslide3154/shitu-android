package com.landslide.shitu.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Flourish 圆角阶梯（FlClash `lib/ui/shape.dart:6-28`）。
 * 选择规则：`fit(shortestSide)` = 不超过最短边 1/3 的最大档。
 */
object AppCorner {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 28.dp

    /** iOS 27 UIAlertController 的圆角，比 Sheet 更圆。 */
    val alert = 34.dp
    val full = 1000.dp

    fun fit(shortestSide: Dp): Dp {
        val limit = shortestSide / 3
        return when {
            limit >= xxl -> xxl
            limit >= xl -> xl
            limit >= lg -> lg
            limit >= md -> md
            limit >= sm -> sm
            limit >= xs -> xs
            else -> none
        }
    }
}

/**
 * 超椭圆（squircle）圆角。
 *
 * FlClash 用 `material_ui` 的 `RoundedSuperellipseBorder`；Compose 没有原生实现，
 * 这里按超椭圆方程 `|x/r|^n + |y/r|^n = 1` 采样每个角（n = 4），再连成 Path。
 * 半径 ≥ 最短边一半时退化为胶囊/圆，和 FlClash 的 `full` 一致。
 *
 * Path 按尺寸缓存：列表滚动时每行尺寸固定，不会反复重建。
 */
class SquircleShape(
    private val radius: Dp,
    private val exponent: Float = 4f,
) : Shape {

    private val cache = HashMap<Size, Path>()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect(0f, 0f, 0f, 0f))
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val path = cache.getOrPut(size) { buildPath(size, r) }
        return Outline.Generic(path)
    }

    private fun buildPath(size: Size, r: Float): Path {
        val w = size.width
        val h = size.height
        val path = Path()
        // 顺序：上边 → 右上 → 右边 → 右下 → 下边 → 左下 → 左边 → 左上
        val corners = listOf(
            Offset(w - r, r) to 270f, // 右上 270° → 360°
            Offset(w - r, h - r) to 0f, // 右下 0° → 90°
            Offset(r, h - r) to 90f, // 左下 90° → 180°
            Offset(r, r) to 180f, // 左上 180° → 270°
        )
        path.moveTo(r, 0f)
        for ((center, start) in corners) {
            appendCorner(path, center, r, start, start + 90f)
        }
        path.close()
        return path
    }

    private fun appendCorner(path: Path, center: Offset, r: Float, from: Float, to: Float) {
        val steps = 20
        for (i in 0..steps) {
            val angle = Math.toRadians((from + (to - from) * i / steps).toDouble())
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            // 超椭圆参数方程：x = r * sign(c) * |c|^(2/n)
            val x = center.x + r * signPow(c, 2f / exponent)
            val y = center.y + r * signPow(s, 2f / exponent)
            path.lineTo(x, y)
        }
    }

    private fun signPow(value: Float, power: Float): Float {
        if (abs(value) < 1e-6f) return 0f
        val p = abs(value).pow(power)
        return if (value < 0) -p else p
    }
}

/** 快捷构造：普通档位走超椭圆，`full` 走胶囊。 */
fun squircle(corner: Dp): Shape =
    if (corner >= AppCorner.full) RoundedCornerShape(50) else SquircleShape(corner)

/** M3 全局形状注入：卡片 16 / 对话 34 / chip 8 / Snackbar 4。
 *
 * 注意：M3 的 `Shapes` 只接受 `CornerBasedShape`，超椭圆的 `SquircleShape` 不能直接塞进来；
 * 新组件显式使用 `ShituRadius.*Shape`（超椭圆），M3 内置组件退回圆角近似。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppCorner.xs),
    small = RoundedCornerShape(AppCorner.sm),
    medium = RoundedCornerShape(AppCorner.md),
    large = RoundedCornerShape(AppCorner.lg),
    extraLarge = RoundedCornerShape(AppCorner.alert),
)

/** Sheet 专用（M3 的 extraLarge 是 34，不能直接复用）。 */
val SheetTopShape = RoundedCornerShape(
    topStart = AppCorner.xxl,
    topEnd = AppCorner.xxl,
)
