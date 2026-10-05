# -*- coding: utf-8 -*-
"""临时调试：只画箭头，看它到底长什么样。"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_icon import (Image, ImageDraw, catmull_rom, curved_arrow, new_mask,
                       rounded_polygon, fill)

S = 1024
m, d = new_mask(S)
s = S / 1024.0
pts = catmull_rom([
    (360 * s, 900 * s), (610 * s, 892 * s), (770 * s, 850 * s), (876 * s, 754 * s),
], samples=16)
print("箭头路径末端 5 点：")
for p in pts[-5:]:
    print("   %.1f, %.1f" % p)
import math
from make_icon import end_direction

tip = pts[-1]
ang = end_direction(pts, 210 * s * 0.9)
print("tip=(%.1f, %.1f)  方向角=%.1f°" % (tip[0], tip[1], math.degrees(ang)))
head_len, head_w = 210 * s, 136 * s
back = (tip[0] - math.cos(ang) * head_len * 0.72, tip[1] - math.sin(ang) * head_len * 0.72)
perp = (math.cos(ang + math.pi / 2), math.sin(ang + math.pi / 2))
left = (back[0] + perp[0] * head_w / 2, back[1] + perp[1] * head_w / 2)
right = (back[0] - perp[0] * head_w / 2, back[1] - perp[1] * head_w / 2)
print("back=(%.1f, %.1f) left=(%.1f, %.1f) right=(%.1f, %.1f)" % (back + left + right))
tri = rounded_polygon([tip, left, right], radius=min(head_w, head_len) * 0.10)
xs = [p[0] for p in tri]
ys = [p[1] for p in tri]
print("三角轮廓：%d 点，范围 x %.1f..%.1f  y %.1f..%.1f" % (len(tri), min(xs), max(xs), min(ys), max(ys)))
curved_arrow(d, pts, shaft_w=58 * s, head_len=head_len, head_w=head_w)
m.resize((512, 512), Image.LANCZOS).save(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                      "..", "docs", "icon", "debug-arrow.png"))

# 单独看看 rounded_polygon 画的三角
m2, d2 = new_mask(S)
tri = [(820, 300), (640, 420), (640, 180)]
fill(d2, rounded_polygon(tri, 26.0), 255)
m2.crop((560, 120, 900, 480)).resize((340, 360), Image.LANCZOS).save(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "icon", "debug-tri.png"))
print("已输出 debug-arrow.png / debug-tri.png")
