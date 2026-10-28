package com.landslide.shitu.ui.design

import androidx.compose.ui.unit.dp

/**
 * 间距 / 尺寸 / 时长的唯一来源，数值取自 FlClash 0.9.2（`lib/ui/metrics.dart`、
 * `lib/ui/sidebar.dart`、`lib/ui/navigation_dock.dart`、`lib/ui/dialog.dart`）。
 *
 * 页面里不要再写裸 dp：行高、内衬、工具栏、弹层、底栏都从这里取。
 */
object AppMetrics {
    // 列表 / 设置行
    val ListRowMinHeight = 56.dp
    val ListRowVPad = 10.dp
    val ListRowStartPad = 16.dp
    val ListRowEndPad = 8.dp
    val SectionHeaderTop = 24.dp
    val SectionHeaderBottom = 8.dp
    val SectionGap = 16.dp

    // 页面
    val PagePadding = 16.dp
    val PageToolbarHeight = 72.dp
    val ContentMaxWidth = 840.dp
    val PageBottomPadding = 32.dp

    // 弹层 / 告警
    val SheetToolbarHeight = 48.dp
    val SheetHeaderHeight = 68.dp
    val SheetHandleWidth = 28.dp
    val SheetHandleHeight = 6.dp
    val DialogWidth = 348.dp
    val DialogActionHeight = 48.dp
    val DialogContentMaxWidth = 300.dp

    // 导航
    val DockHeight = 62.dp
    val DockInset = 4.dp
    val DockEdge = 21.dp
    val DockIcon = 28.dp
    val DockLabelSize = 10.dp
    val SidebarCollapsed = 48.dp
    val SidebarExpanded = 220.dp
    val SidebarItemHeight = 40.dp
    val SidebarItemGap = 4.dp
    val SidebarIcon = 24.dp
    val SidebarIndicatorWidth = 3.dp
    val SidebarMaxPad = 12.dp

    // 分段 / chip / 按钮
    val SegmentedHeight = 44.dp
    val SegmentedInset = 4.dp
    val ChipHeight = 36.dp
    val ButtonHeightBar = 44.dp
    val ButtonHeightSection = 36.dp
    val ButtonHeightCompact = 32.dp

    // 图标
    val IconSize = 24.dp
    val IconStroke = 1.8f
}
