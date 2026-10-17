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

BRAND = (0x66, 0xBB, 0x6A, 255)  # 浅绿底板（用户指定）；白色剪影 + 内投影保证小尺寸下仍清楚
WHITE = (255, 255, 255, 255)
PLATE_SHADOW = (10, 16, 48)      # 底板外投影的颜色（深藏蓝，和 PicSieve 一致）

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


def band_polygon(back, front, g, steps=40):
    """后面那张卡减去"前面卡外扩 g"之后剩下的 L 形带（前面的卡在左下方）。

    用几何直接算出轮廓，比在像素上挖洞干净：凸角是 back 的圆角，
    内凹角是外扩后 front 的圆角（所以缝隙宽度处处都是 g）。
    """
    bx0, by0, bx1, by1, br = back
    fx0, fy0, fx1, fy1, fr = front
    ex0, ey0, ex1, ey1, er = fx0 - g, fy0 - g, fx1 + g, fy1 + g, fr + g
    if not (ex0 <= bx0 and ey1 >= by1 and ey0 > by0 and ex1 < bx1):
        raise ValueError("叠放位置超出假设：前卡外扩后应盖住后卡的左下角")
    pts = []
    pts += superellipse_arc(bx0 + br, by0 + br, br, math.pi, 1.5 * math.pi, steps=steps)
    pts += superellipse_arc(bx1 - br, by0 + br, br, 1.5 * math.pi, 2.0 * math.pi, steps=steps)
    pts += superellipse_arc(bx1 - br, by1 - br, br, 0.0, 0.5 * math.pi, steps=steps)
    pts.append((ex1, by1))
    pts += superellipse_arc(ex1 - er, ey0 + er, er, 0.0, -0.5 * math.pi, steps=steps)
    pts.append((bx0, ey0))
    return pts


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


STACK_FRONT = (170, 340, 730, 900, 132)   # 前一张（左下，完整）
STACK_BACK = (400, 110, 960, 670, 132)    # 后一张（右上，露出一道厚边）
STACK_GAP = 46                            # 两张之间的空隙


def motif_stack(size):
    """「一叠图」：两张一样的图错开叠放，后面那张露出一道厚边。

    只有两个形状、一条等宽的缝，没有任何小细节 —— 缩到 40px 也不糊。
    叠压关系靠"缝"表达：缝的宽度处处等于 STACK_GAP（用几何算出来的，
    不是把像素挖掉），内凹角是外扩后前卡的圆角。
    """
    m, d = new_mask(size)
    s = size / 1024.0
    band = band_polygon(STACK_BACK, STACK_FRONT, STACK_GAP)
    d.polygon([(x * s, y * s) for x, y in band], fill=255)
    x0, y0, x1, y1, r = STACK_FRONT
    d.polygon(rounded_rect(x0 * s, y0 * s, x1 * s, y1 * s, r * s), fill=255)
    return m


VARIANTS = {
    "stack": motif_stack,
    "grid_moving": motif_grid_moving,
    "cascade": motif_cascade,
    "grid": motif_grid,
    "frame": motif_frame,
}


# ---------- 合成 ----------

