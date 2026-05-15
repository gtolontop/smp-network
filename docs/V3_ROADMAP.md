# PTR V3 — roadmap after SMP Creation Kit

SMP Creation Kit now ships only reusable base systems:

- Folia-safe scheduler wrapper and version guard.
- Hot-reload YAML config.
- SQLite/Flyway storage and audit log.
- Empty registries for future blocks, items, mobs, enchants, damage types, and skills.
- API facades plus Bukkit events.
- Disguise carriers.
- Skill vocabulary and simple mechanics.
- Telemetry and `/ptrf` diagnostics.

It intentionally does **not** ship ModelEngine-like animation code, resource
pack assets, pack autohosting, boss helpers, loot leaderboards, crates, maps, or
any gameplay content.

## Next Branches

1. **Content plugin skeletons.** Create separate modules for actual gameplay
   experiments instead of growing the foundation: crates, mobs, bosses, shops,
   or resource-pack-backed items each get their own layer.

2. **Datapack/resource-pack strategy.** Decide later whether PTR content uses a
   hosted pack, a generated pack, ItemsAdder, or another asset pipeline. The
   creation kit should not own that decision.

3. **Concrete registries.** Once the content split exists, register real
   `PtrBlockDef`, `PtrItemDef`, `PtrMobDef`, and skill entries from those
   plugins.

4. **Operator tooling.** Useful additions to `/ptrf`:
   - `/ptrf debug scheduler`
   - `/ptrf debug carrier <kind> <location>`
   - `/ptrf audit tail [n]`

5. **Formatting.** Re-enable Spotless once google-java-format or
   palantir-java-format ships a JDK 25-compatible build.

## Deferred

- Boss frameworks and loot dispatchers belong in a future content module.
- Blockbench / ModelEngine parsing belongs in a future rendering/content module.
- Resource-pack zipping and hosting belongs in a future asset module.
- Large maps and imported datapacks stay out of SMP Creation Kit.
