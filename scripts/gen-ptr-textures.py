#!/usr/bin/env python3
"""Generate 16x16 custom textures for the PTR Showcase resource pack."""
import os
import sys
from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(__file__), "..", "ptr_resourcepack", "assets", "ptr", "textures")
ITEM = os.path.join(BASE, "item")
BLOCK = os.path.join(BASE, "block")
ENTITY = os.path.join(BASE, "entity")

for d in (ITEM, BLOCK, ENTITY):
    os.makedirs(d, exist_ok=True)


def save(img, path):
    img.save(path, optimize=True)
    print(f"  wrote {path}  ({os.path.getsize(path)}B)")


def fill_rect(d, box, fill, outline=None):
    d.rectangle(box, fill=fill, outline=outline)


def base_canvas():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


# Palette inspired by the items lore (custom industrial / arcane feel)
def grad(c1, c2, steps):
    return [tuple(int(c1[i] + (c2[i] - c1[i]) * t / max(1, steps - 1)) for i in range(4)) for t in range(steps)]


# ---------------- ITEMS ----------------

def texture_foreuse():
    img, d = base_canvas()
    # Drill body — dark steel with cyan rivets and red core
    body = (40, 44, 50, 255)
    rivet = (180, 185, 195, 255)
    rim = (15, 18, 22, 255)
    core_lo = (40, 0, 0, 255)
    core_hi = (255, 80, 60, 255)
    fill_rect(d, (3, 1, 12, 4), body, outline=rim)
    fill_rect(d, (4, 4, 11, 6), (60, 65, 70, 255))
    # Drill bit triangle bottom
    for y in range(7, 14):
        offset = (y - 7)
        d.line((4 + offset, y, 12 - offset, y), fill=(120, 125, 130, 255))
    # Tip
    d.point((8, 14), fill=(220, 230, 240, 255))
    d.point((8, 15), fill=(200, 210, 220, 255))
    # Core glow
    fill_rect(d, (7, 2, 9, 4), core_hi)
    d.point((8, 3), fill=(255, 220, 200, 255))
    # Rivets
    for px in [(4, 2), (11, 2), (4, 5), (11, 5)]:
        d.point(px, fill=rivet)
    return img


def texture_tronconneuse():
    img, d = base_canvas()
    iron = (200, 200, 210, 255)
    iron_dark = (130, 130, 140, 255)
    wood = (110, 75, 40, 255)
    wood_dark = (75, 50, 25, 255)
    teeth = (240, 240, 250, 255)
    # Handle (top)
    fill_rect(d, (1, 1, 4, 11), wood)
    fill_rect(d, (1, 3, 4, 4), wood_dark)
    fill_rect(d, (1, 7, 4, 8), wood_dark)
    # Blade arm
    fill_rect(d, (4, 8, 14, 11), iron_dark)
    fill_rect(d, (4, 9, 14, 10), iron)
    # Saw teeth top
    for x in range(5, 14, 2):
        d.point((x, 7), fill=teeth)
    for x in range(5, 14, 2):
        d.point((x, 12), fill=teeth)
    # Bolt
    d.point((5, 9), fill=(60, 60, 65, 255))
    d.point((13, 9), fill=(60, 60, 65, 255))
    return img


