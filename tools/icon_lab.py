# -*- coding: utf-8 -*-
"""图标设计实验台（不参与打包）：把候选方案并排渲染成对比图，肉眼挑。

复用 tools/make_icon.py 的底板/投影/缩放逻辑，只换中间那层剪影。
用法：python tools/icon_lab.py [方案名 ...]
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_icon as M  # noqa: E402

OUT = os.path.join(M.ROOT, "docs", "icon")
S = 1024.0  # 设计网格


def mask(size):
    m = Image.new("L", (size, size), 0)
    return m, ImageDraw.Draw(m)


def box(x0, y0, x1, y1, r):
    return (x0, y0, x1, y1, r)


def card(d, s, b, value=255):
    """一张圆角卡片。b = (x0, y0, x1, y1, r)，坐标是 1024 网格。"""
    x0, y0, x1, y1, r = b
    d.polygon(M.rounded_rect(x0 * s, y0 * s, x1 * s, y1 * s, r * s), fill=value)


def gap_around(m, s, b, g):
    """在卡片外围留一圈空（蓝底），用于让紧邻的白色形状彼此分开。"""
    x0, y0, x1, y1, r = b
    ring = Image.new("L", m.size, 0)
    rd = ImageDraw.Draw(ring)
    rd.polygon(M.rounded_rect((x0 - g) * s, (y0 - g) * s,
                              (x1 + g) * s, (y1 + g) * s, (r + g) * s), fill=255)
    return ImageChops.subtract(m, ring)


def photo_mark(d, s, b, sun=True, amp=0.62, base=0.88, rid=1.0):
    """卡片内的"照片内容"：一条粗山脊 + 一个太阳，用负空间挖出来。

    amp 控制山有多高（占卡片高度的比例），数值越大越醒目。
    4 格里塞不下这个——一格里的山在 48px 下只剩几个像素。
    """
    x0, y0, x1, y1, _ = b
    w, h = (x1 - x0) * s, (y1 - y0) * s
    x0, y0 = x0 * s, y0 * s
    top = y0 + h * (1 - amp)
    bot = y0 + h * base
    pts = M.catmull_rom([
        (x0 + w * 0.06, bot),
        (x0 + w * 0.28, top + (bot - top) * 0.55 * rid),
        (x0 + w * 0.44, top + (bot - top) * 0.08 * rid),
        (x0 + w * 0.64, top + (bot - top) * 0.72 * rid),
        (x0 + w * 0.94, bot),
    ], samples=26)
    d.polygon([(x0 + w * 0.06, bot)] + pts + [(x0 + w * 0.94, bot)], fill=0)
    if sun:
        r = w * 0.105
        cx, cy = x0 + w * 0.755, y0 + h * (1 - amp) * 0.52
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=0)


def A_stack3(size):
    """A 一叠三张：斜向错位，彼此留蓝缝，最前面一张有照片内容。"""
    m, d = mask(size)
    s = size / S
    front = box(180, 320, 700, 840, 104)
    card(d, s, box(440, 60, 960, 580, 104))
    m = gap_around(m, s, box(310, 190, 830, 710, 104), 34)
    card(d, s, box(310, 190, 830, 710, 104))
    m = gap_around(m, s, front, 34)
    d = ImageDraw.Draw(m)
    card(d, s, front)
    photo_mark(d, s, front, amp=0.50, base=0.86)
    return m


def B_window_out(size):
    """B 取走一张：相册右上角缺一格，那一格正飘出去（无箭头，靠"缺 + 飘"）。"""
    m, d = mask(size)
    s = size / S
    b = box(200, 240, 700, 740, 104)
    card(d, s, b)
    bite = box(520, 240, 700, 420, 48)
    x0, y0, x1, y1, r = bite
    d.polygon(M.rounded_rect(x0 * s, y0 * s, x1 * s, y1 * s, r * s), fill=0)
    card(d, s, box(740, 60, 920, 240, 48))
    return m


def C_grid_out(size):
    """C 四格出一格：三格原位，右下那格整块移出格位（不旋转），空槽留着。"""
    m, d = mask(size)
    s = size / S
    cell, gap, x0, y0 = 320, 96, 104, 104
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx, cy = x0 + c * (cell + gap), y0 + r * (cell + gap)
        card(d, s, box(cx, cy, cx + cell, cy + cell, 88))
    card(d, s, box(x0 + cell + gap + 140, y0 + cell + gap + 140,
                   x0 + 2 * cell + gap + 140, y0 + 2 * cell + gap + 140, 88))
    return m


def D_grid_solid(size):
    """D 四格（实心版）：只保留网格本身，格内不留细节 —— 现行方案的去细节版。"""
    m, d = mask(size)
    s = size / S
    cell, gap, x0, y0 = 344, 88, 124, 124
    for r in range(2):
        for c in range(2):
            cx, cy = x0 + c * (cell + gap), y0 + r * (cell + gap)
            card(d, s, box(cx, cy, cx + cell, cy + cell, 92))
    return m


def E_photo_bold(size):
    """E 一张大照片：一个卡 + 醒目的山与太阳（负空间），其余全空。"""
    m, d = mask(size)
    s = size / S
    b = box(176, 176, 848, 848, 164)
    card(d, s, b)
    photo_mark(d, s, b, amp=0.56, base=0.85)
    return m


def F_two_cards(size):
    """F 后面还有一张：前卡带内容，后卡斜后方露出厚厚一道边。"""
    m, d = mask(size)
    s = size / S
    front = box(232, 300, 792, 860, 112)
    card(d, s, box(392, 140, 952, 700, 112))
    m = gap_around(m, s, front, 40)
    d = ImageDraw.Draw(m)
    card(d, s, front)
    photo_mark(d, s, front, amp=0.50, base=0.86)
    return m


def landscape(d, s, b, mode="sun", k=0.22):
    """卡片内的"照片内容"，只用一个负空间大形状，力求干净。

    mode="sun"  ：从下边缘升起的一个半圆（日出）
    mode="peak" ：两座柔和的山（山谷）
    """
    x0, y0, x1, y1, _ = b
    X0, Y0, X1, Y1 = x0 * s, y0 * s, x1 * s, y1 * s
    w, h = X1 - X0, Y1 - Y0
    if mode == "sun":
        r = w * k
        d.ellipse([X0 + w / 2 - r, Y1 - r * 1.55, X0 + w / 2 + r, Y1 + r * 0.45], fill=0)
        d.rectangle([X0 + w / 2 - r, Y1 - r * 0.9, X0 + w / 2 + r, Y1], fill=0)
        return
    base = Y1 - h * 0.10
    pts = M.catmull_rom([
        (X0 + w * 0.04, base),
        (X0 + w * 0.24, Y0 + h * 0.44),
        (X0 + w * 0.40, Y0 + h * 0.72),
        (X0 + w * 0.62, Y0 + h * 0.30),
        (X0 + w * 0.96, base),
    ], samples=30)
    d.polygon([(X0 + w * 0.04, base)] + pts + [(X0 + w * 0.96, base)], fill=0)


def E2_photo_sun(size):
    """E2 一张大照片（日出）：卡 + 一个半圆，只有两个形状。"""
    m, d = mask(size)
    s = size / S
    b = box(176, 176, 848, 848, 164)
    card(d, s, b)
    landscape(d, s, b, "sun")
    return m


def E3_photo_peaks(size):
    """E3 一张大照片（山谷）：卡 + 两座柔和的山。"""
    m, d = mask(size)
    s = size / S
    b = box(176, 176, 848, 848, 164)
    card(d, s, b)
    landscape(d, s, b, "peak")
    return m


def I2_sun_plus(size):
    """I2 大照片 + 右上角一小张：一大一小，"还有别的图"。"""
    m, d = mask(size)
    s = size / S
    b = box(176, 288, 764, 876, 148)
    card(d, s, b)
    landscape(d, s, b, "sun")
    card(d, s, box(760, 148, 948, 336, 52))
    return m


def G_photo_tilt(size):
    """G 大照片 + 右下压着歪放的一张：压着的关系靠蓝缝和倾斜。"""
    m, d = mask(size)
    s = size / S
    b = box(176, 176, 760, 760, 146)
    card(d, s, b)
    landscape(d, s, b, "sun", k=0.20)
    small = box(596, 640, 930, 974, 60)
    small = rotate_box(small, -13.0)
    ring = Image.new("L", m.size, 0)
    rd = ImageDraw.Draw(ring)
    rd.polygon([(x * s, y * s) for x, y in small], fill=255)
    m = ImageChops.subtract(m, ring)
    d = ImageDraw.Draw(m)
    d.polygon([(x * s, y * s) for x, y in small], fill=255)
    return m


def rotate_box(b, deg):
    x0, y0, x1, y1, r = b
    pts = M.rounded_rect(x0, y0, x1, y1, r)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    return M.rotate_pts(pts, cx, cy, deg)


def two_clean(size, deg=0.0, off=170, g=44, front=520):
    """两张干净的卡斜向叠放：后面那张露出一道厚边（无内容、无折角）。"""
    m, d = mask(size)
    s = size / S
    fx0, fy0 = 200, 200
    front_b = box(fx0, fy0, fx0 + front, fy0 + front, 112)
    back_x0, back_y0 = fx0 + off, fy0 - off
    back_b = box(back_x0, back_y0, back_x0 + front, back_y0 + front, 112)
    back_pts = [p for p in M.rounded_rect(back_b[0], back_b[1], back_b[2], back_b[3], back_b[4])]
    if deg:
        cx, cy = back_x0 + front / 2.0, back_y0 + front / 2.0
        back_pts = M.rotate_pts(back_pts, cx, cy, deg)
    d.polygon([(x * s, y * s) for x, y in back_pts], fill=255)
    m = ImageChops.subtract(m, _ring(m.size, s, front_b, g))
    d = ImageDraw.Draw(m)
    card(d, s, front_b)
    return m


def _ring(size_px, s, b, g):
    x0, y0, x1, y1, r = b
    ring = Image.new("L", size_px, 0)
    ImageDraw.Draw(ring).polygon(
        M.rounded_rect((x0 - g) * s, (y0 - g) * s, (x1 + g) * s, (y1 + g) * s, (r + g) * s),
        fill=255)
    return ring


def A3_two_clean(size):
    return two_clean(size)


def A4_two_tilt(size):
    return two_clean(size, deg=-9.0, off=150)


def A5_three_clean(size):
    """三张干净的卡错开斜排，彼此不挨着 —— "散落的图"。"""
    m, d = mask(size)
    s = size / S
    for i, sz in enumerate((330, 400, 470)):
        x0 = 120 + i * 210
        y0 = 700 - i * 250
        card(d, s, box(x0, y0, x0 + sz, y0 + sz, sz * 0.22))
    return m


def D2_grid_tilt_clean(size):
    """四格（纯色版）：右下那格只做倾斜 + 轻微位移，格内不留任何细节。"""
    m, d = mask(size)
    s = size / S
    cell, gap, x0, y0 = 344, 88, 124, 124
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx, cy = x0 + c * (cell + gap), y0 + r * (cell + gap)
        card(d, s, box(cx, cy, cx + cell, cy + cell, 92))
    cx, cy = x0 + cell + gap, y0 + cell + gap
    pts = M.rotate_pts(M.rounded_rect(cx, cy, cx + cell, cy + cell, 92),
                       cx + cell / 2.0, cy + cell / 2.0, -14.0)
    d.polygon([(x * s, y * s) for x, y in pts], fill=255)
    return m


def C2_grid_out_far(size):
    """四格：右下那格整块搬到格子外面，原位留空槽（不旋转）。"""
    m, d = mask(size)
    s = size / S
    cell, gap, x0, y0 = 316, 92, 92, 92
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx, cy = x0 + c * (cell + gap), y0 + r * (cell + gap)
        card(d, s, box(cx, cy, cx + cell, cy + cell, 88))
    mx, my = x0 + cell + gap + 200, y0 + cell + gap + 200
    card(d, s, box(mx, my, mx + cell, my + cell, 88))
    return m


def D6_grid_small_out(size):
    """四格：右下格变小并飘到格子外（远小近大 = 距离感），原位留空槽。"""
    m, d = mask(size)
    s = size / S
    cell, gap, x0, y0 = 330, 88, 108, 108
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx, cy = x0 + c * (cell + gap), y0 + r * (cell + gap)
        card(d, s, box(cx, cy, cx + cell, cy + cell, 90))
    mx, my = x0 + cell + gap + 176, y0 + cell + gap + 176
    card(d, s, box(mx, my, mx + 208, my + 208, 58))
    return m


def A6_card_plus(size):
    """一大张 + 右上角一小张（都不带内容）：构图有大小对比和方向。"""
    m, d = mask(size)
    s = size / S
    card(d, s, box(180, 300, 760, 880, 150))
    card(d, s, box(770, 150, 950, 330, 54))
    return m


def F1_grid_slide(size):
    """四格：右下那格沿对角线滑出半个格位，原位留空槽（不旋转、不倾斜）。"""
    m, d = mask(size)
    s = size / S
    cell, gap, x0, y0 = 330, 74, 100, 100
    step = cell + gap
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx, cy = x0 + c * step, y0 + r * step
        card(d, s, box(cx, cy, cx + cell, cy + cell, 90))
    mx, my = x0 + step + step / 2.0, y0 + step + step / 2.0
    card(d, s, box(mx, my, mx + cell, my + cell, 90))
    return m


def album_pull(size, tilt=0.0):
    """相册里插着一张图：一张图从册子顶上探出来一大截（探出=被拾起）。"""
    m, d = mask(size)
    s = size / S
    b = box(170, 350, 854, 1014, 128)
    card(d, s, b)
    ph = box(470, 110, 800, 440, 70)
    pts = M.rotate_pts(M.rounded_rect(*[v for v in ph]), 635, 275, tilt)
    ring = Image.new("L", m.size, 0)
    ImageDraw.Draw(ring).polygon(
        M.rotate_pts(M.rounded_rect(426, 66, 844, 484, 114), 635, 275, tilt), fill=255)
    m = ImageChops.subtract(m, ring)
    d = ImageDraw.Draw(m)
    d.polygon([(x * s, y * s) for x, y in pts], fill=255)
    return m


def F2_album_pull(size):
    return album_pull(size)


def F2b_album_pull_tilt(size):
    return album_pull(size, tilt=-9.0)


def F3_two_big(size):
    """两张卡斜向叠放（精修版）：厚边 + 均匀蓝缝，不带任何内容。"""
    m, d = mask(size)
    s = size / S
    front = box(170, 290, 790, 910, 140)
    card(d, s, box(400, 60, 1020, 680, 140))
    m = ImageChops.subtract(m, _ring(m.size, s, front, 44))
    d = ImageDraw.Draw(m)
    card(d, s, front)
    return m


def F5_flow(size, grow=True):
    """一串图沿对角线排开：等大 = 一列，渐大 = 汇拢（像漏斗的收口）。"""
    m, d = mask(size)
    s = size / S
    if grow:
        sizes, gap, px, py = (220, 300, 380), 50, 12, 12
    else:
        sizes, gap, px, py = (280, 280, 280), 60, 32, 32
    for sz in sizes:
        card(d, s, box(px, py, px + sz, py + sz, sz * 0.26))
        px += sz + gap
        py += sz + gap
    return m


def F5b_flow_even(size):
    return F5_flow(size, grow=False)


def F4_basket(size):
    """筐里装着图：一只梯形筐 + 一道筐口，两张图从筐里探出头。"""
    m, d = mask(size)
    s = size / S
    body = M.rounded_polygon([(150, 470), (874, 470), (784, 940), (240, 940)], 86)
    fill = d.polygon
    fill([(x * s, y * s) for x, y in body], fill=255)
    for b in (box(250, 150, 560, 560, 84), box(500, 240, 790, 560, 74)):
        d.polygon(M.rounded_rect(*[v * s for v in b]), fill=255)
    m = ImageChops.subtract(m, _ring(m.size, s, box(126, 446, 898, 604, 70), 40))
    d = ImageDraw.Draw(m)
    fill([(x * s, y * s) for x, y in body], fill=255)
    d.polygon(M.rounded_rect(126 * s, 446 * s, 898 * s, 604 * s, 70 * s), fill=255)
    return m


def grid_motion(kind, size):
    """四格的几种"移动"表达，参数统一：格 340、缝 78、圆角 0.30 格。"""
    m, d = mask(size)
    s = size / S
    cell, gap, x0, y0 = 340, 78, 92, 92
    step = cell + gap
    r = cell * 0.30

    def put(cx, cy, sz=None, rr=None):
        sz = sz or cell
        card(d, s, box(cx, cy, cx + sz, cy + sz, rr or r))

    if kind == "stagger":
        # 下一行整排右移半格 —— 像刚被推了一下，但仍是整齐的棋盘格
        put(x0 + step, y0)
        put(x0 + 2 * step, y0)
        put(x0, y0 + step)
        put(x0 + step, y0 + step)
        return m
    if kind == "shear":
        # 四格沿对角线逐格偏移，形成斜向的"流动"
        for i in range(4):
            put(x0 + i * 92, y0 + i * 92)
        return m
    if kind == "slide":
        # 右下那格正滑出：只走了一半格位，仍与邻格错开
        for (rr, cc) in ((0, 0), (0, 1), (1, 0)):
            put(x0 + cc * step, y0 + rr * step)
        put(x0 + step + step * 0.62, y0 + step + step * 0.62)
        return m
    if kind == "slide_trace":
        # 滑出 + 原位留一个虚线感的细框（大图上才有，小图上自然消失）
        for (rr, cc) in ((0, 0), (0, 1), (1, 0)):
            put(x0 + cc * step, y0 + rr * step)
        put(x0 + step + step * 0.55, y0 + step + step * 0.55)
        tr = 26
        d.polygon(M.rounded_rect((x0 + step) * s, (y0 + step) * s,
                                 (x0 + step + cell) * s, (y0 + step + cell) * s, r * s),
                  outline=255, width=int(tr * s))
        return m
    raise SystemExit("未知 kind：%s" % kind)


def G1_stagger(size):
    return grid_motion("stagger", size)


def G2_shear(size):
    return grid_motion("shear", size)


def G3_slide(size):
    return grid_motion("slide", size)


def G4_slide_trace(size):
    return grid_motion("slide_trace", size)


GRID = dict(cell=300, gap=76, x0=100, y0=100)


def grid_cells(d, s, skip_last=False):
    """2×2 干净格子：格 300、缝 76，整体 676 见方。"""
    g = GRID
    step = g["cell"] + g["gap"]
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx, cy = g["x0"] + c * step, g["y0"] + r * step
        card(d, s, box(cx, cy, cx + g["cell"], cy + g["cell"], 88))
    if skip_last:
        return
    cx, cy = g["x0"] + step, g["y0"] + step
    card(d, s, box(cx, cy, cx + g["cell"], cy + g["cell"], 88))


def H1_tilt(size):
    """右下那格斜着挪出去半格（不碰邻格）。"""
    m, d = mask(size)
    s = size / S
    g = GRID
    step = g["cell"] + g["gap"]
    grid_cells(d, s)
    cx, cy = g["x0"] + step + 190, g["y0"] + step + 190
    pts = M.rotate_pts(M.rounded_rect(cx, cy, cx + g["cell"], cy + g["cell"], 88),
                       cx + g["cell"] / 2.0, cy + g["cell"] / 2.0, -12.0)
    d.polygon([(x * s, y * s) for x, y in pts], fill=255)
    return m


def H2_out(size):
    """右下那格正正方方地平移出格位，原位留空。"""
    m, d = mask(size)
    s = size / S
    g = GRID
    step = g["cell"] + g["gap"]
    grid_cells(d, s)
    cx, cy = g["x0"] + step + 233, g["y0"] + step + 233
    card(d, s, box(cx, cy, cx + g["cell"], cy + g["cell"], 88))
    return m


def H3_small(size):
    """右下那格缩小并飘出去（远小近大）。"""
    m, d = mask(size)
    s = size / S
    g = GRID
    step = g["cell"] + g["gap"]
    grid_cells(d, s)
    sz = g["cell"] * 0.84
    cx, cy = g["x0"] + step + 225, g["y0"] + step + 225
    card(d, s, box(cx, cy, cx + sz, cy + sz, sz * 0.29))
    return m


def H4_stagger(size):
    """上一排不动，下一排整排右移半格 —— 整齐的错缝（砖墙）。"""
    m, d = mask(size)
    s = size / S
    cell, gap = 300, 76
    half = (cell + gap) / 2.0
    for (cx, cy) in ((60, 100), (436, 100), (60 + half, 476), (436 + half, 476)):
        card(d, s, box(cx, cy, cx + cell, cy + cell, 88))
    return m


def H5_over(size):
    """右下那格正往格子里滑（压在邻格前面），原位留空。"""
    m, d = mask(size)
    s = size / S
    g = GRID
    step = g["cell"] + g["gap"]
    grid_cells(d, s)
    cell = g["cell"]
    cx, cy = g["x0"] + step - 150, g["y0"] + step - 150
    m = ImageChops.subtract(m, _ring(m.size, s, box(cx, cy, cx + cell, cy + cell, 88), 40))
    d = ImageDraw.Draw(m)
    card(d, s, box(cx, cy, cx + cell, cy + cell, 88))
    return m


def H0_current(size):
    return M.motif_grid_moving(size)


def stack_pair(size, dx=0, dy=-230, gap=44, front=600, r=140):
    """两张一样大的卡叠放：后面那张沿 (dx,dy) 露出一道厚边。"""
    m, d = mask(size)
    s = size / S
    fx0, fy0 = 170, 340
    fb = box(fx0, fy0, fx0 + front, fy0 + front, r)
    card(d, s, box(fx0 + dx, fy0 + dy, fx0 + dx + front, fy0 + dy + front, r))
    m = ImageChops.subtract(m, _ring(m.size, s, fb, gap))
    d = ImageDraw.Draw(m)
    card(d, s, fb)
    return m


def S1_stack_up(size):
    return stack_pair(size, dx=0, dy=-230)


def S2_stack_diag(size):
    return stack_pair(size, dx=230, dy=-230)


def S3_stack_right(size):
    return stack_pair(size, dx=230, dy=0)


def grid4(size, gap=88, rad=0.29, cell=300):
    """干净的 2×2：四格，等缝，无内容。"""
    m, d = mask(size)
    s = size / S
    x0 = y0 = (1024 - (2 * cell + gap)) / 2.0
    for r in range(2):
        for c in range(2):
            cx, cy = x0 + c * (cell + gap), y0 + r * (cell + gap)
            card(d, s, box(cx, cy, cx + cell, cy + cell, cell * rad))
    return m


def grid4_out(size, gap=88, rad=0.29, cell=300, shift=0.62):
    """四格，右下那格沿对角线整体搬出半个格位（原位留空槽，不旋转）。"""
    m, d = mask(size)
    s = size / S
    step = cell + gap
    x0 = y0 = 60.0
    for (r, c) in ((0, 0), (0, 1), (1, 0)):
        cx, cy = x0 + c * step, y0 + r * step
        card(d, s, box(cx, cy, cx + cell, cy + cell, cell * rad))
    off = step * shift
    card(d, s, box(x0 + step + off, y0 + step + off,
                   x0 + step + off + cell, y0 + step + off + cell, cell * rad))
    return m


def OPT1(size):
    """方案一 四格相册（干净版）"""
    return grid4(size)


def OPT2(size):
    """方案二 一叠图"""
    return stack_pair(size, dx=230, dy=-230, gap=46, front=560, r=132)


def OPT3(size):
    """方案三 一张正在搬出"""
    return grid4_out(size)


def folder_base(d, s, body=(210, 360, 814, 900, 116), tab=(210, 262, 486, 420, 62)):
    card(d, s, box(*tab))
    card(d, s, box(*body))


def FD1(size):
    """文件夹 + 一张插在里面的图（图从右侧探出头）。"""
    m, d = mask(size)
    s = size / S
    ph = box(500, 150, 812, 462, 70)
    card(d, s, ph)
    m = ImageChops.subtract(m, _ring(m.size, s, ph, 46))
    d = ImageDraw.Draw(m)
    folder_base(d, s)
    return m


def FD2(size):
    """文件夹 + 两张插在里面的图（一前一后，都探头）。"""
    m, d = mask(size)
    s = size / S
    back = box(300, 120, 560, 380, 60)
    front = box(430, 168, 700, 438, 64)
    card(d, s, back)
    m = ImageChops.subtract(m, _ring(m.size, s, front, 44))
    d = ImageDraw.Draw(m)
    card(d, s, front)
    m = ImageChops.subtract(m, _ring(m.size, s, box(430, 168, 700, 438, 64), 44))
    d = ImageDraw.Draw(m)
    card(d, s, front)
    folder_base(d, s)
    return m


def OPT4(size):
    """方案四 收进文件夹"""
    return FD1(size)


VARIANTS = {
    "H0_current": H0_current,
    "H2_out": H2_out,
    "D_grid_solid": D_grid_solid,
    "S2_stack_diag": S2_stack_diag,
    "OPT1": OPT1,
    "OPT2": OPT2,
    "OPT3": OPT3,
    "OPT4": OPT4,
    "FD2": FD2,
}


# ---------- 参照图（用户点名的两个好例子） ----------

def ref_picsieve():
    return Image.open(r"D:\code\PicSieve\src-tauri\icons\icon.png").convert("RGBA")


def ref_lumen():
    """Komikku/Lumen：浅色底板 + 自适应前景层（432 = 108dp）拼回一张。"""
    fg = Image.open(r"D:\code\Komikku\app\src\main\res\drawable-xxxhdpi"
                    r"\ic_launcher_foreground.png").convert("RGBA")
    base = Image.new("RGBA", fg.size, (0xF7, 0xF3, 0xEC, 255))
    return Image.alpha_composite(base, fg)


# ---------- 对比图 ----------
COLS = (400, 116, 64, 44)   # 每行渲染的尺寸：大图 / 中图 / 小图 / 极小图
PAD = 26


def sheet(names, with_refs=True, bg=(238, 238, 240, 255), frac=0.72):
    rows = []
    if with_refs:
        rows.append(("PicSieve(参照)", ref_picsieve().resize((512, 512), Image.LANCZOS)))
        rows.append(("Lumen(参照)", ref_lumen().resize((512, 512), Image.LANCZOS)))
    for n in names:
        rows.append((n, M.plate(512, VARIANTS[n], frac=frac)))

    width = PAD + sum(COLS) + PAD * len(COLS)
    height = PAD + len(rows) * (COLS[0] + PAD)
    img = Image.new("RGBA", (width, height), bg)
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    for i, (label, big) in enumerate(rows):
        y = PAD + i * (COLS[0] + PAD)
        img.alpha_composite(big, (PAD, y))
        d.text((PAD + 4, y + 4), label, font=font, fill=(40, 40, 40, 255))
        x = PAD + COLS[0] + PAD
        for px in COLS[1:]:
            small = big.resize((px, px), Image.LANCZOS)
            img.alpha_composite(small, (x, y + (COLS[0] - px) // 2))
            x += px + PAD
    out = os.path.join(OUT, "lab-sheet.png")
    img.convert("RGB").save(out)
    print("对比图：%s" % out)


def ladder(names, fracs):
    """同一方案在不同大小下各渲染一行：看"留白"够不够。"""
    rows = []
    for n in names:
        for f in fracs:
            rows.append(("%s@%.2f" % (n, f), M.plate(512, VARIANTS[n], frac=f)))
    width = PAD + sum(COLS) + PAD * len(COLS)
    height = PAD + len(rows) * (COLS[0] + PAD)
    img = Image.new("RGBA", (width, height), (238, 238, 240, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    for i, (label, big) in enumerate(rows):
        y = PAD + i * (COLS[0] + PAD)
        img.alpha_composite(big, (PAD, y))
        d.text((PAD + 4, y + 4), label, font=font, fill=(40, 40, 40, 255))
        x = PAD + COLS[0] + PAD
        for px in COLS[1:]:
            img.alpha_composite(big.resize((px, px), Image.LANCZOS),
                                (x, y + (COLS[0] - px) // 2))
            x += px + PAD
    out = os.path.join(OUT, "lab-ladder.png")
    img.convert("RGB").save(out)
    print("大小对照图：%s" % out)


def cjk_font(px):
    for p in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"):
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, px)
            except Exception:
                pass
    return ImageFont.load_default()


def options_sheet(entries, out_name="options.png"):
    """给用户挑的方案图：每行一个大图 + 三个小尺寸（看小图标下认不认得出）。"""
    big, smalls = 300, (96, 64, 48)
    row_h = big + 74
    width = 60 + big + 40 + sum(smalls) + 24 * len(smalls) + 60
    img = Image.new("RGBA", (width, 60 + row_h * len(entries)), (247, 247, 249, 255))
    d = ImageDraw.Draw(img)
    fl, fs = cjk_font(30), cjk_font(19)
    for i, (label, note, name, frac) in enumerate(entries):
        y = 40 + i * row_h
        d.text((60, y - 34), label, font=fl, fill=(20, 20, 24, 255))
        w = d.textlength(label, font=fl)
        d.text((60 + w + 16, y - 26), note, font=fs, fill=(110, 112, 120, 255))
        img.alpha_composite(M.plate(big, VARIANTS[name], frac=frac), (60, y))
        x = 60 + big + 40
        for px in smalls:
            img.alpha_composite(M.plate(px, VARIANTS[name], frac=frac),
                                (x, y + big - px))
            x += px + 24
    out = os.path.join(OUT, out_name)
    img.convert("RGB").save(out)
    print("方案图：%s" % out)


def main():
    args = sys.argv[1:]
    frac = 0.72
    if args and args[0].startswith("--frac="):
        frac = float(args[0].split("=", 1)[1])
        args = args[1:]
    if args and args[0] == "--options":
        options_sheet([
            ("现在手机上的", "四格里有小山和太阳", "H0_current", 0.72),
            ("方案一 · 四格相册", "四张干净的图，最醒目", "OPT1", 0.70),
            ("方案二 · 一叠图", "像一摞照片，一个主体", "OPT2", 0.68),
            ("方案三 · 一张搬出", "右下那张正被搬走", "OPT3", 0.68),
        ])
        return
    if args and args[0] == "--ladder":
        ladder(args[1:], (0.50, 0.58, 0.66, 0.72))
        return
    names = args or list(VARIANTS)
    if args and args[0] == "--single":
        for n in args[1:]:
            M.plate(512, VARIANTS[n], frac=frac).save(os.path.join(OUT, "lab-%s.png" % n))
            print("单图：lab-%s.png" % n)
        return
    sheet(names, frac=frac)


if __name__ == "__main__":
    main()
