# PTR V3 — roadmap after the foundation

The foundation branch (`feat/ptr-v3-folia-foundation`) ships the
plumbing. None of this list ships there.

## High-priority (next branch)

1. **Port the 35 datapack entries** from
   `plugins-fabric-legacy/ptr-showcase-fabric/src/main/resources/data/ptr/`
   into a datapack served at `ptr/datapacks/ptr_content/` or via a
   resource-pack-equivalent loader. Stack-agnostic JSON, copy verbatim:
   5 damage_type, 5 enchantment, 6 painting, 3 jukebox_song,
   2 instrument, 4 banner, 2 trim, 4 biome.

2. **Resource-pack autohost replacement.** Polymer used to do this; in
   the plugin model it's our code. Options:
   - Reuse the existing `dist/ptr-resourcepack.zip` GitHub-raw flow that
     the legacy server uses.
   - Embed a tiny Netty HTTP server inside the foundation plugin.
   The decision depends on whether we want hot-reload of the pack.

3. **Concrete content registration.** The cahier des charges
   (`CAHIER_DES_CHARGES_MINECRAFT_V3.md`) specifies tools, items,
   blocks, enchants, potions, bosses. Each becomes a `PtrXxxDef`
   registered into the matching `PtrRegistry`.

## Medium-priority (own branches)

4. **NMS `ClientboundBundlePacket` migration for Telegraph impls.** The
   current `Telegraph` implementations use Bukkit's
   `world.spawnParticle` which fans out as one packet per particle. A
   single NMS bundle per frame is the documented optimisation;
   interfaces stay unchanged.

5. **Spotless re-enable.** Once google-java-format or palantir-java-format
   publish a JDK 25 build that doesn't `NoSuchMethodError` on
   `Log$DeferredDiagnosticHandler`, re-add Spotless to
   `plugins/ptr-foundation/build.gradle.kts`. Track upstream:
   - https://github.com/google/google-java-format/issues
   - https://github.com/palantir/palantir-java-format/issues

6. **`/ptrf` debug surface widening.** The foundation ships `info`,
   `reload`, `registry list`, `registry dump`, `debug region`, `debug
   pdc`. Useful additions:
   - `/ptrf debug scheduler` — counts of pending tasks per scheduler.
   - `/ptrf debug carrier <kind> <location>` — verify a placement.
   - `/ptrf audit tail [n]` — read recent `ptr_audit` rows.

## Low-priority / nice-to-have

7. **Folia API as separate dep.** If paperweight ever drops the Folia
   scheduler shims from paper-api, declare `dev.folia:folia-api`
   explicitly. Today it conflicts on the `paper-mojangapi` capability.

8. **`LeafLitterCarrier` live state read.** The current impl writes
   leaf_litter via `setType` and stores `(segments, facing)` in PDC; it
   doesn't yet read it back from `BlockData`. Wait for paperweight to
   stabilise the leaf_litter `BlockData` API surface.

9. **`SCAFFOLDING` / `CHORUS` carriers.** Rejected for the foundation
   because their state count is small and their physics are aggressive.
   If a future content layer needs them, reopen the analysis.

10. **Spectator camera fix for `DisplayMobCarrier`.** Spectator locks on
    to the invisible base mob, not the ItemDisplay rig. There is no
    server-side fix; needs a client-side hint or accepted as a leak.

11. **Folia-aware permission probe.** LuckPerms is the de-facto perm
    backend. Confirm the existing lobby/survival LuckPerms install
    forwards correctly to the PTR backend over Velocity plugin messages.

## Deferred from the Fabric attempt (still relevant)

- Telegraph damage application + scheduled tick wiring (currently the
  telegraphs render visuals only; damage is the content layer's job).
- Music orchestrator → start/crossfade `ptr:music.*` per active phase.
- Loot dispatcher → wire to a per-boss loot table.
- `wolf_variant` / `cat_variant` datapack entries — the 26.1 schema
  rejects the schema we built for Fabric (`minecraft:tag` not allowed,
  `baby_assets` required). Cross-reference a vanilla wolf_variant JSON.
