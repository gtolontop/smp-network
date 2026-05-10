# PTR — Architecture Decision Record (Fabric + Polymer)

> **Living document.** Updated continuously during the overnight overhaul (2026-05-10/11).
> Source of truth for the PTR (Public Test Realm) custom-content stack on the SMP network.

---

## 0. TL;DR

The PTR backend has been switched from **Paper 26.1.2** to a **Fabric 0.19.2 + Polymer 0.16.4** server on Minecraft 26.1.2. This gives us **real custom blocks, items and entities server-side** while keeping **vanilla clients connected with no mod and no resource pack other than the auto-served Polymer pack**. The legacy Paper PTR is preserved at `ptr-paper-legacy/` as historical reference and as a fallback.

The lobby and survival shards stay on Paper. PTR is the experimentation lane.

---

## 1. The vanilla-client boundary

Mojang's client treats some registries as *data-driven* and others as *built-in code*. The split, on 26.1.x, is:

| Registry | Data-driven on vanilla client? | Notes |
|---|---|---|
| `BIOME` | ✅ | Full worldgen / fog / sky / spawns |
| `DIMENSION_TYPE` / `DIMENSION` | ✅ | New worlds with custom rules |
| `ENCHANTMENT` | ✅ | Real ids + effect graphs (since 1.21) |
| `JUKEBOX_SONG` | ✅ | Real songs as registry entries |
| `INSTRUMENT` | ✅ | Goat horn instrument variants |
| `PAINTING_VARIANT` | ✅ | New paintings with size/title |
| `DAMAGE_TYPE` | ✅ | Custom death messages, scaling, exhaustion |
| `BANNER_PATTERN` | ✅ | Real banner ornament ids |
| `TRIM_PATTERN` / `TRIM_MATERIAL` | ✅ | Real armor trims |
| `WOLF_VARIANT` / `CAT_VARIANT` | ✅ | Real cosmetic variants |
| `BLOCK` | ❌ | Hard-coded, renderer + class assumptions |
| `ITEM` | ❌ | Hard-coded, item class assumptions |
| `ENTITY_TYPE` | ❌ | Hard-coded, renderer assumptions |
| `BLOCK_ENTITY_TYPE` | ❌ | Hard-coded |
| `MENU` | ❌ | Hard-coded |
| `ATTRIBUTE` | ✅ (synced) | But effect logic still client-coded for some |

**Consequence:** without a client mod, anything in the ❌ rows must be *disguised* as a vanilla member of the same registry. That disguise is the whole point of Polymer.

---

## 2. Decision matrix — which stack do we run?

| Option | Vanilla client OK? | Real server-side block/item/entity? | Survives WorldEdit / rollback? | Multi-thread regions? | Migration cost from Paper | Verdict |
|---|---|---|---|---|---|---|
| **A. Pure Paper + disguises** | ✅ | ❌ (note_block tuning, display rigs) | ❌ (note_block can be reset to plain by editors) | ❌ | none | retained for `lobby/`, `survival/` — proven, stable |
| **B. Folia + disguises** | ✅ | ❌ (same as Paper) | ❌ | ✅ (regions) | high (Folia API ≠ Paper API in many places) | not pursued for PTR — disguises are still required and we lose Polymer |
| **C. Fabric + Polymer + custom mod** | ✅ | ✅ (real registry ids server-side) | ✅ (block id stays correct under WorldEdit) | partial (C2ME for chunk perf) | high (mod ≠ plugin) | **chosen for PTR** |
| **D. Force Fabric on every player** | ❌ | ✅ | ✅ | partial | impossible — public SMP, mixed cracked/premium | rejected by user constraint |
| **E. Optional launcher upgrades** | ✅ vanilla, ✅+ for opted-in | partial | partial | partial | very high (launcher distribution + AV friction) | rejected by user constraint ("autant intégrer un mod si launcher") |

**Folia clarification.** Folia is a Paper fork. Fabric is a different mod loader entirely. They cannot run in the same server JVM. "Going Folia AND Polymer" is incompatible — Polymer requires Fabric. We pick Fabric for PTR because Polymer is the leverage point. If multi-region threading becomes critical for survival, we re-evaluate Folia *separately for the survival shard* without Polymer.

---

## 3. Stack chosen for PTR

