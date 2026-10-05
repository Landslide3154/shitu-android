# -*- coding: utf-8 -*-
"""
拾图 Shitu 图标生成器。

按 ~/.dsh/AGENTS.md §7「应用图标与视觉规范」：
- 底板「经典圆角」：半径 r = 0.35 × 边长，圆角用**超椭圆**（指数 n = 4），不是 CSS 正圆角（n=2）。
- 底色纯色，无渐变；前景纯白单色剪影，居中，高约 0.72 × 边长。
- 轮廓不含折点：所有折角一律用超椭圆弧替换（切线连续），山脊用 Catmull-Rom 样条。
- 前景往右下投一层轻投影：偏移 0.024 S、模糊 0.022 S。
- 4 倍超采样后 LANCZOS 缩小，做抗锯齿。

输出：
  1) 完整底板图标 PNG（README / Release 页用）
  2) Android 自适应图标的前景层 / 单色层（108dp 画布，内容限制在中央安全区）

用法：python tools/make_icon.py [变体名 ...]
"""

import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SS = 4  # 超采样倍数

BRAND = (0x1B, 0x4F, 0xC4, 255)  # 与 App 主题主色一致（Theme.kt 的 Blue）
WHITE = (255, 255, 255, 255)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "docs", "icon")


# ---------- 几何工具 ----------

def superellipse_arc(cx, cy, r, t0, t1, n=4.0, steps=72):
    """超椭圆角：从 t0 到 t1 采样，x = cx + r·sgn(cos)·|cos|^(2/n)，y 同理。"""
    pts = []
    for i in range(steps + 1):
        t = t0 + (t1 - t0) * i / steps
        c, s = math.cos(t), math.sin(t)
        x = cx + r * math.copysign(abs(c) ** (2.0 / n), c)
        y = cy + r * math.copysign(abs(s) ** (2.0 / n), s)
        pts.append((x, y))
    return pts


def rounded_rect(x0, y0, x1, y1, r, n=4.0, steps=72):
    """超椭圆圆角矩形（顺时针：右下 → 左下 → 左上 → 右上）。图片坐标 y 向下。"""
    r = max(0.0, min(r, (x1 - x0) / 2.0, (y1 - y0) / 2.0))
    pts = []
    pts += superellipse_arc(x1 - r, y1 - r, r, 0.0, math.pi / 2, n, steps)
    pts += superellipse_arc(x0 + r, y1 - r, r, math.pi / 2, math.pi, n, steps)
    pts += superellipse_arc(x0 + r, y0 + r, r, math.pi, 1.5 * math.pi, n, steps)
    pts += superellipse_arc(x1 - r, y0 + r, r, 1.5 * math.pi, 2.0 * math.pi, n, steps)
    return pts


def rounded_polygon(points, radius, n=4.0, steps=48):
    """把多边形的每个折角换成超椭圆弧 → 轮廓无折点（切线连续）。

    注意：圆角的**弧心在内切圆心**（沿角平分线、距顶点 r/sin(半角)），不是顶点本身；
    弧的起止点是两条边上的切点。放错位置会鼓成一个疙瘩。
    """
    m = len(points)
    out = []
    for i in range(m):
        px, py = points[(i - 1) % m]
        cx, cy = points[i]
        nx, ny = points[(i + 1) % m]
        v1 = (px - cx, py - cy)
        v2 = (nx - cx, ny - cy)
        l1 = math.hypot(*v1)
        l2 = math.hypot(*v2)
        if l1 < 1e-9 or l2 < 1e-9:
            out.append((cx, cy))
            continue
        u1 = (v1[0] / l1, v1[1] / l1)
        u2 = (v2[0] / l2, v2[1] / l2)
        cos_phi = max(-1.0, min(1.0, u1[0] * u2[0] + u1[1] * u2[1]))
        phi = math.acos(cos_phi)
        if phi < 1e-6 or phi > math.pi - 1e-6:
            out.append((cx, cy))
            continue
        half = phi / 2.0
        r = min(radius, l1 * math.tan(half) * 0.5, l2 * math.tan(half) * 0.5)
        t = r / math.tan(half)  # 顶点到切点的距离
        bis = (u1[0] + u2[0], u1[1] + u2[1])
        bl = math.hypot(*bis)
        bis = (bis[0] / bl, bis[1] / bl)
        d = r / math.sin(half)  # 顶点到内切圆心
        ox, oy = cx + bis[0] * d, cy + bis[1] * d
        start = (cx + u1[0] * t, cy + u1[1] * t)
        end = (cx + u2[0] * t, cy + u2[1] * t)
        a1 = math.atan2(start[1] - oy, start[0] - ox)
        a2 = math.atan2(end[1] - oy, end[0] - ox)
        d_ang = a2 - a1
        while d_ang > math.pi:
            d_ang -= 2 * math.pi
        while d_ang < -math.pi:
            d_ang += 2 * math.pi
        out += superellipse_arc(ox, oy, r, a1, a1 + d_ang, n, steps)
    return out


