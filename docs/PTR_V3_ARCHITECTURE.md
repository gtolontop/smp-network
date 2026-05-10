# PTR V3 — Architecture Decision Record (Folia + custom plugin)

> Written 2026-05-11 during the V3 foundation bootstrap. Supersedes
> [`PTR_ARCHITECTURE.md`](./PTR_ARCHITECTURE.md), which remains as the
> historical record of the Fabric+Polymer attempt (now archived under
> `ptr-fabric-legacy/`).

---

## 0. TL;DR

PTR V3 runs on **Folia 26.1.2** with a single in-house plugin
(`plugins/ptr-foundation/`). The Fabric+Polymer stack from the previous
overnight overhaul is retained read-only as `ptr-fabric-legacy/` /
`plugins-fabric-legacy/`. The Paper-era stack is `ptr-paper-legacy/`.

The trade-off vs Polymer is consciously taken: we lose real registry ids
for blocks/items/entities (everything goes back to vanilla disguises) in
exchange for:

- Folia's multi-region threading — a clear runway for the survival/PTR
  load profile.
- No mod-loader split: PTR and the rest of the network share the same
  plugin ecosystem.
- Standard Paper tooling end-to-end (paperweight-userdev, Adventure,
  Bukkit/Paper APIs).

---

## 1. What we accept losing vs Polymer

| Lost | Why it matters | Mitigation |
|---|---|---|
| Real `ptr:` block ids on the wire | WorldEdit / `//set` corrupts custom blocks back to their carrier | content layer must register placement in PDC + an SQLite placement table |
| Real `ptr:` item ids | crafting recipes pin to carrier material, so collisions are possible | every custom item carries a unique `ptr:item_id` PDC tag at creation |
| Real `ptr:` entity ids | bosses revert to "invisible base mob + ItemDisplay rig" V1-style | `DisplayMobCarrier` documents the pattern; the foundation ships zero bosses |
| Polymer auto-served resource pack | autohost is now our code to write | deferred to a later branch (`docs/V3_ROADMAP.md`) |

What we keep: every `ptr:` registry entry that Mojang made data-driven
(damage_type, enchantment, painting_variant, jukebox_song, instrument,
banner_pattern, trim_pattern, biome). The 35 entries built for the Fabric
attempt port one-to-one as a datapack — also on the roadmap.

---

## 2. Layer matrix (downward-only)

A layer never imports from a layer below it.

```
                            ┌─────────────────┐
        command (Brigadier) │ /ptrf root      │
                            └────────┬────────┘
                                     │
                            ┌────────┴────────┐
         boss             telemetry           │
   BossDefinition       PtrMetrics            │
   PhaseController      PtrAuditLog           │
   Telegraph(s)         PtrTelemetryService   │
   MusicOrchestrator                          │
   LootDispatcher                             │
                            ┌────────┴────────┐
        disguise            │ DisguiseCarrier │
        NoteBlock           │ DisguiseGuard…  │
        Mushroom            └────────┬────────┘
        Tripwire                     │
        LeafLitter                   │
        DisplayBlock                 │
        DisplayMob                   │
                            ┌────────┴────────┐
        storage             │ PtrDatabase     │
                            │ PtrRepository   │
                            │ PtrPdcCodec     │
                            │ PtrPdcKeys      │
                            └────────┬────────┘
                                     │
                            ┌────────┴────────┐
        registry            │ PtrRegistry<T>  │
                            │ Block/Item/Mob… │
                            └────────┬────────┘
                                     │
                            ┌────────┴────────┐
        config              │ PtrConfigSvc    │
                            │ PtrFoundCfg     │
                            └────────┬────────┘
                                     │
                            ┌────────┴────────┐
        platform            │ SchedulerSvc    │
                            │ PaperVerGuard   │
                            │ RegionLocator   │
                            └─────────────────┘
```

`PtrServices` is a service locator wired in `PtrFoundationPlugin#onEnable`
in this exact order; shutdown is LIFO.

---

## 3. Stack

| Layer | Component | Version |
|---|---|---|
| Runtime | Eclipse Temurin OpenJDK | 25.0.2+10 (bundled at `java/`) |
| Server | Folia | `folia-26.1.2-8.jar` (channel STABLE, 2026-05-06) |
| Velocity | unchanged — routes `ptr` → `127.0.0.1:25568` |
| Plugin entry | Paper Plugin API + Brigadier + `LifecycleEvents.COMMANDS` |
| Build | paperweight-userdev 2.0.0-beta.21, Gradle 9.4.1, Shadow 9.2.2 |
| Config | Configurate YAML 4.2 |
| Storage | HikariCP 5.1 + Flyway 11.0 + sqlite-jdbc 3.47 |
| Tests | JUnit 5.11 + AssertJ 3.27 + Mockito 5.14 |

Folia's `dev.folia:folia-api` is **not** declared as a separate Gradle
dependency: it shares the `paper-mojangapi` capability with `paper-api`
and Gradle refuses to resolve both. Paper-api already exposes Folia's
`RegionScheduler` / `GlobalRegionScheduler` / `AsyncScheduler` /
`Entity#getScheduler()` as shims, so the foundation compiles against
paper-api alone and runs unchanged on Folia.