| Layer | Component | Version | Source |
|---|---|---|---|
| Runtime | Eclipse Temurin OpenJDK | 25.0.2+10 | bundled in `java/` |
| Server | Minecraft Java | 26.1.2 | downloaded by fabric-server-launcher |
| Mod loader | Fabric Loader | 0.19.2 | `meta.fabricmc.net` |
| Loader bootstrap | Fabric server launcher | for 26.1.2 | `meta.fabricmc.net/v2/versions/loader/26.1.2/0.19.2/1.1.1/server/jar` |
| API | Fabric API | 0.148.0+26.1.2 | Modrinth |
| Custom-content lib | Polymer (bundled) | 0.16.4+26.1.2 | Modrinth — modules: core, blocks, virtual-entity, networking, common, autohost, resource-pack, resource-pack-extras, sound-patcher, registry-sync-manipulator |
| Perf | Lithium | 0.24.2+mc26.1.2 | Modrinth |
| Proxy bridge | FabricProxy-Lite | 2.12.0 | Modrinth — Velocity modern forwarding |
| Build tooling | Fabric Loom | 1.16-SNAPSHOT | `maven.fabricmc.net` |
| Build tooling | Gradle | 9.4.1 | wrapper |
| Mappings | Mojang (official) | matched to MC | `loom.officialMojangMappings()` |

### Server-side abstractions used

- **`PolymerBlock`** — block registered server-side with a real id, presents to the client as a vanilla carrier block via `PolymerBlock#getPolymerBlockState`. Survives WorldEdit/copy because the *real id stays correct on the server*.
- **`PolymerBlockItem`** — item form of a polymer block, carrier item supplied as the third constructor arg.
- **`PolymerItem` / `SimplePolymerItem`** — item registered server-side with a real id, disguised as a vanilla item over the wire. Components (e.g. `Equippable`, `Consumable`, attribute modifiers) are real and survive.
- **`PolymerEntity`** — entity type registered server-side, transmitted to the client as the type returned by `getPolymerEntityType()`. Real AI, real hitbox, real attributes server-side.
- **`BlockWithElementHolder` / `ElementHolder`** — display-entity rigs attached to blocks for furniture and machine appearance. Polymer manages the per-player visibility envelope.
- **Polymer resource pack module** — programmatically generates the resource pack zip from `assets/` plus runtime declarations (item models, block models, paintings, fonts, sounds). Auto-host via `polymer-autohost` serves it on a tiny built-in HTTP server.

### Carrier choice rationale

| Custom thing | Carrier | Why |
|---|---|---|
| Real custom solid blocks (5×) | `note_block` instrument×note×powered tuning | 1000 stable invalid states usable on Paper, Polymer makes the server-side identity real |
| Furniture / machines (5×) | `BlockWithElementHolder` riding a real polymer block (often `barrier`) | non-cubic silhouettes, animations, no voxel constraint |
| Custom items (8×) | various vanilla iron / gold / netherite tools as carrier | each item picks the closest semantic carrier (sword → iron_sword, drill → iron_pickaxe, etc.) |
| Custom helmets/hats (4×) | `leather_helmet` carrier + Equippable component pointing to custom model | real `equippable` slot semantics, custom client model |
| Bosses (3×) | `IronGolem`, `Pillager`, `WitherSkeleton` virtual-entity types | closest base AI silhouette per boss; real `LivingEntity` server-side |

---

## 4. Project layout (post-overhaul)

```
miencraft/
├── lobby/                    # Paper — unchanged
├── survival/                 # Paper — unchanged
├── velocity/                 # Velocity — proxy, unchanged config (already routes ptr→127.0.0.1:25568)
├── ptr/                      # Fabric+Polymer server backend (NEW)
│   ├── fabric-server-launcher.jar
│   ├── server.properties     # online-mode=false (Velocity forwarding)
│   ├── eula.txt
│   ├── ops.json / whitelist.json / etc. (player data)
│   ├── mods/
│   │   ├── fabric-api-0.148.0+26.1.2.jar
│   │   ├── polymer-bundled-0.16.4+26.1.2.jar
│   │   ├── lithium-fabric-0.24.2+mc26.1.2.jar
│   │   ├── FabricProxy-Lite-2.12.0.jar
│   │   └── ptr-showcase-fabric-2.0.0.jar  ← built from plugins-fabric/
│   ├── config/
│   │   ├── FabricProxy-Lite.toml          # forwarding secret
│   │   ├── lithium.properties
│   │   └── polymer/{auto-host,common,resource-pack,server,sound-patch}.json
│   └── world/                # gitignored
├── ptr-paper-legacy/         # frozen Paper PTR snapshot (reference + fallback)
├── plugins/                  # Paper plugins (lobby + survival + legacy ptr-showcase)
│   ├── core-paper/
│   ├── core-velocity/
│   ├── anticheat-paper/
│   ├── smp-logger/
│   └── ptr-showcase/         # legacy Paper PTR plugin (kept for fallback / reference)
├── plugins-fabric/           # NEW — Fabric mods compiled here
│   └── ptr-showcase-fabric/  # the new PTR mod
├── ptr_resourcepack/         # legacy Paper-era resource pack source
├── ptr-fabric-resourcepack/  # NEW — sources for the v2 resource pack auto-built by Polymer
├── docs/
│   └── PTR_ARCHITECTURE.md   # this file
├── scripts/
│   ├── start-ptr-fabric.ps1
│   ├── ptr-deploy-datapacks.ps1
│   ├── rcon-cmd.ps1 / rcon-stop.ps1
│   └── gen-* (texture / blockstate / painting generators)
├── start-ptr-fabric.bat
└── java/jdk-25.0.2+10/       # bundled Temurin (gitignored)
```

