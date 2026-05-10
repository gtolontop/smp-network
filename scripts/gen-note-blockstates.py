#!/usr/bin/env python3
"""Generate the note_block blockstates override that maps specific (instrument,note,powered)
combos to PTR custom block models. Vanilla combos default to minecraft:block/note_block.
"""
import json, os

INSTRUMENTS = [
    "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
    "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo",
    "bit", "banjo", "pling",
    "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin",
    "custom_head"
]

# Known PTR custom block mappings (instrument, note, powered) -> ptr model
CUSTOM = {
    ("harp", 1, False):  "ptr:block/blue_grass",
    ("harp", 2, False):  "ptr:block/plasma_block",
    ("harp", 3, False):  "ptr:block/darkstone",
    ("harp", 4, False):  "ptr:block/glow_moss",
    ("harp", 5, False):  "ptr:block/runesteel",
}

variants = {}
for inst in INSTRUMENTS:
    for note in range(25):
        for powered in (False, True):
            key = f"instrument={inst},note={note},powered={'true' if powered else 'false'}"
            model = CUSTOM.get((inst, note, powered), "minecraft:block/note_block")
            variants[key] = {"model": model}

out_path = os.path.join(
    os.path.dirname(__file__), "..",
    "ptr_resourcepack", "assets", "minecraft", "blockstates", "note_block.json"
)
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w") as f:
    json.dump({"variants": variants}, f, indent=2)
print(f"Wrote {len(variants)} variants to {out_path}")
print("Custom mappings:")
for k, v in CUSTOM.items():
    print(f"  {k} -> {v}")
