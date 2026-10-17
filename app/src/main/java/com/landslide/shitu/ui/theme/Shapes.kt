package com.landslide.shitu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 圆角的唯一来源。
 *
 * 以前每个文件各写各的数字（8 / 10 / 12 / 14 / 18 / 20 / 24 / 28），同一种东西圆角都不一样；
 * 现在一律引用这里，改圆角只改这一个文件。
 *
 * - [Card] 大卡片：规则卡 / 设置卡 / 状态卡 / 日志卡
 * - [Dialog] 自定义对话框（M3 的 AlertDialog 自动跟随 Theme 的 extraLarge）
 * - [Field] 所有输入框（见 `ui/components/ShituField.kt`）
 * - [Row] 列表行、小色块、弹窗里的选项行
 * - [Pill] 胶囊：状态标签 / 模式标签
 */
object ShituRadius {
    val Card = 20.dp
    val Dialog = 28.dp
    val Field = 14.dp
    val Row = 12.dp
    val Pill = 50

    val CardShape = RoundedCornerShape(Card)
    val DialogShape = RoundedCornerShape(Dialog)
    val FieldShape = RoundedCornerShape(Field)
    val RowShape = RoundedCornerShape(Row)
    val PillShape = RoundedCornerShape(Pill)

    /** 缩略图这类随尺寸等比缩放的块：半径 = 边长 / 6，本身没有固定尺寸，不算「不统一」。 */
    fun thumb(size: Dp): RoundedCornerShape = RoundedCornerShape(size / 6f)
}

/**
 * 交给 `MaterialTheme` 的形状表：M3 组件没显式传 shape 时会自动用这里，全 App 跟着统一。
 *
 * - medium = 卡片 20dp（Card 默认形状）
 * - extraLarge = 对话框 28dp（AlertDialog 默认形状）
 * - large = 20dp（FloatingActionButton 默认，变成 M3 Expressive 的圆角方形）
 * - small = 列表行 12dp；extraSmall = 菜单/提示条 8dp
 */
internal val ShituShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = ShituRadius.RowShape,
    medium = ShituRadius.CardShape,
    large = ShituRadius.CardShape,
    extraLarge = ShituRadius.DialogShape,
)
