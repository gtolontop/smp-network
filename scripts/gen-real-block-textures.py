#!/usr/bin/env python3
"""Generate 16x16 textures for real custom blocks (note_block tuning targets)."""
import os
from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(__file__), "..", "ptr_resourcepack", "assets", "ptr", "textures", "block")
os.makedirs(BASE, exist_ok=True)


def save(img, name):
    p = os.path.join(BASE, name)
    img.save(p, optimize=True)
    print(f"  wrote {p}")


def grass_top(base_color, accent_color):
    img = Image.new("RGBA", (16, 16), base_color)
    d = ImageDraw.Draw(img)
    # Tufts
    import random
    rng = random.Random(42)
    for _ in range(40):
        x = rng.randint(0, 15)
        y = rng.randint(0, 15)
        d.point((x, y), fill=accent_color)
    return img


def grass_side(base_color, dirt, top_band):
    img = Image.new("RGBA", (16, 16), dirt)
    d = ImageDraw.Draw(img)
    # Top grass band
    d.rectangle((0, 0, 15, 3), fill=top_band)
    # Drips
    for x in [1, 4, 7, 10, 13]:
        d.point((x, 4), fill=base_color)
    return img


# 1. Blue grass
save(grass_top((40, 100, 220, 255), (90, 180, 255, 255)), "blue_grass_top.png")
save(grass_side((40, 100, 220, 255), (90, 60, 30, 255), (40, 100, 220, 255)), "blue_grass_side.png")

# 2. Plasma block - cyan/magenta cycling
img = Image.new("RGBA", (16, 16), (10, 5, 30, 255))
d = ImageDraw.Draw(img)
for x in range(16):
    for y in range(16):
        if (x + y) % 2 == 0:
            d.point((x, y), fill=(255, 80, 200, 255))
        elif (x * y) % 3 == 0:
            d.point((x, y), fill=(80, 220, 255, 255))
d.ellipse((4, 4, 11, 11), fill=(255, 240, 255, 255), outline=(255, 80, 200, 255))
save(img, "plasma_block.png")

# 3. Darkstone - very dark with sparkle
img = Image.new("RGBA", (16, 16), (10, 10, 18, 255))
d = ImageDraw.Draw(img)
for x in range(16):
    for y in range(16):
        c = ((x * 7 + y * 13) % 11) - 5
        if c > 0:
            d.point((x, y), fill=(20 + c*3, 20 + c*3, 30 + c*3, 255))
# Sparkles
for px in [(2, 3), (5, 9), (11, 4), (13, 12), (8, 7)]:
    d.point(px, fill=(220, 220, 255, 255))
save(img, "darkstone.png")

# 4. Glow moss - bright green/yellow with luminous specs
img = Image.new("RGBA", (16, 16), (20, 60, 20, 255))
d = ImageDraw.Draw(img)
for x in range(16):
    for y in range(16):
        if ((x * 3 + y * 5) % 7) < 3:
            d.point((x, y), fill=(40, 100, 30, 255))
# Glow specs
for px in [(3, 3), (8, 5), (12, 8), (5, 11), (10, 13)]:
    d.point(px, fill=(255, 255, 130, 255))
    d.point((px[0]+1, px[1]), fill=(180, 220, 100, 255))
save(img, "glow_moss.png")

# 5. Runesteel - dark steel with bright runic carvings
img = Image.new("RGBA", (16, 16), (45, 45, 55, 255))
d = ImageDraw.Draw(img)
# Plate texture
for x in range(16):
    for y in range(16):
        if ((x % 4) == 0 or (y % 4) == 0):
            d.point((x, y), fill=(70, 70, 85, 255))
# Rune patterns
d.line((4, 5, 11, 5), fill=(255, 200, 80, 255))
d.line((4, 8, 8, 8), fill=(255, 200, 80, 255))
d.line((9, 8, 11, 10), fill=(255, 200, 80, 255))
d.line((4, 11, 11, 11), fill=(255, 200, 80, 255))
d.point((6, 7), fill=(255, 240, 180, 255))
d.point((10, 9), fill=(255, 240, 180, 255))
save(img, "runesteel.png")

print("Done.")
