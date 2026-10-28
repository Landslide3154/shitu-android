#!/usr/bin/env python3
"""Fetch Lucide SVGs and generate Compose ImageVectors for shitu.

Lucide is ISC-licensed (https://lucide.dev). We keep only path geometry,
stroke 1.8, round caps/joins — the same 24-grid stroke language as FlClash,
without copying FlClash's own glyphs.

Usage: python tools/gen_lucide_icons.py
"""
from __future__ import annotations

import os
import re
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".icon-cache")
OUT = os.path.join(ROOT, "app/src/main/java/com/landslide/shitu/ui/design/ShituIcons.kt")
BASE = "https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/{}.svg"

# (Kotlin property, lucide slug)
ICONS = [
    ("Rules", "list-checks"),
    ("Logs", "scroll-text"),
    ("Settings", "settings-2"),
    ("Plus", "plus"),
    ("Search", "search"),
    ("Filter", "list-filter"),
    ("Sort", "arrow-up-down"),
    ("Check", "check"),
    ("Close", "x"),
    ("ChevronRight", "chevron-right"),
    ("ChevronDown", "chevron-down"),
    ("ChevronUp", "chevron-up"),
    ("ChevronLeft", "chevron-left"),
    ("More", "ellipsis"),
    ("Play", "play"),
    ("Undo", "undo-2"),
    ("Copy", "copy"),
    ("Trash", "trash"),
    ("Folder", "folder"),
    ("FolderOpen", "folder-open"),
    ("Download", "download"),
    ("Upload", "upload"),
    ("Refresh", "refresh-cw"),
    ("Shield", "shield-check"),
    ("Bell", "bell"),
    ("Palette", "palette"),
    ("Info", "info"),
    ("Warning", "triangle-alert"),
    ("Clock", "clock"),
    ("Sun", "sun"),
    ("Moon", "moon"),
    ("Monitor", "monitor"),
    ("Database", "database"),
    ("Zap", "zap"),
    ("ArrowRightLeft", "arrow-right-left"),
    ("GripVertical", "grip-vertical"),
    ("ExternalLink", "external-link"),
    ("Image", "image"),
    ("Pause", "pause"),
    ("CircleCheck", "circle-check"),
    ("CircleX", "circle-x"),
    ("CircleAlert", "circle-alert"),
    ("Power", "power"),
    ("HardDrive", "hard-drive"),
    ("Timer", "timer"),
]


def fetch(slug: str) -> str:
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, f"{slug}.svg")
    if not os.path.exists(path):
        print("fetch", slug)
        try:
            with urllib.request.urlopen(BASE.format(slug), timeout=30) as r:
                open(path, "wb").write(r.read())
        except Exception as e:  # noqa: BLE001
            print("  failed:", slug, e)
            return ""
    return open(path, "r", encoding="utf-8").read()


def fmt(v: float) -> str:
    return ("%.3f" % v).rstrip("0").rstrip(".")


def shape_to_d(el) -> str | None:
    tag = el.tag.split("}")[-1]
    if tag == "path":
        return el.get("d")
    if tag == "circle":
        cx, cy, r = (float(el.get(k, 0)) for k in ("cx", "cy", "r"))
        return (f"M{fmt(cx - r)},{fmt(cy)} a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2*r)},0 "
                f"a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2*r)},0")
    if tag == "line":
        x1, y1, x2, y2 = (float(el.get(k, 0)) for k in ("x1", "y1", "x2", "y2"))
        return f"M{fmt(x1)},{fmt(y1)} L{fmt(x2)},{fmt(y2)}"
    if tag in ("polyline", "polygon"):
        nums = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", el.get("points", ""))]
        if len(nums) < 4:
            return None
        parts = [f"M{fmt(nums[0])},{fmt(nums[1])}"]
        for i in range(2, len(nums) - 1, 2):
            parts.append(f"L{fmt(nums[i])},{fmt(nums[i+1])}")
        if tag == "polygon":
            parts.append("Z")
        return " ".join(parts)
    if tag == "rect":
        x, y = float(el.get("x", 0)), float(el.get("y", 0))
        w, h = float(el.get("width", 0)), float(el.get("height", 0))
        rx = float(el.get("rx", 0) or 0)
        ry = float(el.get("ry", rx) or rx)
        if rx <= 0 or ry <= 0:
            return (f"M{fmt(x)},{fmt(y)} H{fmt(x+w)} V{fmt(y+h)} "
                    f"H{fmt(x)} Z")
        return (
            f"M{fmt(x+rx)},{fmt(y)} H{fmt(x+w-rx)} "
            f"A{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(x+w)},{fmt(y+ry)} "
            f"V{fmt(y+h-ry)} A{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(x+w-rx)},{fmt(y+h)} "
            f"H{fmt(x+rx)} A{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(x)},{fmt(y+h-ry)} "
            f"V{fmt(y+ry)} A{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(x+rx)},{fmt(y)} Z"
        )
    return None


def parse(svg: str) -> list[str]:
    svg = re.sub(r"<!--.*?-->", "", svg, flags=re.S)
    root = ET.fromstring(svg)
    out = []
    for el in root.iter():
        d = shape_to_d(el)
        if d:
            out.append(" ".join(d.split()))
    return out


def main() -> None:
    lines = [
        "package com.landslide.shitu.ui.design",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.StrokeCap",
        "import androidx.compose.ui.graphics.StrokeJoin",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "import androidx.compose.ui.graphics.vector.PathParser",
        "import androidx.compose.ui.unit.dp",
        "",
        "/**",
        " * 24×24 网格、1.8 描边、round cap/join 的单色图标（FlClash 图标语言）。",
        " *",
        " * 几何来自 Lucide（ISC License，https://lucide.dev），仅保留 path 数据；",
        " * 不包含 FlClash 自绘字形与品牌资源。生成脚本：tools/gen_lucide_icons.py。",
        " */",
        "object ShituIcons {",
    ]
    count = 0
    for prop, slug in ICONS:
        svg = fetch(slug)
        if not svg:
            continue
        paths = parse(svg)
        if not paths:
            print("skip empty", slug)
            continue
        lines.append(f'    val {prop}: ImageVector = icon(')
        lines.append(f'        "{prop}",')
        for d in paths:
            lines.append(f'        "{d}",')
        lines.append("    )")
        count += 1
    lines += [
        "}",
        "",
        "private fun icon(name: String, vararg pathData: String): ImageVector {",
        "    val builder = ImageVector.Builder(",
        "        name = name,",
        "        defaultWidth = 24.dp,",
        "        defaultHeight = 24.dp,",
        "        viewportWidth = 24f,",
        "        viewportHeight = 24f,",
        "    )",
        "    pathData.forEach { d ->",
        "        builder.addPath(",
        "            pathData = PathParser().parsePathString(d).toNodes(),",
        "            fill = null,",
        "            stroke = SolidColor(Color.Black),",
        "            strokeLineWidth = 1.8f,",
        "            strokeLineCap = StrokeCap.Round,",
        "            strokeLineJoin = StrokeJoin.Round,",
        "        )",
        "    }",
        "    return builder.build()",
        "}",
        "",
    ]
    open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
    print("wrote", OUT, "icons", count)


if __name__ == "__main__":
    main()
