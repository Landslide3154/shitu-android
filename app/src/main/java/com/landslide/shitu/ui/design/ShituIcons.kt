package com.landslide.shitu.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 24×24 网格、1.8 描边、round cap/join 的单色图标（FlClash 图标语言）。
 *
 * 几何来自 Lucide（ISC License，https://lucide.dev），仅保留 path 数据；
 * 不包含 FlClash 自绘字形与品牌资源。生成脚本：tools/gen_lucide_icons.py。
 */
object ShituIcons {
    val Rules: ImageVector = icon(
        "Rules",
        "M13 5h8",
        "M13 12h8",
        "M13 19h8",
        "m3 17 2 2 4-4",
        "m3 7 2 2 4-4",
    )
    val Logs: ImageVector = icon(
        "Logs",
        "M15 12h-5",
        "M15 8h-5",
        "M19 17V5a2 2 0 0 0-2-2H4",
        "M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3",
    )
    val Settings: ImageVector = icon(
        "Settings",
        "M14 17H5",
        "M19 7h-9",
        "M14,17 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0",
        "M4,7 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0",
    )
    val Plus: ImageVector = icon(
        "Plus",
        "M5 12h14",
        "M12 5v14",
    )
    val Search: ImageVector = icon(
        "Search",
        "m21 21-4.34-4.34",
        "M3,11 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0",
    )
    val Filter: ImageVector = icon(
        "Filter",
        "M2 5h20",
        "M6 12h12",
        "M9 19h6",
    )
    val Sort: ImageVector = icon(
        "Sort",
        "m21 16-4 4-4-4",
        "M17 20V4",
        "m3 8 4-4 4 4",
        "M7 4v16",
    )
    val Check: ImageVector = icon(
        "Check",
        "M20 6 9 17l-5-5",
    )
    val Close: ImageVector = icon(
        "Close",
        "M18 6 6 18",
        "m6 6 12 12",
    )
    val ChevronRight: ImageVector = icon(
        "ChevronRight",
        "m9 18 6-6-6-6",
    )
    val ChevronDown: ImageVector = icon(
        "ChevronDown",
        "m6 9 6 6 6-6",
    )
    val ChevronUp: ImageVector = icon(
        "ChevronUp",
        "m18 15-6-6-6 6",
    )
    val ChevronLeft: ImageVector = icon(
        "ChevronLeft",
        "m15 18-6-6 6-6",
    )
    val More: ImageVector = icon(
        "More",
        "M11,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
        "M18,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
        "M4,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
    )
    val Play: ImageVector = icon(
        "Play",
        "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
    )
    val Undo: ImageVector = icon(
        "Undo",
        "M9 14 4 9l5-5",
        "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
    )
    val Copy: ImageVector = icon(
        "Copy",
        "M10,8 H20 A2,2 0 0 1 22,10 V20 A2,2 0 0 1 20,22 H10 A2,2 0 0 1 8,20 V10 A2,2 0 0 1 10,8 Z",
        "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
    )
    val Trash: ImageVector = icon(
        "Trash",
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    )
    val Folder: ImageVector = icon(
        "Folder",
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
    )
    val FolderOpen: ImageVector = icon(
        "FolderOpen",
        "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2",
    )
    val Download: ImageVector = icon(
        "Download",
        "M12 15V3",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
    )
    val Upload: ImageVector = icon(
        "Upload",
        "M12 3v12",
        "m17 8-5-5-5 5",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
    )
    val Refresh: ImageVector = icon(
        "Refresh",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    )
    val Shield: ImageVector = icon(
        "Shield",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4",
    )
    val Bell: ImageVector = icon(
        "Bell",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    )
    val Palette: ImageVector = icon(
        "Palette",
        "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
        "M13,6.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0",
        "M17,10.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0",
        "M6,12.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0",
        "M8,7.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0",
    )
    val Info: ImageVector = icon(
        "Info",
        "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0",
        "M12 16v-4",
        "M12 8h.01",
    )
    val Warning: ImageVector = icon(
        "Warning",
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
    )
    val Clock: ImageVector = icon(
        "Clock",
        "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0",
        "M12 6v6l4 2",
    )
    val Sun: ImageVector = icon(
        "Sun",
        "M8,12 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0",
        "M12 2v2",
        "M12 20v2",
        "m4.93 4.93 1.41 1.41",
        "m17.66 17.66 1.41 1.41",
        "M2 12h2",
        "M20 12h2",
        "m6.34 17.66-1.41 1.41",
        "m19.07 4.93-1.41 1.41",
    )
    val Moon: ImageVector = icon(
        "Moon",
        "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
    )
    val Monitor: ImageVector = icon(
        "Monitor",
        "M4,3 H20 A2,2 0 0 1 22,5 V15 A2,2 0 0 1 20,17 H4 A2,2 0 0 1 2,15 V5 A2,2 0 0 1 4,3 Z",
        "M8,21 L16,21",
        "M12,17 L12,21",
    )
    val Database: ImageVector = icon(
        "Database",
        "M3 5V19A9 3 0 0 0 21 19V5",
        "M3 12A9 3 0 0 0 21 12",
    )
    val Zap: ImageVector = icon(
        "Zap",
        "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z",
    )
    val ArrowRightLeft: ImageVector = icon(
        "ArrowRightLeft",
        "m16 3 4 4-4 4",
        "M20 7H4",
        "m8 21-4-4 4-4",
        "M4 17h16",
    )
    val GripVertical: ImageVector = icon(
        "GripVertical",
        "M8,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
        "M8,5 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
        "M8,19 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
        "M14,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
        "M14,5 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
        "M14,19 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0",
    )
    val ExternalLink: ImageVector = icon(
        "ExternalLink",
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    )
    val Image: ImageVector = icon(
        "Image",
        "M5,3 H19 A2,2 0 0 1 21,5 V19 A2,2 0 0 1 19,21 H5 A2,2 0 0 1 3,19 V5 A2,2 0 0 1 5,3 Z",
        "M7,9 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
    )
    val Pause: ImageVector = icon(
        "Pause",
        "M15,3 H18 A1,1 0 0 1 19,4 V20 A1,1 0 0 1 18,21 H15 A1,1 0 0 1 14,20 V4 A1,1 0 0 1 15,3 Z",
        "M6,3 H9 A1,1 0 0 1 10,4 V20 A1,1 0 0 1 9,21 H6 A1,1 0 0 1 5,20 V4 A1,1 0 0 1 6,3 Z",
    )
    val CircleCheck: ImageVector = icon(
        "CircleCheck",
        "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0",
        "m16 9-5.5 5.5L8 12",
    )
    val CircleX: ImageVector = icon(
        "CircleX",
        "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0",
        "m15 9-6 6",
        "m9 9 6 6",
    )
    val CircleAlert: ImageVector = icon(
        "CircleAlert",
        "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0",
        "M12,8 L12,12",
        "M12,16 L12.01,16",
    )
    val Power: ImageVector = icon(
        "Power",
        "M12 2v10",
        "M18.4 6.6a9 9 0 1 1-12.77.04",
    )
    val HardDrive: ImageVector = icon(
        "HardDrive",
        "M10 16h.01",
        "M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
        "M21.946 12.013H2.054",
        "M6 16h.01",
    )
    val Timer: ImageVector = icon(
        "Timer",
        "M10,2 L14,2",
        "M12,14 L15,11",
        "M4,14 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0",
    )
}

private fun icon(name: String, vararg pathData: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    pathData.forEach { d ->
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
