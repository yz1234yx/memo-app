# -*- coding: utf-8 -*-
"""生成 MemoApp 图标：宝石蓝渐变底 + 白色「备」字。

产出：
  mipmap-*/ic_launcher.png          传统方形图标（圆角矩形）
  mipmap-*/ic_launcher_round.png    传统圆形图标
  mipmap-*/ic_launcher_background.png / ic_launcher_foreground.png  自适应图标两层
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

RES = os.path.join("app", "src", "main", "res")
FONT_PATH = r"C:\Windows\Fonts\msyhbd.ttc"

# 宝石蓝：上偏亮、下偏深，做出宝石的通透感
TOP = (46, 108, 255)      # #2E6CFF
MID = (22, 78, 226)       # #164EE2
BOTTOM = (9, 45, 158)     # #092D9E
GLYPH = (255, 255, 255)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}


def gradient_square(size, radius_ratio=0.0, circle=False):
    """竖向三段渐变 + 左上角柔光，画在超采样画布上再缩，边缘更干净。"""
    ss = 4
    n = size * ss
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    grad = Image.new("RGBA", (1, n))
    gd = ImageDraw.Draw(grad)
    for y in range(n):
        t = y / (n - 1)
        if t < 0.5:
            k = t / 0.5
            c = tuple(int(TOP[i] + (MID[i] - TOP[i]) * k) for i in range(3))
        else:
            k = (t - 0.5) / 0.5
            c = tuple(int(MID[i] + (BOTTOM[i] - MID[i]) * k) for i in range(3))
        gd.point((0, y), fill=c + (255,))
    grad = grad.resize((n, n))

    # 左上柔光（宝石高光）
    glow = Image.new("L", (n, n), 0)
    gdd = ImageDraw.Draw(glow)
    r = int(n * 0.78)
    gdd.ellipse([-int(n * 0.30), -int(n * 0.46), -int(n * 0.30) + r, -int(n * 0.46) + r], fill=95)
    glow = glow.filter(ImageFilter.GaussianBlur(n * 0.10))
    highlight = Image.new("RGBA", (n, n), (255, 255, 255, 255))
    highlight.putalpha(glow)
    grad = Image.alpha_composite(grad, highlight)

    mask = Image.new("L", (n, n), 0)
    md = ImageDraw.Draw(mask)
    if circle:
        md.ellipse([0, 0, n - 1, n - 1], fill=255)
    elif radius_ratio > 0:
        md.rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * radius_ratio), fill=255)
    else:
        md.rectangle([0, 0, n - 1, n - 1], fill=255)
    grad.putalpha(mask)
    return grad.resize((size, size), Image.LANCZOS)


def glyph_layer(size, ratio=0.46, shadow=True, ss=4):
    """白色「备」字，可选柔和投影。ratio = 字面高度占画布比例。"""
    n = size * ss
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    target_h = n * ratio
    # 从大字号起逐级试，直到字面高度接近目标
    fs = int(target_h * 1.35)
    font = ImageFont.truetype(FONT_PATH, fs)
    while fs > 8:
        font = ImageFont.truetype(FONT_PATH, fs)
        box = font.getbbox("备")
        if (box[3] - box[1]) <= target_h:
            break
        fs -= max(1, fs // 40)

    box = font.getbbox("备")
    gw, gh = box[2] - box[0], box[3] - box[1]
    x = (n - gw) / 2 - box[0]
    y = (n - gh) / 2 - box[1]

    if shadow:
        sh = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((x, y + n * 0.012), "备", font=font, fill=(4, 20, 80, 130))
        sh = sh.filter(ImageFilter.GaussianBlur(n * 0.014))
        img = Image.alpha_composite(img, sh)

    ImageDraw.Draw(img).text((x, y), "备", font=font, fill=GLYPH + (255,))
    return img.resize((size, size), Image.LANCZOS)


def main():
    for dens, scale in DENSITIES.items():
        d = os.path.join(RES, "mipmap-" + dens)
        os.makedirs(d, exist_ok=True)

        # 传统图标
        for name, circle in (("ic_launcher", False), ("ic_launcher_round", True)):
            size = int(48 * scale)
            base = gradient_square(size, radius_ratio=0.24, circle=circle)
            g = glyph_layer(size, ratio=0.46 if not circle else 0.44)
            base = Image.alpha_composite(base, g)
            base.save(os.path.join(d, name + ".png"))

        # 自适应图标两层（108dp 画布）
        size = int(108 * scale)
        gradient_square(size).save(os.path.join(d, "ic_launcher_background.png"))
        glyph_layer(size, ratio=0.44).save(os.path.join(d, "ic_launcher_foreground.png"))

    # 自适应图标声明
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    for name in ("ic_launcher", "ic_launcher_round"):
        with open(os.path.join(anydpi, name + ".xml"), "w", encoding="utf-8") as f:
            f.write(xml)

    # 预览用大图
    big = gradient_square(512, radius_ratio=0.24)
    big = Image.alpha_composite(big, glyph_layer(512, ratio=0.46))
    big.save("icon_preview.png")


if __name__ == "__main__":
    main()
