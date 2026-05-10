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
| 2026-05-10 | Renamed Paper `ptr/` → `ptr-paper-legacy/`, fresh Fabric ptr/ | `81ed691` |
| 2026-05-10 | Bootstrap Fabric+Polymer stack on MC 26.1.2, smoke-boot OK | `81ed691` |
| 2026-05-10 | (in progress) Architecture doc + Fabric mod scaffold | this commit |

---

## 10. Future work (out of scope for the overnight overhaul)

- C2ME for parallel chunk gen on PTR if worldgen ever stalls.
- Optional Geyser dual-stack to give Bedrock players a stronger custom-block experience — would require duplicating textures into a Bedrock pack.
- If survival ever demands Folia (pure Paper fork), build a survival-specific custom-content layer that does not depend on Polymer (i.e. notebook-style disguise only).
- Investigate `polymer-resource-pack-extras` for animated texture support to push boss visuals further.

---

## 11. Attributions index

See [`ATTRIBUTIONS.md`](./ATTRIBUTIONS.md) for licenses + URLs of every external model, texture, sound, music track or font used by the PTR resource pack. Contribution rule: **if it isn't CC0 or explicitly granted, it doesn't ship**.
