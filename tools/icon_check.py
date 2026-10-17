# -*- coding: utf-8 -*-
"""装机前自检：把自适应图标的图层合成成"手机上的样子"，并排看大中小三个尺寸。

用法：python tools/icon_check.py
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_icon as M  # noqa: E402

RES = os.path.join(M.ROOT, "app", "src", "main", "res")
BRAND = (0x4C, 0xAF, 0x50, 255)  # 整块绿底板（白色剪影和它的投影在前景层里）


def composite(px):
    """按自适应图标的规则合成：108dp 画布 + 品牌底色 + 前景层，然后用方圆遮罩裁。"""
    fg = Image.open(os.path.join(RES, "mipmap-xxxhdpi",
                                 "ic_launcher_foreground.png")).convert("RGBA")
    fg = fg.resize((px, px), Image.LANCZOS)
    base = Image.new("RGBA", (px, px), BRAND)
    out = Image.alpha_composite(base, fg)
    mask = Image.new("L", (px, px), 0)
    ImageDraw.Draw(mask).polygon(M.rounded_rect(0, 0, px, px, 0.35 * px), fill=255)
    out.putalpha(mask)
    return out


def main():
    sizes = (256, 96, 64, 48)
    icons = [composite(n).resize((s, s), Image.LANCZOS)
             for n, s in zip((384, 384, 384, 384), sizes)]
    w = 40 + sizes[0] + sum(sizes[1:]) + 30 * len(sizes)
    h = sizes[0] + 60
    img = Image.new("RGBA", (w, h), (247, 247, 249, 255))
    img.alpha_composite(icons[0], (40, 40))
    x = 40 + sizes[0] + 40
    for ic, s in zip(icons[1:], sizes[1:]):
        img.alpha_composite(ic, (x, 40 + sizes[0] - s))
        x += s + 30
    out = os.path.join(M.OUT, "check-adaptive.png")
    img.convert("RGB").save(out)
    print("自检图：%s" % out)


if __name__ == "__main__":
    main()