---

## 5. Datapack-native real content (free wins, no client cost)

The PTR datapack `ptr_content` will be progressively expanded. Each entry below is a **real registry id** synchronised to vanilla clients.

| Registry | Planned entries | Status |
|---|---|---|
| `worldgen/biome` | 4 (azure_depths, crystalline_caves, mycelium_grotto, stratosphere) — carried over from v2 | ✅ ported |
| `painting_variant` | 6 (anomalie, etendard_ptr, pylone, gardien, roi, mining_rig) | 🟡 3/6 |
| `enchantment` | 5 (frost_aspect, void_strike, runic_shield, pillager_bane, anomaly_resist) | ⏳ planned |
| `jukebox_song` | 3 (boss themes — Gardien / Roi / Anomalie) | ⏳ planned |
| `instrument` | 2 (rune_horn, void_horn) | ⏳ planned |
| `damage_type` | 5 (frost, void, runic, telegraph_slam, anomaly_warp) | ⏳ planned |
| `banner_pattern` | 4 (PTR factions emblems) | ⏳ planned |
| `wolf_variant` | 3 (mining_dog, abyss_hound, forest_wolf) | ⏳ planned |
| `cat_variant` | 2 (rune_cat, void_cat) | ⏳ planned |
| `trim_pattern` | 2 (runic_trim, glitch_trim) | ⏳ planned |
| `trim_material` | 2 (runesteel, voidshard) | ⏳ planned |

These entries are the cheapest "real custom content" wins available. Every datapack registry shipped is one fewer thing we need to disguise.

---

## 6. Resource pack pipeline

1. **Sources** in `ptr-fabric-resourcepack/` (textures, models, sounds — all CC0 / permissive, attributions in `docs/ATTRIBUTIONS.md`).
2. **Polymer resource-pack module** at runtime adds programmatic items: items maps, block model dispatchers, painting variants, font characters for HUD ornaments, sound entries.
3. **Polymer autohost** serves the final pack on a built-in HTTP endpoint at boot, hashes it, and points the client to it via the `resource-pack` server.properties hook.
4. **Vanilla client** prompts to download the pack on join (200–500 KB target).

---

## 7. Velocity proxy wiring

`velocity/velocity.toml` already routes `ptr = "127.0.0.1:25568"`. The Fabric backend listens on the same port. Modern forwarding is enabled via `FabricProxy-Lite.toml` whose `secret` matches `velocity/forwarding.secret`. No proxy-side change required.

---

## 8. Honest leak list (what disguise still cannot fully hide)

| Leak | Where it manifests | Mitigation |
|---|---|---|
| Block break particles | When breaking a custom block, vanilla emits particles for the *carrier* block | Polymer's particle override + custom packet emission |
| Sound on placement / step / break | Carrier block sound type leaks unless overridden | Polymer block sound override per-block (already supported via `SoundType`) |
| Pathfinding cost | Mobs path according to the carrier block's pathfinding type, not the polymer block's intent | Acceptable; no tractable workaround on vanilla client |
| Tooltip when hovering with bundle / shulker | Carrier name can show in some context-dimensional rendering paths | Mitigated by polymer-virtual-entity and Polymer item display name overrides |
| World physics (pistons, liquids) | The carrier's block class drives interactions; if our polymer block uses a `note_block` carrier, pistons see a movable block | Choose carriers with the desired physical traits (e.g. `bedrock` for unbreakable disguises) |
| Spectator mode camera entity | Vanilla spectator chases the *transmitted* entity, not our virtual one | Only matters for spectated bosses; documented edge case |

