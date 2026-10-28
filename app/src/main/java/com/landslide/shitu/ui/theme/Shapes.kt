package com.landslide.shitu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import com.landslide.shitu.ui.design.AppCorner
import com.landslide.shitu.ui.design.AppShapes
import com.landslide.shitu.ui.design.squircle

/**
 * 圆角的唯一来源（FlClash 阶梯：0/4/8/16/20/24/28/34/full）。
 *
 * 具体形状由 [squircle] 生成超椭圆；这里只保留语义名，页面不要写裸值。
 *
 * - [Card] 普通卡片 16；[Wide] 通栏卡/分组容器 24
 * - [Dialog] 告警 34；[Sheet] 弹层顶部 28
 * - [Field] 输入框 16；[Row] 菜单/列表行 16；[Chip] 小色块 8
 * - [Pill] 胶囊（按钮/状态标签）
 */
object ShituRadius {
    val Card = AppCorner.md
    val Wide = AppCorner.xl
    val Dialog = AppCorner.alert
    val Sheet = AppCorner.xxl
    val Field = AppCorner.md
    val Row = AppCorner.md
    val Chip = AppCorner.sm
    val Pill = AppCorner.full

    val CardShape = squircle(Card)
    val WideShape = squircle(Wide)
    val DialogShape = squircle(Dialog)
    val SheetShape = squircle(Sheet)
    val FieldShape = squircle(Field)
    val RowShape = squircle(Row)
    val ChipShape = squircle(Chip)
    val PillShape = RoundedCornerShape(50)

    /** 缩略图随尺寸等比（半径 = 边长 / 6），本身没有固定尺寸。 */
    fun thumb(size: Dp) = squircle(size / 6f)
}

/** 交给 `MaterialTheme` 的形状表；M3 组件没显式传 shape 时自动跟随。 */
internal val ShituShapes: Shapes = AppShapes
