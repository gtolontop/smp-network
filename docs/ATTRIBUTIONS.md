# PTR Resource Pack — Attributions

> Every external asset must be either **CC0**, **CC-BY** with attribution, or **explicitly granted by the author**. If a candidate cannot be cleanly licensed, it does not ship.

---

## Curated upstream sources (vetted)

These repositories / sites have been vetted as suitable upstream sources for the PTR pack. Each entry below documents the license, what kind of asset to source, and whether attribution must accompany usage.

| Source | License | Best for | Attribution required |
|---|---|---|---|
| [Faithful 32x Java](https://github.com/Faithful-Resource-Pack/Faithful-32x-Java) | Faithful License v3 (free for server packs) | Higher-resolution faithful base for vanilla blocks/items where we want to keep a vanilla-ish silhouette | ✅ Credit + link back to https://faithfulpack.net/ + ship `LICENSE.txt` unmodified in the pack |
| [FreeMinecraftModels (MagmaGuy)](https://github.com/MagmaGuy/FreeMinecraftModels) | CC0 (resource pack contents) | Boss / mob models. Plugin code is GPLv3 but the models are CC0 | ❌ Recommended: still note where it came from for our own audit trail |
| [FrenchKrab / mc-blockbench-models](https://github.com/FrenchKrab/mc-blockbench-models) | CC-BY-4.0 | Tool/weapon models — `drill_breaker.bbmodel` (foreuse fit), `er_warhammer_pickaxe.bbmodel` (marteau fit), `greatsword_mineral.bbmodel`, `tree_monster.bbmodel`/`tree_monster_small.bbmodel` (boss adds) | ✅ Credit author + URL + license name |
| [awesome-cc0 (madjin)](https://github.com/madjin/awesome-cc0) | List, individual assets vary | Index of CC0 art, audio, fonts | per-asset |
| [OpenGameArt — CC0 filter](https://opengameart.org/content/cc0-public-domain) | CC0 (per asset) | Audio (SFX + music), 2D sprites, 3D models | ❌ |
| [Pixabay Music — License Free](https://pixabay.com/music/) | Pixabay Content License (commercial OK, no attribution required, but be checked per-track) | Boss music tracks | optional |
| [Mixkit — Free Music](https://mixkit.co/free-stock-music/) | Mixkit License (free, but verify per-track) | Boss music tracks | optional |
| [Kenney.nl — Free game assets](https://kenney.nl/) | CC0 | UI elements, fonts, generic block textures, audio | ❌ |
| [Sketchfab — CC0 filter](https://sketchfab.com/3d-models?features=downloadable&licenses=cc0) | CC0 (per model) | Reference 3D for re-modelling in Blockbench | ❌ |

### Workflow for sourcing an asset

1. Pick a candidate from one of the vetted sources above.
2. Verify the per-asset license matches the table.
3. Add an entry to the relevant section below with: **asset path in our repo**, **original URL**, **license**, **author**, **what we used it for**.
4. If the source's license requires the LICENSE file shipped, copy it under `plugins-fabric/ptr-showcase-fabric/src/main/resources/LICENSE-<source>.txt`.
5. Keep modifications documented (e.g., "downsampled from 64x to 32x", "recoloured for ptr theme").

---

## Audio (jukebox_song)

| Track | License | Source URL | Used as | Notes |
|---|---|---|---|---|
| _none yet_ | — | — | — | jukebox_song registries declare `ptr:music.gardien_theme`, `ptr:music.roi_theme`, `ptr:music.anomalie_theme` — sound files pending |

**Suggested next step**: hunt three CC0 ambient/orchestral tracks from Pixabay or Mixkit fitting the boss themes (mining drone, battle orchestral, glitch ambient). Convert to 22050 Hz mono OGG, 96 kbps, then drop into `assets/ptr/sounds/music/` and reference from `assets/ptr/sounds.json`.

## Models / Textures (Blocks)

| Asset path | License | Source URL | Used by | Notes |
|---|---|---|---|---|
| `assets/ptr/textures/block/blue_grass_*.png` | CC0 (in-house) | `scripts/gen-real-block-textures.py` | `ptr:blue_grass` | Procedurally generated placeholder |
| `assets/ptr/textures/block/plasma_block.png` | CC0 (in-house) | gen script | `ptr:plasma_block` | placeholder |
| `assets/ptr/textures/block/darkstone.png` | CC0 (in-house) | gen script | `ptr:darkstone` | placeholder |
| `assets/ptr/textures/block/glow_moss.png` | CC0 (in-house) | gen script | `ptr:glow_moss` | placeholder |
| `assets/ptr/textures/block/runesteel.png` | CC0 (in-house) | gen script | `ptr:runesteel` | placeholder |
| `assets/ptr/textures/block/forge_runique_*.png` | CC0 (in-house) | gen script | `ptr:forge_runique` | placeholder |
| `assets/ptr/textures/block/station_recharge.png` | CC0 (in-house) | gen script | `ptr:station_recharge` | placeholder |
| `assets/ptr/textures/block/console_marche_noir.png` | CC0 (in-house) | gen script | `ptr:console_marche_noir` | placeholder |
| `assets/ptr/textures/block/pylone_telegraph.png` | CC0 (in-house) | gen script | `ptr:pylone_telegraph` | placeholder |
| `assets/ptr/textures/block/tableau_events.png` | CC0 (in-house) | gen script | `ptr:tableau_events` | placeholder |

## Models / Textures (Items)

| Asset path | License | Source URL | Used by | Notes |
|---|---|---|---|---|
| `assets/ptr/models/item/foreuse.json` | CC0 (in-house Blockbench) | this repo | `ptr:foreuse` | 3D Blockbench shaft + drill head + redstone core. Suggested upgrade: replace with `drill_breaker.bbmodel` from FrenchKrab repo (CC-BY-4.0, attribution required) |
| `assets/ptr/models/item/marteau_build.json` | CC0 (in-house) | this repo | `ptr:marteau_build` | Suggested upgrade: `er_warhammer_pickaxe.bbmodel` from FrenchKrab |
| `assets/ptr/models/item/scanner.json` | CC0 (in-house) | this repo | `ptr:scanner` | tube + lens, in-house |
| `assets/ptr/models/item/tronconneuse.json` | CC0 (in-house) | this repo | `ptr:tronconneuse` | handle + blade |
| `assets/ptr/models/item/grappin.json` | CC0 (in-house) | this repo | `ptr:grappin` | inheritance |
| `assets/ptr/models/item/voidstone.json` | CC0 (in-house) | this repo | `ptr:voidstone` | inheritance |
| `assets/ptr/models/item/boussole_boss.json` | CC0 (in-house) | this repo | `ptr:boussole_boss` | inheritance |
| `assets/ptr/models/item/totem_alarme.json` | CC0 (in-house) | this repo | `ptr:totem_alarme` | inheritance |

## Models / Textures (Entities)

| Asset path | License | Source URL | Used by | Notes |
|---|---|---|---|---|
| `assets/ptr/textures/entity/gardien_mine.png` | CC0 (in-house) | gen script | `ptr:gardien_mine` | placeholder ItemDisplay rig texture; suggested upgrade: search `FreeMinecraftModels/imports/` for a heavy-armoured golem — CC0 |
| `assets/ptr/textures/entity/roi_pillards.png` | CC0 (in-house) | gen script | `ptr:roi_pillards` | placeholder |
| `assets/ptr/textures/entity/anomalie.png` | CC0 (in-house) | gen script | `ptr:anomalie` | placeholder; the cube-glitch concept fits a hand-painted glitch texture from OpenGameArt |

## Paintings

| Asset path | License | Source URL | Used by | Notes |
|---|---|---|---|---|
| `assets/ptr/textures/painting/anomalie.png` | CC0 (in-house) | `scripts/gen-paintings.py` | `ptr:anomalie` painting variant | placeholder |
| `assets/ptr/textures/painting/etendard_ptr.png` | CC0 (in-house) | gen script | `ptr:etendard_ptr` | placeholder |
| `assets/ptr/textures/painting/pylone.png` | CC0 (in-house) | gen script | `ptr:pylone` | placeholder |
| _gardien, roi, mining_rig painting variants_ | declared in datapack, no PNG yet | — | — | three variants reference textures that don't exist yet — sourcing pending |

## Fonts

| Asset path | License | Source URL | Used by | Notes |
|---|---|---|---|---|
| _none yet_ | — | — | — | HUD ornaments planned via Polymer's `polymer-resource-pack-extras` font asset API |

---

## Curation rules

1. **CC0 first.** Prefer CC0 sources. Most desirable: Kenney.nl, OpenGameArt (CC0 filter), Pixabay (CC0 ones), Mixkit (CC0 audio), Sketchfab CC0 filter, MagmaGuy/FreeMinecraftModels.
2. **CC-BY with attribution.** Acceptable but each entry MUST be listed here with the author and source URL. The pack ships a `LICENSES.txt` mirror that includes the same attributions.
3. **Faithful** is acceptable for placeholders / bases for vanilla items, with the explicit linkback + LICENSE.txt requirement met.
4. **No implicit re-use.** "Found on Pinterest", "borrowed from a Discord server", "ripped from a popular pack" — all rejected.
5. **Modify, don't redistribute.** When we tweak a CC-BY texture, both the original attribution and the change description must be recorded.
6. **In-house assets.** Anything generated by our own scripts (`scripts/gen-*.py`) or hand-built from scratch is licensed CC0 to the project.

## Sourcing priority for the next sprint

Highest-leverage upgrades, in order:

1. **3 boss music tracks** (Pixabay or Mixkit) for `gardien_theme`, `roi_theme`, `anomalie_theme` — converts a declared registry id into actual playable content. ~1 h.
2. **Replace `foreuse` and `marteau_build` 3D models** with `drill_breaker.bbmodel` and `er_warhammer_pickaxe.bbmodel` (FrenchKrab, CC-BY-4.0) — these are exactly the right concepts and free. ~30 min.
3. **Boss creature models** — pull a heavy-armoured golem and a "lich"-style figure from FreeMinecraftModels (CC0) for Gardien des Mines and Anomalie. ~1 h per model + Blockbench export.
4. **Faithful base** for the painting placeholders that look hand-rolled — replace with real curated artwork from CC0 archives. ~30 min.