def catmull_rom(points, samples=28, closed=False):
    """Catmull-Rom 样条：山脊这类自由曲线用它，避免任何折点。"""
    pts = list(points)
    if closed:
        pts = [pts[-1]] + pts + [pts[0], pts[1]]
    else:
        pts = [pts[0]] + pts + [pts[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for j in range(samples):
            t = j / samples
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t +
                       (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 +
                       (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t +
                       (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 +
                       (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    out.append(pts[-2])
    return out


# ---------- 画剪影 ----------

def new_mask(size):
    m = Image.new("L", (size, size), 0)
    return m, ImageDraw.Draw(m)


def fill(draw, pts, value=255):
    draw.polygon(pts, fill=value)


def hole(draw, pts):
    fill(draw, pts, 0)


def disc(draw, cx, cy, r, value=255):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=value)


def stroke(draw, pts, width, value=255):
    """带圆头的粗折线（点足够密时看着就是光滑曲线）。"""
    draw.line(pts, fill=value, width=int(round(width)), joint="curve")
    r = width / 2.0
    for p in (pts[0], pts[-1]):
        disc(draw, p[0], p[1], r, value)


def end_direction(pts, back):
    """末端切线方向：沿路径往回找**足够远**的一点做参考。

    注意不能直接用 pts[-2]/pts[-3]——采样点末尾几乎重合，算出来的方向是噪声。
    """
    tip = pts[-1]
    ref = pts[0]
    for p in reversed(pts):
        if math.hypot(tip[0] - p[0], tip[1] - p[1]) >= back:
            ref = p
            break
    return math.atan2(tip[1] - ref[1], tip[0] - ref[0])


def curved_arrow(draw, pts, shaft_w, head_len, head_w, value=255):
    """一条带箭头的曲线：圆头箭杆 + 圆角三角箭头（三只角也是超椭圆弧，无折点）。"""
    stroke(draw, pts, shaft_w, value)
    tip = pts[-1]
    ang = end_direction(pts, head_len * 0.9)
    back = (tip[0] - math.cos(ang) * head_len * 0.72,
            tip[1] - math.sin(ang) * head_len * 0.72)
    perp = (math.cos(ang + math.pi / 2), math.sin(ang + math.pi / 2))
    left = (back[0] + perp[0] * head_w / 2, back[1] + perp[1] * head_w / 2)
    right = (back[0] - perp[0] * head_w / 2, back[1] - perp[1] * head_w / 2)
    fill(draw, rounded_polygon([tip, left, right],
                               radius=min(head_w, head_len) * 0.14), value)


def rotate_pts(pts, cx, cy, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return [((x - cx) * ca - (y - cy) * sa + cx,
             (x - cx) * sa + (y - cy) * ca + cy) for x, y in pts]


# ---------- 三个方向 ----------

def photo_holes(d, s, x0, y0, x1, y1, sun=True, ridge=True, scale=1.0):
    """在一张卡内部挖出"照片内容"（太阳圆 + 山脊），用负空间表现。

    scale < 1 时内容整体缩小 —— 同一套四格里靠它做出"不同的照片"。
    """
    if scale != 1.0:
        dw, dh = (x1 - x0) * (1 - scale) / 2.0, (y1 - y0) * (1 - scale) / 2.0
        x0, y0, x1, y1 = x0 + dw, y0 + dh, x1 - dw, y1 - dh
    w = x1 - x0
    h = y1 - y0
    if sun:
        disc(d, x0 + w * 0.70, y0 + h * 0.20, w * 0.115, 0)
    if ridge:
        base = y1 - h * 0.02
        pts = catmull_rom([
            (x0 + w * 0.08, base),
            (x0 + w * 0.30, y0 + h * 0.46),
            (x0 + w * 0.48, y0 + h * 0.68),
            (x0 + w * 0.68, y0 + h * 0.34),
            (x0 + w * 0.92, base),
        ], samples=24)
        hole(d, [(x0 + w * 0.08, base)] + pts + [(x0 + w * 0.92, base)])


def motif_cascade(size):
    """v1「一叠图」：三张照片阶梯错落叠放，最前面一张露出山与太阳。

    没有箭头：多张错落的照片本身就是"一堆图"的样子，正好是 App 在整理的东西。
    """
    m, d = new_mask(size)
    s = size / 1024.0
    card, step = 468, 96
    for i in range(3):
        x0 = 244 + i * step
        y0 = 116 + i * step
        fill(d, rounded_rect(x0 * s, y0 * s, (x0 + card) * s, (y0 + card) * s, 74 * s))
    x0, y0 = 244 + 2 * step, 116 + 2 * step
    photo_holes(d, s, x0 * s, y0 * s, (x0 + card) * s, (y0 + card) * s)
    return m


def motif_tray(size):
    """v2「装着图的收纳盒」：一个圆角托盘，两张图从盒里露出来。

    容器本身就是"收"的意象，不需要箭头。
    """
    m, d = new_mask(size)
    s = size / 1024.0
    left = rounded_rect(214 * s, 232 * s, 520 * s, 706 * s, 64 * s)
    left = rotate_pts(left, 367 * s, 706 * s, -12.0)
    right = rounded_rect(508 * s, 196 * s, 814 * s, 706 * s, 64 * s)
    right = rotate_pts(right, 661 * s, 706 * s, 9.0)
    fill(d, left)
    fill(d, right)
    fill(d, rounded_rect(140 * s, 596 * s, 884 * s, 884 * s, 100 * s))
    # 露在盒外的部分里挖出内容
    ridge = catmull_rom([
        (250 * s, 560 * s), (306 * s, 452 * s), (360 * s, 508 * s),
        (420 * s, 396 * s), (492 * s, 504 * s), (512 * s, 560 * s),
    ], samples=22)
    hole(d, [(250 * s, 560 * s)] + ridge + [(512 * s, 560 * s)])
    disc(d, 672 * s, 300 * s, 36 * s, 0)
    return m


def motif_grid(size):
    """v3「四格相册」：2×2 圆角格子，两格里是照片内容。

    图的"归拢/整理"感来自网格本身，同样不需要箭头。
    """
    m, d = new_mask(size)
    s = size / 1024.0
    gap, cell, x0, y0 = 74, 356, 130, 130
    for r in range(2):
        for c in range(2):
            cx0 = x0 + c * (cell + gap)
            cy0 = y0 + r * (cell + gap)
            fill(d, rounded_rect(cx0 * s, cy0 * s,
                                 (cx0 + cell) * s, (cy0 + cell) * s, 58 * s))
    # 左上：山；右上：太阳；下面两格留白
    photo_holes(d, s, (x0 + cell + gap) * s, y0 * s,
                (x0 + 2 * cell + gap) * s, (y0 + cell) * s, sun=True, ridge=False)
    photo_holes(d, s, x0 * s, y0 * s, (x0 + cell) * s, (y0 + cell) * s,
                sun=False, ridge=True)
    return m


def motif_frame(size):
    """v3「相框里的一张图」：外圆角相框 + 内圆角留白 + 框里一张照片。

    最克制的一版：不谈"搬"，只谈"图"。
    """
    m, d = new_mask(size)
    s = size / 1024.0
    fill(d, rounded_rect(146 * s, 146 * s, 878 * s, 878 * s, 132 * s))
    hole(d, rounded_rect(266 * s, 266 * s, 758 * s, 758 * s, 88 * s))
    fill(d, rounded_rect(314 * s, 314 * s, 710 * s, 710 * s, 66 * s))
    photo_holes(d, s, 314 * s, 314 * s, 710 * s, 710 * s)
    return m


def motif_grid_moving(size):
    """v2「四格相册（有一张正在移动）」：四格都是照片内容，右下那格歪着、还偏出了格位。

    表达"移动"不靠箭头，靠**错位**：三格安分地对齐，第四格倾斜 + 位移，
    一看就是"刚被搬进来 / 正在挪走"的那一张。
    """
    m, d = new_mask(size)
    s = size / 1024.0
    gap, cell, x0, y0 = 74, 356, 130, 130

    def cell_box(r, c):
        cx0 = x0 + c * (cell + gap)
        cy0 = y0 + r * (cell + gap)
        return cx0, cy0

    # 三格：对齐摆放，内容各不相同（山 / 太阳 / 山）
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx0, cy0 = cell_box(r, c)
        fill(d, rounded_rect(cx0 * s, cy0 * s, (cx0 + cell) * s, (cy0 + cell) * s, 58 * s))
    cx0, cy0 = cell_box(1, 1)
    # 左上：山（无太阳）  右上：太阳 + 山  左下：缩小的一张（远景）  右下：正在移动的那张
    photo_holes(d, s, (x0 + cell + gap) * s, y0 * s,
                (x0 + 2 * cell + gap) * s, (y0 + cell) * s, sun=True, ridge=True)
    photo_holes(d, s, x0 * s, y0 * s, (x0 + cell) * s, (y0 + cell) * s,
                sun=False, ridge=True)
    photo_holes(d, s, x0 * s, (y0 + cell + gap) * s,
                (x0 + cell) * s, (y0 + 2 * cell + gap) * s,
                sun=False, ridge=True, scale=0.72)

    # 第四格：整块（含照片内容）单独画好，再旋转 + 位移后并进来
    layer = Image.new("L", (size, size), 0)
    ld = ImageDraw.Draw(layer)
    fill(ld, rounded_rect(cx0 * s, cy0 * s, (cx0 + cell) * s, (cy0 + cell) * s, 58 * s))
    photo_holes(ld, s, cx0 * s, cy0 * s, (cx0 + cell) * s, (cy0 + cell) * s,
                sun=True, ridge=True)
    cxp, cyp = (cx0 + cell / 2) * s, (cy0 + cell / 2) * s
    layer = layer.rotate(-15.0, resample=Image.BICUBIC, center=(cxp, cyp))
    layer = ImageChops.offset(layer, int(-26 * s), int(-18 * s))
    m = ImageChops.lighter(m, layer)
    return m


VARIANTS = {
    "grid_moving": motif_grid_moving,
    "cascade": motif_cascade,
    "grid": motif_grid,
    "frame": motif_frame,
}


# ---------- 合成 ----------

def plate(size, motif_fn, supersample=SS):
    """完整底板图标：超椭圆底板 + 投影 + 白色剪影（高 0.72S）。"""
    big = size * supersample
    m = motif_fn(big)
    # 剪影缩放到 0.72 × 边长并居中
    target = int(big * 0.72)
    m = m.crop(m.getbbox() or (0, 0, big, big))
    w, h = m.size
    scale = target / max(w, h)
    m = m.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    sw, sh = m.size
    fg = Image.new("L", (big, big), 0)
    fg.paste(m, ((big - sw) // 2, (big - sh) // 2))

    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    plate_pts = rounded_rect(0, 0, big, big, 0.35 * big)
    d = ImageDraw.Draw(canvas)
    d.polygon(plate_pts, fill=BRAND)

    # 投影：偏移 0.024S、模糊 0.022S
    off = int(0.024 * big)
    blur = 0.022 * big
    shadow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    shadow.putalpha(fg.point(lambda v: int(v * 0.32)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    base = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    base.paste(shadow, (off, off), shadow)
    canvas = Image.alpha_composite(canvas, base)

    white = Image.new("RGBA", (big, big), WHITE)
    canvas = Image.alpha_composite(canvas, Image.composite(
        white, Image.new("RGBA", (big, big), (0, 0, 0, 0)), fg))

    return canvas.resize((size, size), Image.LANCZOS)


def adaptive_layer(size, motif_fn, shadow=True, supersample=SS):
    """自适应图标的图层：108dp 画布，剪影落在中央安全区（约 0.72 × 72dp）。"""
    big = size * supersample
    m = motif_fn(big)
    m = m.crop(m.getbbox() or (0, 0, big, big))
    w, h = m.size
    target = 0.72 * (big * 72.0 / 108.0)
    scale = target / max(w, h)
    m = m.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    layer = Image.new("L", (big, big), 0)
    layer.paste(m, ((big - m.size[0]) // 2, (big - m.size[1]) // 2))

    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    if shadow:
        k = 72.0 / 108.0
        off = int(0.024 * big * k)
        blur = 0.022 * big * k
        sh = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        sh.putalpha(layer.point(lambda v: int(v * 0.32)))
        sh = sh.filter(ImageFilter.GaussianBlur(blur))
        tmp = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        tmp.paste(sh, (off, off), sh)
        out = Image.alpha_composite(out, tmp)
    white = Image.new("RGBA", (big, big), WHITE)
    out = Image.alpha_composite(out, Image.composite(
        white, Image.new("RGBA", (big, big), (0, 0, 0, 0)), layer))
    return out.resize((size, size), Image.LANCZOS)


# Android 各密度下 108dp 画布对应的像素
ANDROID_DENSITIES = {
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}


def write_android_assets(variant):
    """把选定的剪影写成 Android 资源：自适应前景层 + 单色层 + 完整底板图。"""
    fn = VARIANTS[variant]
    res = os.path.join(ROOT, "app", "src", "main", "res")
    for dpi, px in ANDROID_DENSITIES.items():
        d = os.path.join(res, "mipmap-" + dpi)
        os.makedirs(d, exist_ok=True)
        adaptive_layer(px, fn).save(os.path.join(d, "ic_launcher_foreground.png"))
        adaptive_layer(px, fn, shadow=False).save(os.path.join(d, "ic_launcher_monochrome.png"))
    os.makedirs(OUT, exist_ok=True)
    for px in (512, 1024):
        plate(px, fn).save(os.path.join(OUT, "ic_launcher_%d.png" % px))
    print("已写入 Android 资源：%s（前景层/单色层 5 个密度 + 512/1024 完整图）" % variant)


def poly_to_path(points, precision=2):
    """把多边形点列转成 Android vector 的 pathData（闭合）。"""
    fmt = "%%.%df,%%.%df" % (precision, precision)
    parts = ["M" + (fmt % points[0])]
    for p in points[1:]:
        parts.append("L" + (fmt % p))
    parts.append("Z")
    return " ".join(parts)


def write_notify_vector():
    """状态栏/通知栏的小图标：24dp 简化剪影（2×2 圆角格 + 右下格倾斜）。

    通知小图标在状态栏里只有十几 dp，格内那座山会糊成一团，所以只保留"形"。
    """
    box, gap, r = 8.0, 2.0, 2.4
    x0, y0 = 3.0, 3.0
    paths = []
    for (rr, cc) in ((0, 0), (0, 1), (1, 0)):
        cx = x0 + cc * (box + gap)
        cy = y0 + rr * (box + gap)
        paths.append(poly_to_path(rounded_rect(cx, cy, cx + box, cy + box, r, steps=18)))
    # 右下那格：倾斜 + 轻微位移，和主图标一个意思
    cx = x0 + (box + gap)
    cy = y0 + (box + gap)
    cell = rounded_rect(cx, cy, cx + box, cy + box, r, steps=18)
    cell = rotate_pts(cell, cx + box / 2.0, cy + box / 2.0, -14.0)
    cell = [(px - 0.55, py - 0.4) for px, py in cell]
    paths.append(poly_to_path(cell))

    body = "\n".join(
        '    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="%s" />' % p
        for p in paths
    )
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n"
        "  通知栏/状态栏的小图标：桌面图标的 24dp 简化版（四格相册，右下那格倾斜 = 正在移动）。\n"
        "  由 tools/make_icon.py 生成，与桌面图标同一套几何参数；这个尺寸下格内的山和太阳会糊掉，\n"
        "  所以只保留格子本身。\n"
        "-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24">\n'
        "%s\n"
        "</vector>\n" % body
    )
    out = os.path.join(ROOT, "app", "src", "main", "res", "drawable", "ic_notify.xml")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(xml)

    print("已写入 drawable/ic_notify.xml")


def main():
    args = sys.argv[1:]
    if args and args[0] == "--android":
        write_android_assets(args[1] if len(args) > 1 else "grid_moving")
        write_notify_vector()
        return
    names = args or list(VARIANTS)
    os.makedirs(OUT, exist_ok=True)
    for name in names:
        fn = VARIANTS[name]
        for px in (512, 1024):
            plate(px, fn).save(os.path.join(OUT, "preview-%s-%d.png" % (name, px)))
        print("生成：%s（512/1024 预览）" % name)


if __name__ == "__main__":
    main()
