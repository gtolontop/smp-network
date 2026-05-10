# ptr-foundation

PTR V3 foundation plugin — Folia 26.1.2, single in-house plugin, zero
gameplay content. The foundation owns the platform layer (Folia
schedulers, region locator, version guard), the registries (empty),
the disguise carriers, the boss framework, the telemetry pipeline, and
the `/ptrf` Brigadier root.

Content layers (custom blocks/items/mobs/bosses) plug in here in a
follow-up branch — see [`../../docs/V3_ROADMAP.md`](../../docs/V3_ROADMAP.md).

## Onboarding in 5 minutes

The foundation hands content code three sets of primitives. Use them in
this order whenever you add something new.

### 1. Register a definition

```java
PtrBlockRegistry blocks = plugin.services().get(PtrBlockRegistry.class);
blocks.register(
    PtrBlockDef.builder()
        .id("runesteel_ore")
        .displayName(Component.text("Minerai de runesteel", NamedTextColor.AQUA))
        .build());
```

Definitions are minimal in the foundation. Add fields (carrier type,
attributes, attributes…) by subclassing the def record or wrapping it.

### 2. Place a disguise carrier

```java
NoteBlockCarrier carrier = plugin.services().get(NoteBlockCarrier.class);
carrier.place(loc,
    new NoteBlockCarrier.State(Instrument.PIANO, new Note(7), false));
```

Capacity & known leaks for every carrier live in
[`disguise/DisguiseCarrier`](src/main/java/fr/smp/ptr/foundation/disguise/DisguiseCarrier.java)
javadoc. Don't pick a carrier without reading its `knownLeaks()` first —
some require `paper-global.yml` flags that the operator may not have set.

### 3. Schedule region-aware work

```java
SchedulerService sched = plugin.services().get(SchedulerService.class);
sched.runOnRegion(loc, () -> {
    // touches blocks / entities at loc — Folia-safe
});

sched.runAsync(() -> {
    // pure CPU / I/O — never call Bukkit state mutators here
});
```

`Bukkit.getScheduler()` is banned at build time (checkstyle).

### 4. Add a boss (when content lands)

```java
BossDefinition def = BossDefinition.builder()
    .id(PtrIds.key("gardien_mine"))
    .displayName(Component.text("Gardien de la Mine"))
    .baseEntityType(EntityType.IRON_GOLEM)
    .attribute(Attribute.MAX_HEALTH, 500.0)
    .attribute(Attribute.ATTACK_DAMAGE, 12.0)
    .addPhase(new BossPhase(
        500.0,
        Component.text("ouverture"),
        List.of(PtrIds.key("telegraph/ground_slam_ring")),
        PtrIds.key("music.gardien_theme"),
        e -> {},   // onEnter
        e -> {}))  // onTick
    .arenaRadius(32.0)
    .build();
```

The phase controller is region-aware and hooks the entity scheduler:

```java
PhaseController ctrl = new PhaseController(
    def,
    spawnedBossEntity,
    sched,
    audienceOfNearbyPlayers);
ctrl.start();
```

## Build

```sh
./gradlew build           # compile + shadow + tests + checkstyle
./gradlew test            # tests only
./gradlew shadowJar       # the runnable plugin jar
```

Output: `build/libs/PtrFoundation-0.1.0.jar`. Drop it into
`ptr/plugins/`.

CI workflow: [`.github/workflows/build-ptr-foundation.yml`](../../.github/workflows/build-ptr-foundation.yml).

## Layout

```
src/main/java/fr/smp/ptr/foundation/
├── PtrFoundationPlugin.java       # init order, LIFO shutdown
├── PtrServices.java               # service locator
├── TelegraphCatalogue.java
├── platform/                      # Folia schedulers, version guard, region helper
├── config/                        # PtrConfigService (Configurate, hot-reload)
├── storage/                       # PtrDatabase (HikariCP + Flyway + SQLite) + PDC codecs
├── registry/                      # PtrRegistry<T> + 5 empty sub-registries
├── disguise/                      # 6 carriers + DisguiseGuardListener
├── telemetry/                     # PtrMetrics + PtrAuditLog + dump service
├── boss/                          # BossDefinition + 4 telegraphs + music + loot
└── command/                       # /ptrf Brigadier root
```

## Caveats

- **Spotless disabled** because google-java-format / palantir-java-format
  versions current as of 2026-05-11 throw `NoSuchMethodError` on JDK 25's
  javac internals. Checkstyle (`config/checkstyle.xml`) covers the
  architectural rules.
- **`folia-api` not a separate gradle dep** — it conflicts with paper-api
  on the `paper-mojangapi` capability. Paper-api ships Folia shims, so
  the plugin builds against paper alone and runs unchanged on Folia.
- **Telegraphs use Bukkit `world.spawnParticle`**, not NMS bundles, in
  the foundation. NMS migration is on the roadmap.

## Where to read more

- ADR for V3: [`../../docs/PTR_V3_ARCHITECTURE.md`](../../docs/PTR_V3_ARCHITECTURE.md)
- Folia gotchas notebook: [`../../docs/FOLIA_PITFALLS.md`](../../docs/FOLIA_PITFALLS.md)
- What's still to do: [`../../docs/V3_ROADMAP.md`](../../docs/V3_ROADMAP.md)
- Historical Fabric+Polymer ADR: [`../../docs/PTR_ARCHITECTURE.md`](../../docs/PTR_ARCHITECTURE.md)