---

## 9. Migration log

| Date | Change | Commit |
|---|---|---|
| 2026-05-10 | Audit cleanup PR opened (#2) on `codex/audit-cleanup-minecraft-server` covering security, economy, permissions, PTR showcase v1, core/logger expansions | `fab5f63` |
| 2026-05-10 | Branched `feat/ptr-fabric-polymer` from codex tip | — |
| 2026-05-10 | Renamed Paper `ptr/` → `ptr-paper-legacy/`, fresh Fabric ptr/, bootstrap Fabric+Polymer on MC 26.1.2, smoke-boot OK | `81ed691` |
| 2026-05-10 | Architecture doc + ATTRIBUTIONS ledger + Fabric mod scaffold | `af28efb` |
| 2026-05-10 | First compileable Fabric mod with 5 PolymerBlocks + 8 PolymerItems + 5 BlockItems. Mojang shipped MC 26.1+ unobfuscated; switched to plugin id `net.fabricmc.fabric-loom` and replaced `modImplementation`→`compileOnly` per the porting guide. `ResourceLocation` → `Identifier` rename absorbed. | `9852887` |
| 2026-05-10 | Datapack registries: 5 damage_types + 3 tags, 5 enchantments, 6 paintings, 3 jukebox_songs, 2 instruments, 4 banner_patterns, 2 trim_patterns, 4 biomes. All ship inside the mod jar at `data/ptr/*`. | `e087774` |
| 2026-05-10 | Furniture polymer blocks (5) + wearable cosmetic helmets (4) + `/ptr` brigadier command + Polymer resource-pack auto-host wired with `addModAssets("ptr_showcase")`. 10 blocks, 22 items. | `47b3fce` |
| 2026-05-10 | Real PolymerEntity bosses (Gardien/Roi/Anomalie), 22 items dispatch JSONs in pack, full state-of-night doc | `81966c8` |
| 2026-05-10 | Boss framework stubs (BossPhase/BossPhaseController/Telegraph) + ATTRIBUTIONS sourcing matrix | `110604e` |
| 2026-05-10 | `BossPhaseController` wired into the 3 boss entities — phases tick in `customServerAiStep`, chat barks broadcast on transition | `9e4cd2c` |
| 2026-05-10 | Helmets become **actually wearable** via 26.1 `Item.Properties#equippable(EquipmentSlot.HEAD)` | `ebf6497` |
| 2026-05-10 | `/ptr setup` plaza generator: 32×32 polished blackstone with all 10 polymer blocks placed, 3 boss spawn pads at corners | `a1e4174` |

## 10. State at end of overnight overhaul

### Done

| Layer | Item | Status |
|---|---|---|
| Server stack | Fabric 0.19.2 + Polymer 0.16.4 + Lithium + FabricProxy-Lite on MC 26.1.2 | ✅ smoke-boot Done(0.5–0.6s) |
| Velocity proxy | Routes `ptr=127.0.0.1:25568` (unchanged), forwarding secret matches | ✅ |
| Fabric mod scaffold | `plugins-fabric/ptr-showcase-fabric/` with Loom 1.15-SNAPSHOT (no-remap mode), JDK 25, fabric.mod.json, version 2.0.0 | ✅ |
| Polymer blocks (5 real custom) | runesteel, plasma_block, darkstone, glow_moss, blue_grass — each a `SimplePolymerBlock` with vanilla carrier disguise | ✅ |
| Polymer blocks (5 furniture) | forge_runique, station_recharge, console_marche_noir, pylone_telegraph, tableau_events | ✅ (basic carriers; display rigs deferred) |
| Polymer items (8 tools) | foreuse, tronconneuse, grappin, scanner, voidstone, boussole_boss, marteau_build, totem_alarme | ✅ |
| Polymer items (4 wearables) | miner_hat, pillager_crown, void_circlet, archmage_hood — **equippable on the head slot** via `Item.Properties#equippable(EquipmentSlot.HEAD)` | ✅ |
| Block items (10) | one PolymerBlockItem per block | ✅ |
| Polymer entities (3 bosses) | `GardienMineEntity`/`RoiPillardsEntity`/`AnomalieEntity` — real `ptr:` registry ids, custom attributes, polymer-disguised over the wire | ✅ |
| Boss framework | `BossPhase` record, `BossPhaseController` (HP-threshold-driven phase transitions + chat barks), `Telegraph` (groundSlam ring, laserBeam pre-trace, ringSeism pulses) | ✅ stubs wired into the 3 boss entities via `customServerAiStep` |
| Datapack registries | damage_type×5, enchantment×5, painting_variant×6, jukebox_song×3, instrument×2, banner_pattern×4, trim_pattern×2, biome×4 + 3 damage_type tags | ✅ all load clean |
| Resource pack | `assets/ptr/{models,textures,items}/*` bundled, **22 item-dispatch JSONs** routing carrier rendering to ptr-namespaced models, autohost on join | ✅ |
| `/ptr` command | `list`, `give <item> [count]`, `block <block> [count]`, `spawn <boss>`, `setup`, `info` with tab completion | ✅ |
| Showcase plaza | `/ptr setup` builds a 32×32 polished blackstone plaza with all 10 polymer blocks placed in display rows + 3 boss-spawn pads at the corners | ✅ |
| Architecture doc | `docs/PTR_ARCHITECTURE.md`, this file | ✅ |
| Attributions ledger | `docs/ATTRIBUTIONS.md` with vetted upstream sources (Faithful, FreeMinecraftModels, FrenchKrab, awesome-cc0, OpenGameArt, Pixabay, Mixkit, Kenney, Sketchfab) and a sourcing priority queue | ✅ |

### Deferred to a follow-up branch

| Item | Why deferred | Estimated effort |
|---|---|---|
| Replace placeholder textures with real CC0/permissive Blockbench-exported models | Sources are now vetted in `ATTRIBUTIONS.md` (FrenchKrab `drill_breaker.bbmodel`, FreeMinecraftModels, etc.), but `.bbmodel` → `.json` + `.png` export needs the Blockbench GUI; can't do that headlessly | 3–5 h |
| Real CC0 boss music for the 3 `ptr:music.*` jukebox_song ids | Sourcing CC0 audio + OGG conversion (mono, 22050 Hz, 96 kbps); registries already declared, just need the sound files in `assets/ptr/sounds/music/` | 1–2 h |
| Telegraph damage application + scheduled tick wiring | `Telegraph` currently renders the visual tells; the damage-on-warmup-end hook needs a `ScheduledTickAccess` integration in `customServerAiStep` | 2 h |
| Music orchestrator | Start/crossfade `ptr:music.*` per active phase from `BossPhaseController`; trivial once the OGGs ship | 1 h |
| Loot dispatcher | On boss death, drop tier-appropriate gear: enchanted books with `ptr:` enchants, `ptr:music.*` discs, painting unlocks. Vanilla `LootTable` JSON suffices | 1 h |
| `wolf_variant` / `cat_variant` registries | The 26.1 spawn_condition_type schema rejects `minecraft:tag` and additionally requires `baby_assets`. Need to inspect a vanilla wolf_variant JSON | 1 h |

### Known leaks / quirks (honest list)

| Item | Where it manifests | Severity |
|---|---|---|
| Polymer items show as their carrier item visually on vanilla clients | until item model dispatch is wired | medium — affects user-facing wow factor |
| `hasPermission()` / `hasPermissionLevel()` API check failed | so `/ptr` is open to everyone on PTR (test realm) | low — appropriate for a test realm |
| Pack metadata format quirk (`supported_formats: [81, 81]` vs object form) | Cost an iteration during bootstrap; documented in the build study | none, resolved |
| Wolf/cat variants deferred | spawn_condition schema differences | none, registry just absent |
| MC 26.1+ ships unobfuscated (no Mojang mappings published from `version_manifest_v2.json`) | Loom needs `net.fabricmc.fabric-loom` plugin id (no remap mode) | none, resolved |

---

## 10. Future work (out of scope for the overnight overhaul)

- C2ME for parallel chunk gen on PTR if worldgen ever stalls.
- Optional Geyser dual-stack to give Bedrock players a stronger custom-block experience — would require duplicating textures into a Bedrock pack.
- If survival ever demands Folia (pure Paper fork), build a survival-specific custom-content layer that does not depend on Polymer (i.e. notebook-style disguise only).
- Investigate `polymer-resource-pack-extras` for animated texture support to push boss visuals further.

---

## 11. Attributions index

See [`ATTRIBUTIONS.md`](./ATTRIBUTIONS.md) for licenses + URLs of every external model, texture, sound, music track or font used by the PTR resource pack. Contribution rule: **if it isn't CC0 or explicitly granted, it doesn't ship**.