def plate(size, motif_fn, supersample=SS, frac=0.72, plate_shadow=True, pad_ratio=0.075):
    """完整底板图标：超椭圆底板（带外投影）+ 剪影（带内投影，高 frac × 边长）。

    两层阴影，和 PicSieve / Lumen 那两个图标一致：
      底板外投影：偏移 (0.0024, 0.024)S、模糊 0.022S、不透明度 0.42
      剪影内投影：偏移 0.024S、模糊 0.022S、不透明度 0.42
    外投影会被画布裁掉，所以画布四周留 pad_ratio 的边（输出尺寸不变，底板相应变小）。
    """
    big = size * supersample
    pad = int(big * pad_ratio) if plate_shadow else 0
    side = big + 2 * pad

    m = motif_fn(big)
    # 剪影缩放到 frac × 边长并居中
    target = int(big * frac)
    m = m.crop(m.getbbox() or (0, 0, big, big))
    w, h = m.size
    scale = target / max(w, h)
    m = m.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    sw, sh = m.size
    fg = Image.new("L", (side, side), 0)
    fg.paste(m, (pad + (big - sw) // 2, pad + (big - sh) // 2))

    blur = 0.022 * big
    plate_pts = rounded_rect(pad, pad, pad + big, pad + big, 0.35 * big)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))

    if plate_shadow:
        sil = Image.new("L", (side, side), 0)
        ImageDraw.Draw(sil).polygon(plate_pts, fill=255)
        ps = Image.new("RGBA", (side, side), PLATE_SHADOW + (255,))
        ps.putalpha(sil.point(lambda v: int(v * 0.42)))
        ps = ps.filter(ImageFilter.GaussianBlur(blur))
        holder = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        holder.paste(ps, (int(0.0024 * big), int(0.024 * big)), ps)
        canvas = Image.alpha_composite(canvas, holder)

    ImageDraw.Draw(canvas).polygon(plate_pts, fill=BRAND)

    # 剪影的投影：偏移 0.024S、模糊 0.022S
    off = int(0.024 * big)
    shadow = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    shadow.putalpha(fg.point(lambda v: int(v * 0.42)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    base = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    base.paste(shadow, (off, off), shadow)
    canvas = Image.alpha_composite(canvas, base)

    white = Image.new("RGBA", (side, side), WHITE)
    canvas = Image.alpha_composite(canvas, Image.composite(
        white, Image.new("RGBA", (side, side), (0, 0, 0, 0)), fg))

    return canvas.resize((size, size), Image.LANCZOS)


def adaptive_layer(size, motif_fn, shadow=True, supersample=SS, frac=0.68):
    """自适应图标的图层：108dp 画布，剪影落在中央安全区。

    frac 是剪影相对「桌面可见区（72dp）」的比例：0.68 时正方形剪影的对角半径
    约 34.6dp < 36dp，圆形遮罩也裁不到角；和完整底板预览用的是同一个比例。
    """
    big = size * supersample
    m = motif_fn(big)
    m = m.crop(m.getbbox() or (0, 0, big, big))
    w, h = m.size
    target = frac * (big * 72.0 / 108.0)
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


def write_notify_vector(variant="stack"):
    """状态栏/通知栏的小图标：24dp 的简化剪影，和桌面图标同一套几何。

    「一叠图」在 24dp 下正好只有两个形状（后卡露出的 L 形边 + 前卡），
    不做任何简化也不会糊，所以这里直接把桌面图标的几何等比缩到 24dp。
    """
    if variant != "stack":
        raise ValueError("通知小图标目前只实现了一叠图（stack）")
    src = 1024.0
    k = 20.0 / (STACK_BACK[2] - STACK_FRONT[0])   # 剪影最宽处缩到 20dp
    ox, oy = STACK_FRONT[0], STACK_BACK[1]        # 剪影左上角
    off = (24.0 - (STACK_BACK[2] - STACK_FRONT[0]) * k) / 2.0

    def tf(pts):
        return [((x - ox) * k + off, (y - oy) * k + off) for x, y in pts]

    band = band_polygon(STACK_BACK, STACK_FRONT, STACK_GAP)
    bx0, by0, bx1, by1, br = STACK_FRONT
    paths = [
        poly_to_path(tf(band)),
        poly_to_path(tf(rounded_rect(bx0, by0, bx1, by1, br))),
    ]
    body = "\n".join(
        '    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="%s" />' % p
        for p in paths
    )
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n"
        "  通知栏/状态栏的小图标：桌面图标（一叠图）的 24dp 版。\n"
        "  由 tools/make_icon.py 生成，几何与桌面图标完全一致：后卡露出的 L 形边 + 完整的前卡，\n"
        "  中间那道缝就是两张图的分界。\n"
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
        write_android_assets(args[1] if len(args) > 1 else "stack")
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