---

## 4. Disguise carrier capacity at a glance

| Carrier | Capacity | Per-instance cost | Best for |
|---|---|---|---|
| NoteBlock | 800 states | 0 entities | solid mineable blocks (resource pack via instrument/note tuning) |
| Mushroom | 192 states | 0 entities | connecting blocks (face-bit identifier) |
| Tripwire | 127 states | 0 entities | flat decals, low collision |
| LeafLitter (26.1+) | 16 states | 0 entities | ground decals |
| DisplayBlock | unbounded (PDC) | 3 entities (BlockDisplay + barrier + Interaction) | furniture, machines, non-cubic decor |
| DisplayMob | unbounded (PDC) | 1 mob + ≥1 ItemDisplay | bosses, custom mob silhouettes |

Carriers explicitly **not** implemented in the foundation:

- `SCAFFOLDING` — too few states, physics resets aggressively.
- `CHORUS` — physics resets aggressively, fragile under entity traffic.

---

## 5. Foundation responsibilities (what ships)

```
plugins/ptr-foundation/
├── build.gradle.kts                 # paperweight, shadow, checkstyle
├── gradle/libs.versions.toml        # version catalog
├── config/checkstyle.xml            # bans Bukkit.getScheduler() + star imports
├── src/main/java/fr/smp/ptr/foundation/
│   ├── PtrFoundationPlugin.java     # init order, LIFO shutdown
│   ├── PtrServices.java             # in-house service locator
│   ├── TelegraphCatalogue.java
│   ├── platform/                    # §3.1 SchedulerService + PaperVersionGuard + RegionLocator
│   ├── config/                      # §3.2 hot-reload typed config (Configurate)
│   ├── storage/                     # §3.3 HikariCP/Flyway/SQLite + PDC codecs
│   ├── registry/                    # §3.4 PtrRegistry<T> + 5 empty sub-registries
│   ├── disguise/                    # §3.5 6 carriers + guard listener
│   ├── telemetry/                   # §3.7 metrics + audit log + telemetry service
│   ├── boss/                        # §3.8 BossDefinition + 4 telegraphs + music + loot
│   └── command/                     # §3.6 /ptrf brigadier root
└── src/main/resources/
    ├── paper-plugin.yml
    ├── config/foundation.yml
    └── db/migration/V001__init.sql
```

What does **not** ship in the foundation:

- Zero blocks, zero items, zero mobs, zero bosses, zero enchantments,
  zero damage types, zero loot tables, zero arenas, zero datapacks, zero
  resource pack assets.
- No Paper plugin from `plugins/` (core-paper / anticheat-paper /
  smp-logger / ptr-showcase / core-velocity) is touched; those are
  reserved for lobby + survival.

---

## 6. Foundation expectations on the operator

The Folia backend must run with `config/paper-global.yml`'s
`block-updates.disable-noteblock-updates: true` and
`block-updates.disable-mushroom-block-updates: true`. The
`NoteBlockCarrier` and `MushroomCarrier` documentation calls this out;
without it, every neighbour update tears their disguise.

Velocity forwarding is wired through `proxies.velocity.secret` in
`paper-global.yml`, mirroring `velocity/forwarding.secret`.

---

## 7. Open trade-offs accepted in this branch

| Decision | Why | Reversible? |
|---|---|---|
| Spotless disabled | google-java-format / palantir-java-format 1.25–2.50 throw `NoSuchMethodError` on JDK 25's javac internals | yes — re-enable once gjf ships a JDK 25 build (`V3_ROADMAP.md`) |
| Telegraphs use Bukkit `world.spawnParticle` instead of NMS `ClientboundBundlePacket` | NMS BundlePacket optimisation can be a drop-in replacement on the `Telegraph` interface | yes — interface unchanged when migrated |
| No `dev.folia:folia-api` gradle dep | resolves capability conflict with paper-api | reversible if paperweight ever stops shipping the Folia shims in paper-api |
| `BlockDisplay`-based carriers spawn 3 entities per placement | trade-off of unbounded capacity vs entity budget — caller's problem to throttle | content layer can switch to a Mushroom carrier for cubic blocks |

---

## 8. Migration log (so far)

| Date | Change | Commit |
|---|---|---|
| 2026-05-10 | Branched `feat/ptr-v3-folia-foundation` from `master` | first commit |
| 2026-05-10 | Renamed `ptr/` → `ptr-fabric-legacy/`, `plugins-fabric/` → `plugins-fabric-legacy/`, archived start scripts under `scripts/legacy/` | `2c7760a` |
| 2026-05-10 | Bootstrapped Folia 26.1.2 backend in `ptr/` (jar, server.properties, paper-global.yml, start scripts, README) | `87a96da` |
| 2026-05-10 | Bootstrapped Gradle build for `plugins/ptr-foundation/` (paperweight, shadow, checkstyle, JUnit5, CI workflow) | `4d14818` |
| 2026-05-10 | Wrote every foundation layer (platform → command) with empty registries and zero content | `ac7bd47` |
| 2026-05-11 | Tests + Hikari connectionInitSql for SQLite PRAGMAs + JDK 25 `--release 25` | `580aa1c` |
