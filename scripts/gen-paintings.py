#!/usr/bin/env python3
"""Generate custom painting textures for the PTR Showcase datapack."""
import os
from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(__file__), "..", "ptr_resourcepack", "assets", "ptr", "textures", "painting")
os.makedirs(BASE, exist_ok=True)


def save(img, name):
    p = os.path.join(BASE, name)
    img.save(p, optimize=True)
    print(f"  wrote {p}  ({os.path.getsize(p)}B)")


# 16x16 (1x1 block) - "Anomalie" — purple cracked void
img = Image.new("RGBA", (16, 16), (10, 0, 25, 255))
d = ImageDraw.Draw(img)
for x in range(16):
    for y in range(16):
        if (x * 7 + y * 13) % 17 < 4:
            d.point((x, y), fill=(60, 20, 100, 255))
d.line((2, 14, 6, 8), fill=(180, 80, 220, 255))
d.line((10, 1, 14, 7), fill=(180, 80, 220, 255))
d.ellipse((5, 5, 10, 10), fill=(255, 200, 255, 255), outline=(255, 80, 220, 255))
d.point((7, 7), fill=(255, 255, 80, 255))
d.point((8, 7), fill=(255, 255, 80, 255))
save(img, "anomalie.png")

# 32x16 (2x1 block) - "Etendard PTR" - banner-style horizontal
img = Image.new("RGBA", (32, 16), (40, 30, 90, 255))
d = ImageDraw.Draw(img)
# Stripes
for x in range(32):
    if x % 4 < 2:
        for y in range(16):
            d.point((x, y), fill=(60, 50, 130, 255))
# PTR text emblem
text = [
    "##  ##### ##### ",
    "#### # #   #     ",
    "#### # ##  ###   ",
    "##  ###  # #     ",
    "##  ##### # #    ",
]
# Approximate paint blocky letters
d.rectangle((2, 5, 6, 11), fill=(255, 220, 80, 255))
d.rectangle((4, 5, 6, 7), fill=(40, 30, 90, 255))
d.rectangle((9, 5, 13, 6), fill=(255, 220, 80, 255))
d.rectangle((10, 5, 12, 11), fill=(255, 220, 80, 255))
d.rectangle((16, 5, 20, 11), fill=(255, 220, 80, 255))
d.rectangle((18, 5, 20, 8), fill=(40, 30, 90, 255))
d.line((19, 8, 21, 11), fill=(255, 220, 80, 255))
save(img, "etendard_ptr.png")

# 16x32 (1x2 block) - "Pylone" - vertical glowing pylon art
img = Image.new("RGBA", (16, 32), (15, 5, 40, 255))
d = ImageDraw.Draw(img)
# Vertical beam
for y in range(32):
    d.line((6, y, 9, y), fill=(150, 80, 220, 255))
    d.line((7, y, 8, y), fill=(255, 200, 255, 255))
# Top crystal
d.polygon([(8, 1), (12, 6), (10, 12), (6, 12), (4, 6)], fill=(220, 100, 240, 255), outline=(255, 200, 255, 255))
# Bottom pedestal
d.rectangle((4, 26, 12, 31), fill=(50, 40, 80, 255))
d.line((4, 26, 12, 26), fill=(180, 150, 200, 255))
save(img, "pylone.png")

print("Generated 3 paintings.")
