# SMP PTR Foundation

Clean Folia base for future PTR creation plugins. This module owns only the
stable plumbing: Folia-safe scheduling, hot-reload config, SQLite audit
storage, empty registries, disguise carriers, telemetry, API facades, and the
`/ptrf` operator command.

It deliberately ships zero gameplay content: no bosses, no crates, no maps, no
resource pack, no ModelEngine-style runtime, no loot tables, no arenas.

## Creation Flow

### 1. Register a definition

```java
PtrBlockRegistry blocks = plugin.services().get(PtrBlockRegistry.class);
blocks.register(
    PtrBlockDef.builder()
        .id("runesteel_ore")
        .displayName(Component.text("Minerai de runesteel", NamedTextColor.AQUA))
        .build());
```

Definitions are intentionally small in the foundation. Content plugins add
their own fields or wrap the foundation records.

### 2. Place a disguise carrier

```java
NoteBlockCarrier carrier = plugin.services().get(NoteBlockCarrier.class);
carrier.place(loc,
    new NoteBlockCarrier.State(Instrument.PIANO, new Note(7), false));
```

Capacity and known leaks for every carrier live in
[`disguise/DisguiseCarrier`](src/main/java/fr/smp/ptr/foundation/disguise/DisguiseCarrier.java).
Read the carrier `knownLeaks()` before choosing it.

### 3. Schedule region-aware work

```java
SchedulerService sched = plugin.services().get(SchedulerService.class);
sched.runOnRegion(loc, () -> {
    // touches blocks / entities at loc, Folia-safe
});

sched.runAsync(() -> {
    // pure CPU / I/O only
});
```

`Bukkit.getScheduler()` is banned at build time by checkstyle.

## Build

```sh
./gradlew build
./gradlew test
./gradlew shadowJar
```

Output: `build/libs/PtrFoundation-0.1.0.jar`. Drop it into `ptr/plugins/`.

CI workflow: [`.github/workflows/build-ptr-foundation.yml`](../../.github/workflows/build-ptr-foundation.yml).

## Layout

```
src/main/java/fr/smp/ptr/foundation/
├── PtrFoundationPlugin.java       # init order, LIFO shutdown
├── PtrServices.java               # service locator
├── platform/                      # Folia schedulers, version guard, region helper
├── config/                        # PtrConfigService (Configurate, hot-reload)
├── storage/                       # PtrDatabase + PDC codecs
├── registry/                      # PtrRegistry<T> + empty sub-registries
├── disguise/                      # carriers + DisguiseGuardListener
├── skill/                         # reusable skill vocabulary/mechanics
├── telemetry/                     # PtrMetrics + PtrAuditLog + dump service
├── api/                           # public facades + Bukkit events
└── command/                       # /ptrf Brigadier root
```

## Caveats

- Spotless stays disabled until a JDK 25-compatible formatter release is
  available. Checkstyle covers the architectural rules meanwhile.
- `folia-api` is not declared separately because it conflicts with paper-api on
  the `paper-mojangapi` capability. Paper-api already exposes the Folia shims
  used here.

## More Context

- ADR for V3: [`../../docs/PTR_V3_ARCHITECTURE.md`](../../docs/PTR_V3_ARCHITECTURE.md)
- Folia notes: [`../../docs/FOLIA_PITFALLS.md`](../../docs/FOLIA_PITFALLS.md)
- Roadmap: [`../../docs/V3_ROADMAP.md`](../../docs/V3_ROADMAP.md)
