# Folia pitfalls — running notes

> Append to this file every time the foundation (or a content layer)
> trips over a Folia-specific behaviour. Each entry should describe the
> trap, the fix, and which API to use instead.

## 1. Region ownership is dynamic — never cache `isOwnedByCurrentRegion`

Folia merges and splits regions at runtime to balance load. A region you
own when you stash the answer may no longer be yours next tick.

- ❌ Don't: `private final boolean owned = Bukkit.isOwnedByCurrentRegion(loc);`
- ✅ Do: call `RegionLocator.isOwnedByCurrentRegion(loc)` at every entry
  point that mutates state.

## 2. `Bukkit.getScheduler()` is banned

The legacy Bukkit scheduler exists for backward compatibility but its
contract — "all tasks see a single main thread" — is false on Folia.
Tasks scheduled there run at unpredictable times relative to region
work and silently violate region-thread invariants.

- ❌ Don't: `Bukkit.getScheduler().runTask(plugin, ...);`
- ✅ Do: use `SchedulerService` (registered as a foundation service).
  The checkstyle config rejects the bad pattern at build time.

## 3. Four schedulers, pick the matching one

Folia has four schedulers and each touches a distinct lane of state:

| Scheduler | When to use | Foundation helper |
|---|---|---|
| `RegionScheduler` | mutating blocks / entities / chunks tied to a specific location | `SchedulerService#runOnRegion(Location, …)` |
| `EntityScheduler` (`Entity#getScheduler()`) | following a specific entity (it might cross regions mid-task) | `SchedulerService#runOnEntity(Entity, …)` |
| `GlobalRegionScheduler` | world-wide state (worldborder, weather, time, server-wide broadcasts) | `SchedulerService#runOnGlobal(…)` |
| `AsyncScheduler` | pure CPU / I/O off any tick thread | `SchedulerService#runAsync(…)` |

## 4. 26.1 NMS / paperweight rename pitfalls

These caught the Fabric attempt and apply identically under paperweight:

- `ResourceLocation` is now `Identifier` in some contexts (Mojang
  mappings on 26.1 unified the package). Search `Identifier` first.
- `Pillager` lives under `monster.illager.` and `WitherSkeleton` under
  `monster.skeleton.` — both moved out of `monster.` directly.
- `BlockBehaviour.Properties#setId(ResourceKey)` is now required before
  instantiation; the class refuses to construct without it.
- `Item.Properties#setId(ResourceKey)` likewise.
- `EntityType.Builder#build(ResourceKey)` — the `String` overload is
  gone.
- `Level#isClientSide()` is a method again, not a public field. Code
  ported from 1.20.x snapshots that accessed `level.isClientSide` will
  fail to compile.

## 5. Brigadier commands need the Paper lifecycle API

The legacy `getCommand("ptrf").setExecutor(...)` path does not exist on
the Paper Plugin API path that Folia uses for first-class command
support.

- ✅ Do:

  ```java
  getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
      event.registrar().register(myRootNode, "description", List.of("alias1"));
  });
  ```

## 6. Mixed transactional / non-transactional SQL in one Flyway migration

SQLite `PRAGMA journal_mode = WAL;` and friends are non-transactional.
Flyway refuses to put them in the same migration as transactional
`CREATE TABLE`. Set PRAGMAs via Hikari's `connectionInitSql` instead —
this is how `PtrDatabase` is wired.

## 7. `LivingEntity#setMaxHealth` is deprecated

Use the attribute API:

```java
LivingEntity e = ...;
AttributeInstance maxHp = e.getAttribute(Attribute.MAX_HEALTH);
if (maxHp != null) maxHp.setBaseValue(value);
```

## 8. `Player#getLuck()` no longer exists

Use the attribute API:

```java
AttributeInstance attr = player.getAttribute(Attribute.LUCK);
float luck = attr == null ? 0.0f : (float) attr.getValue();
```