def texture_grappin():
    img, d = base_canvas()
    rope_lo = (90, 65, 35, 255)
    rope_hi = (140, 100, 55, 255)
    hook = (180, 180, 195, 255)
    hook_dk = (110, 110, 125, 255)
    # Rope coil
    for i in range(3, 13):
        c = rope_lo if (i // 2) % 2 == 0 else rope_hi
        d.line((i, 4 + (i % 3), i, 4 + (i % 3) + 1), fill=c)
    # Hook
    d.line((11, 9, 13, 11), fill=hook)
    d.line((11, 10, 13, 12), fill=hook_dk)
    d.line((13, 11, 13, 14), fill=hook)
    d.line((12, 14, 13, 14), fill=hook_dk)
    d.line((11, 14, 11, 13), fill=hook)
    # Crystal core (cyan)
    fill_rect(d, (4, 1, 8, 3), (100, 200, 220, 255))
    d.point((6, 2), fill=(220, 250, 255, 255))
    return img


def texture_scanner():
    img, d = base_canvas()
    barrel = (60, 65, 75, 255)
    barrel_lit = (100, 110, 125, 255)
    glass = (160, 220, 240, 255)
    glow = (255, 240, 80, 255)
    # Tube vertical
    fill_rect(d, (5, 2, 10, 14), barrel)
    fill_rect(d, (6, 2, 9, 14), barrel_lit)
    # Top lens
    fill_rect(d, (4, 0, 11, 2), barrel)
    fill_rect(d, (5, 1, 10, 2), glass)
    d.point((7, 1), fill=(255, 255, 255, 255))
    # Side notch
    fill_rect(d, (10, 7, 12, 9), barrel)
    # Indicator light (yellow)
    d.point((7, 10), fill=glow)
    d.point((8, 10), fill=glow)
    return img


def texture_voidstone():
    img, d = base_canvas()
    # Crystal hexagonal vibe
    dark = (15, 5, 25, 255)
    mid = (60, 30, 120, 255)
    bright = (140, 80, 220, 255)
    star = (240, 220, 255, 255)
    pts = [(7, 1), (10, 3), (12, 7), (10, 12), (7, 14), (4, 12), (2, 7), (4, 3)]
    d.polygon(pts, fill=mid, outline=dark)
    d.polygon([(7, 4), (10, 7), (7, 11), (4, 7)], fill=bright)
    d.point((7, 7), fill=star)
    d.point((7, 6), fill=star)
    d.point((8, 7), fill=star)
    return img


def texture_boussole_boss():
    img, d = base_canvas()
    rim = (60, 50, 80, 255)
    face = (250, 240, 200, 255)
    face_dark = (200, 180, 130, 255)
    needle_red = (220, 40, 40, 255)
    needle_blue = (40, 80, 200, 255)
    # Round body via diamond approximation
    d.ellipse((1, 1, 14, 14), fill=face, outline=rim)
    d.ellipse((3, 3, 12, 12), fill=face_dark)
    # Needle
    d.line((8, 4, 8, 8), fill=needle_red)
    d.line((8, 8, 8, 12), fill=needle_blue)
    d.point((8, 8), fill=(20, 20, 20, 255))
    # Tick marks
    for tx, ty in [(7, 1), (8, 1), (1, 7), (1, 8), (14, 7), (14, 8), (7, 14), (8, 14)]:
        d.point((tx, ty), fill=rim)
    return img


def texture_marteau_build():
    img, d = base_canvas()
    gold = (255, 200, 60, 255)
    gold_dk = (180, 130, 30, 255)
    wood = (110, 75, 40, 255)
    rune = (255, 240, 180, 255)
    # Hammer head
    fill_rect(d, (2, 1, 14, 6), gold_dk)
    fill_rect(d, (3, 2, 13, 5), gold)
    # Rune line
    d.line((4, 3, 12, 3), fill=rune)
    d.point((8, 4), fill=rune)
    # Shaft
    fill_rect(d, (7, 6, 9, 15), wood)
    d.line((8, 7, 8, 14), fill=(150, 100, 55, 255))
    # Pommel
    fill_rect(d, (6, 14, 10, 15), gold)
    return img


def texture_totem_alarme():
    img, d = base_canvas()
    base = (90, 60, 30, 255)
    horn = (240, 220, 90, 255)
    eye = (220, 30, 30, 255)
    eye_glow = (255, 200, 200, 255)
    # Body (totem-like)
    fill_rect(d, (4, 2, 11, 14), base)
    # Horns
    d.line((3, 1, 5, 3), fill=horn)
    d.line((10, 3, 12, 1), fill=horn)
    # Eye
    fill_rect(d, (6, 6, 9, 9), eye)
    d.point((7, 7), fill=eye_glow)
    # Mouth (siren)
    d.line((6, 11, 9, 11), fill=horn)
    d.line((6, 12, 9, 12), fill=(40, 20, 10, 255))
    # Glow base
    fill_rect(d, (4, 13, 11, 14), (200, 60, 60, 200))
    return img


# ---------------- BLOCKS ----------------

def texture_forge_runique_top():
    img, d = base_canvas()
    plate = (45, 40, 60, 255)
    plate2 = (60, 55, 80, 255)
    rune = (255, 180, 60, 255)
    glow = (255, 240, 180, 255)
    fill_rect(d, (0, 0, 16, 16), plate)
    for x in range(0, 16, 4):
        for y in range(0, 16, 4):
            d.point((x, y), fill=plate2)
    # Rune circle
    d.ellipse((3, 3, 12, 12), outline=rune)
    d.line((8, 3, 8, 12), fill=rune)
    d.line((3, 8, 12, 8), fill=rune)
    d.point((8, 8), fill=glow)
    return img


def texture_forge_runique_side():
    img, d = base_canvas()
    plate = (40, 35, 55, 255)
    rivet = (140, 130, 110, 255)
    fill_rect(d, (0, 0, 16, 16), plate)
    for px in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        d.point(px, fill=rivet)
        d.point((px[0]+1, px[1]), fill=rivet)
    fill_rect(d, (3, 7, 12, 9), (60, 55, 80, 255))
    d.line((4, 8, 11, 8), fill=(255, 180, 60, 255))
    return img


def texture_station_recharge():
    img, d = base_canvas()
    # Cyan/copper recharge station
    body = (50, 70, 90, 255)
    rim = (90, 130, 170, 255)
    coil = (220, 130, 80, 255)
    glow = (160, 240, 255, 255)
    fill_rect(d, (0, 0, 16, 16), body)
    for x in range(2, 14, 2):
        d.line((x, 1, x, 14), fill=rim)
    # Center diamond
    d.polygon([(8, 4), (12, 8), (8, 12), (4, 8)], fill=coil, outline=rim)
    d.point((8, 8), fill=glow)
    d.point((8, 7), fill=glow)
    return img


def texture_console_marche_noir():
    img, d = base_canvas()
    bg = (15, 10, 20, 255)
    screen = (30, 200, 100, 255)
    text = (180, 255, 200, 255)
    case = (60, 50, 70, 255)
    fill_rect(d, (0, 0, 16, 16), bg)
    fill_rect(d, (1, 1, 14, 14), case)
    fill_rect(d, (3, 3, 12, 10), screen)
    # Code lines
    d.line((4, 4, 11, 4), fill=text)
    d.line((4, 6, 9, 6), fill=text)
    d.line((4, 8, 10, 8), fill=text)
    # Buttons
    fill_rect(d, (3, 11, 5, 13), (200, 30, 30, 255))
    fill_rect(d, (7, 11, 9, 13), (220, 220, 50, 255))
    fill_rect(d, (11, 11, 13, 13), (50, 220, 50, 255))
    return img


def texture_pylone_telegraph():
    img, d = base_canvas()
    base = (40, 30, 70, 255)
    crystal = (240, 80, 220, 255)
    glow = (255, 240, 255, 255)
    fill_rect(d, (0, 0, 16, 16), base)
    # Crystal pylon
    d.polygon([(8, 1), (12, 6), (10, 14), (6, 14), (4, 6)], fill=crystal, outline=(255, 200, 250, 255))
    d.line((8, 3, 8, 13), fill=glow)
    return img


def texture_tableau_events():
    img, d = base_canvas()
    frame = (50, 35, 25, 255)
    panel = (180, 30, 60, 255)
    text_y = (255, 220, 80, 255)
    fill_rect(d, (0, 0, 16, 16), frame)
    fill_rect(d, (2, 2, 13, 13), panel)
    # E-letters style
    for y in [4, 7, 10]:
        d.line((4, y, 11, y), fill=text_y)
    return img


# ---------------- ENTITY (mob rigs) ----------------

def texture_gardien_mine():
    img, d = base_canvas()
    metal = (170, 170, 175, 255)
    metal_dk = (90, 90, 95, 255)
    rust = (160, 80, 50, 255)
    eye = (255, 60, 30, 255)
    fill_rect(d, (0, 0, 16, 16), metal)
    for y in range(0, 16, 4):
        d.line((0, y, 16, y), fill=metal_dk)
    # Rust streaks
    d.line((3, 5, 3, 13), fill=rust)
    d.line((11, 4, 11, 12), fill=rust)
    # Eye row
    fill_rect(d, (5, 7, 11, 9), (30, 30, 35, 255))
    d.point((6, 8), fill=eye)
    d.point((10, 8), fill=eye)
    return img


def texture_roi_pillards():
    img, d = base_canvas()
    cloth = (90, 30, 50, 255)
    cloth_lt = (140, 60, 80, 255)
    gold = (255, 200, 60, 255)
    skin = (200, 160, 130, 255)
    fill_rect(d, (0, 0, 16, 16), cloth)
    fill_rect(d, (3, 4, 13, 12), cloth_lt)
    # Crown
    for x in [4, 7, 10, 13]:
        d.line((x, 1, x, 3), fill=gold)
    fill_rect(d, (3, 3, 13, 4), gold)
    # Face area
    fill_rect(d, (5, 5, 11, 9), skin)
    # Eyes
    d.point((6, 7), fill=(0, 0, 0, 255))
    d.point((10, 7), fill=(0, 0, 0, 255))
    return img


def texture_anomalie():
    img, d = base_canvas()
    void = (5, 0, 15, 255)
    purple = (90, 30, 160, 255)
    glow = (220, 100, 255, 255)
    eye = (255, 240, 80, 255)
    fill_rect(d, (0, 0, 16, 16), void)
    # Cracks
    for (x1, y1, x2, y2) in [(2, 14, 6, 8), (10, 1, 14, 7), (5, 0, 9, 6), (1, 5, 5, 9)]:
        d.line((x1, y1, x2, y2), fill=purple)
    # Center eye
    d.ellipse((5, 5, 10, 10), fill=glow, outline=purple)
    d.point((7, 7), fill=eye)
    d.point((8, 7), fill=eye)
    return img


# ---------------- SAVE ----------------

mappings = [
    (ITEM,   "foreuse.png", texture_foreuse()),
    (ITEM,   "tronconneuse.png", texture_tronconneuse()),
    (ITEM,   "grappin.png", texture_grappin()),
    (ITEM,   "scanner.png", texture_scanner()),
    (ITEM,   "voidstone.png", texture_voidstone()),
    (ITEM,   "boussole_boss.png", texture_boussole_boss()),
    (ITEM,   "marteau_build.png", texture_marteau_build()),
    (ITEM,   "totem_alarme.png", texture_totem_alarme()),
    (BLOCK,  "forge_runique_top.png", texture_forge_runique_top()),
    (BLOCK,  "forge_runique_side.png", texture_forge_runique_side()),
    (BLOCK,  "station_recharge.png", texture_station_recharge()),
    (BLOCK,  "console_marche_noir.png", texture_console_marche_noir()),
    (BLOCK,  "pylone_telegraph.png", texture_pylone_telegraph()),
    (BLOCK,  "tableau_events.png", texture_tableau_events()),
    (ENTITY, "gardien_mine.png", texture_gardien_mine()),
    (ENTITY, "roi_pillards.png", texture_roi_pillards()),
    (ENTITY, "anomalie.png", texture_anomalie()),
]

for d, name, img in mappings:
    save(img, os.path.join(d, name))

print(f"Generated {len(mappings)} textures.")
