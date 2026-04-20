package fr.smp.anticheat.xray;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.config.AntiCheatConfig;
import fr.smp.anticheat.visibility.VisibilityEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.block.CraftBlockType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anti-xray module with per-environment aggressiveness profiles.
 *
 * Strategy v1:
 *   - Chunk packets are forwarded unchanged (no body rewrite — palette/byte-buffer
 *     surgery is deferred to v2 to avoid client-side corruption risk).
 *   - On chunk receipt, the module schedules a main-thread scan one tick later,
 *     finds hidden-block positions in configured Y-range, registers them with the
 *     VisibilityEngine, and sends an immediate batch BlockUpdate to mask each
 *     with a deterministic stone/deepslate replacement.
 *   - VisibilityEngine reveals the original block when LoS AND distance ≤ profile
 *     reveal-distance. If LoS is achieved but the player is still far, the block
 *     stays masked. This is the "ultra aggressive" Nether mode.
 *   - Optional fake-ore injection sprays sparse fake hidden blocks for paranoid
 *     mode (defeats seed-based xray detection).
 *
 * Outbound BlockUpdate packets that contain hidden blocks in non-LoS positions
 * are also rewritten on the fly (from explosions, /setblock, physics updates, etc.).
 */
public final class XrayModule {

    private final AntiCheatPlugin plugin;
    private final AntiCheatConfig cfg;
    private final VisibilityEngine visibility;

    private final ConcurrentMap<UUID, ConcurrentMap<Long, Material>> playerHidden = new ConcurrentHashMap<>();
    // Positions we are currently revealing. The outbound filter consults this set on
    // the Netty thread and lets the packet pass through unchanged (i.e. no re-mask).
    // Without this, a reveal BlockUpdatePacket sent from onVisibilityChange / reveal-
    // OnInteract gets intercepted by rewriteBlockUpdate, and if hasOpenFace happens
    // to read the world in a pre-break state OR isRevealed's cachedLoS hasn't been
    // populated yet (cold watch on first reveal), the packet is re-masked and the
    // client keeps seeing netherrack. Happened in the nether because ancient_debris
    // sites are often cold watches: no prior LoS sample before the user broke a
    // neighbor, so cachedLoS was null and the filter re-masked the reveal packet.
    private final ConcurrentMap<UUID, java.util.Set<Long>> pendingReveals = new ConcurrentHashMap<>();
    private BukkitTask revealTask;

    public XrayModule(AntiCheatPlugin plugin, AntiCheatConfig cfg, VisibilityEngine visibility) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.visibility = visibility;
    }

    public boolean enabled() {
        return cfg.xrayEnabled();
    }

    /**
     * Start the distance/LoS reconciler. Runs every {@code xrayReconcileTicks} ticks
     * (config, default 4 = 200 ms) because cave-walking doesn't emit block events —
     * polling is the only way a reveal can fire. 4 ticks is imperceptible and cuts
     * the reconcile cost 4× vs. the old per-tick loop. The reconcile itself is now
     * chunk-scoped (only watches in chunks within revealDistance of the player are
     * considered) so total work is O(chunksInRange × oresPerChunk) instead of
     * O(allWatchesForPlayer).
     */
    public void start() {
        if (revealTask != null) return;
        int period = Math.max(1, cfg.xrayReconcileTicks());
        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickReveal, period, period);
    }

    public void stop() {
        if (revealTask != null) {
            revealTask.cancel();
            revealTask = null;
        }
    }

    private void tickReveal() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (plugin.bypass().isBypassed(player)) continue;
                XrayProfile profile = cfg.xrayProfile(player.getWorld().getEnvironment());
                if (!profile.enabled) continue;
                reconcilePlayer(player, profile);
            } catch (Throwable t) {
                plugin.getLogger().fine("tickReveal error for " + player.getName() + ": " + t.getMessage());
            }
        }
    }

    private void reconcilePlayer(Player player, XrayProfile profile) {
        VisibilityEngine.PlayerView view = visibility.view(player);
        if (view.byChunk.isEmpty()) return;
        UUID id = player.getUniqueId();
        ConcurrentMap<Long, Material> map = playerHidden.get(id);
        double px = player.getX(), py = player.getY() + 1.5, pz = player.getZ();
        double rd = profile.revealDistance;
        double rd2 = rd * rd;
        long now = System.currentTimeMillis();
        int freshBudget = 32;

        // Chunk-scoped iteration: only watches in chunks close enough to matter. Far
        // chunks stay masked (client still sees the block as stone, and when the
        // player leaves / re-enters a chunk we re-run scanAndMask on it anyway).
        // Margin of +2 chunks past the reveal-distance chunk radius accommodates fast
        // sprint movement between reconcile cycles without popping ores late.
        int pcx = player.getLocation().getBlockX() >> 4;
        int pcz = player.getLocation().getBlockZ() >> 4;
        int chunkR = (int) Math.ceil(rd / 16.0) + 2;

        for (int dcx = -chunkR; dcx <= chunkR; dcx++) {
            for (int dcz = -chunkR; dcz <= chunkR; dcz++) {
                var set = view.byChunk.get(VisibilityEngine.chunkKey(pcx + dcx, pcz + dcz));
                if (set == null || set.isEmpty()) continue;
                for (long key : set) {
                    VisibilityEngine.Watched w = view.watched.get(key);
                    if (w == null) continue;
                    int wx = VisibilityEngine.unpackX(key);
                    int wy = VisibilityEngine.unpackY(key);
                    int wz = VisibilityEngine.unpackZ(key);
                    double dx = px - (wx + 0.5), dy = py - (wy + 0.5), dz = pz - (wz + 0.5);
                    double d2 = dx * dx + dy * dy + dz * dz;
                    boolean inRange = d2 <= rd2;
                    boolean cachedLos = w.lastVisible;
                    boolean isMasked = map != null && map.containsKey(key);

                    if (!inRange) {
                        if (!isMasked) onVisibilityChange(player, profile, wx, wy, wz, false);
                        continue;
                    }

                    if (isMasked && cachedLos) {
                        onVisibilityChange(player, profile, wx, wy, wz, true);
                        continue;
                    }

                    if (!isMasked && cachedLos) continue;

                    // cachedLos == false. Fresh-raytrace guarded by a small budget so one
                    // player in a dense ore field doesn't monopolise the main thread.
                    if (freshBudget <= 0) continue;
                    freshBudget--;
                    boolean fresh = visibility.hasLineOfSight(player, wx, wy, wz);
                    w.lastVisible = fresh;
                    w.lastChecked = now;

                    if (fresh == !isMasked) continue;
                    onVisibilityChange(player, profile, wx, wy, wz, fresh);
                }
            }
        }
    }

    public ClientboundLevelChunkWithLightPacket rewriteChunk(Player player, ClientboundLevelChunkWithLightPacket pkt) {
        XrayProfile profile = cfg.xrayProfile(player.getWorld().getEnvironment());
        if (!profile.enabled) return pkt;
        int chunkX = pkt.getX();
        int chunkZ = pkt.getZ();
        UUID id = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> scanAndMask(player, id, profile, chunkX, chunkZ));
        return pkt;
    }

    public ClientboundBlockUpdatePacket rewriteBlockUpdate(Player player, ClientboundBlockUpdatePacket pkt) {
        try {
            XrayProfile profile = cfg.xrayProfile(player.getWorld().getEnvironment());
            if (!profile.enabled) return pkt;
            BlockState state = pkt.getBlockState();
            Material mat = CraftBlockType.minecraftToBukkit(state.getBlock());
            if (!profile.hiddenBlocks.contains(mat)) return pkt;
            BlockPos pos = pkt.getPos();
            int wx = pos.getX(), wy = pos.getY(), wz = pos.getZ();
            if (wy < profile.minY || wy > profile.maxY) return pkt;

            long key = VisibilityEngine.pack(wx, wy, wz);
            UUID id = player.getUniqueId();

            // We just sent this packet from onVisibilityChange/revealOnInteract as an
            // authoritative reveal. Pass it through untouched and clear the flag so a
            // subsequent genuine server update for the same pos re-enters the normal
            // masking path. This is what lets mining-next-to-buried-ancient-debris
            // reveal work in the nether — before the flag, the filter's cachedLoS
            // check could return false (cold watch) and re-mask the reveal back to
            // netherrack, which was exactly the reported symptom.
            java.util.Set<Long> pending = pendingReveals.get(id);
            if (pending != null && pending.remove(key)) {
                ConcurrentMap<Long, Material> existing = playerHidden.get(id);
                if (existing != null) existing.remove(key);
                return pkt;
            }

            // Short-circuit: if the block has any face touching a non-occluding neighbor
            // (air/water/glass/etc.) we treat it as naturally visible and skip masking.
            // A player who places a spawner/chest can't put it inside solid rock — so
            // placed blocks always hit this path and stay visible without any raytrace
            // dance. Also fixes the "I place from an angle, block shows as stone" bug
            // where a subsequent LoS raytrace false-negative would re-mask it.
            if (!profile.maskCaveOres && hasOpenFaceFromLevel(player, wx, wy, wz)) {
                ConcurrentMap<Long, Material> existing = playerHidden.get(id);
                if (existing != null) existing.remove(key);
                return pkt;
            }

            // If the position is already both in-range AND has cached LoS, let the real
            // block through. Safe against a placed ore showing up for one frame.
            if (isRevealed(player, profile, wx, wy, wz)) {
                ConcurrentMap<Long, Material> existing = playerHidden.get(id);
                if (existing != null) existing.remove(key);
                return pkt;
            }

            // Mask + register a visibility watch so future LoS changes / distance changes
            // are reconciled by the tick loops. Without this, a /setblock or player-placed
            // spawner/chest was masked once and then stuck as stone forever because no
            // watch existed for the engine to reveal it later.
            ConcurrentMap<Long, Material> map =
                    playerHidden.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
            Material rep = pickReplacement(player, wx, wy, wz);
            Material prev = map.putIfAbsent(key, rep);
            if (prev == null) {
                final int fwx = wx, fwy = wy, fwz = wz;
                visibility.watch(player, key,
                        (visible, k) -> onVisibilityChange(player, profile, fwx, fwy, fwz, visible));
            }
            BlockState fake = blockStateOf(rep);
            return new ClientboundBlockUpdatePacket(pos, fake);
        } catch (Throwable t) {
            return pkt;
        }
    }

    public ClientboundSectionBlocksUpdatePacket rewriteSectionUpdate(Player player, ClientboundSectionBlocksUpdatePacket pkt) {
        // Bulk physics updates rarely contain hidden blocks; not rewriting for v1.
        return pkt;
    }

    private boolean isRevealed(Player player, XrayProfile profile, int wx, int wy, int wz) {
        double dx = player.getX() - (wx + 0.5);
        double dy = player.getY() + 1.5 - (wy + 0.5); // approximate eye Y
        double dz = player.getZ() - (wz + 0.5);
        double dist2 = dx * dx + dy * dy + dz * dz;
        if (dist2 > profile.revealDistance * profile.revealDistance) return false;
        // Cached LoS only — called from Netty thread via rewriteBlockUpdate, so the
        // sync raytrace is off-limits (not async-safe). If the position is not yet
        // watched we conservatively return false ⇒ the block gets masked and a watch
        // is registered; the visibility tick will reveal it within ~1 tick if LoS ok.
        Boolean los = visibility.cachedLineOfSight(player, VisibilityEngine.pack(wx, wy, wz));
        return los != null && los;
    }

    private void scanAndMask(Player player, UUID id, XrayProfile profile, int chunkX, int chunkZ) {
        scanAndMask(player, id, profile, chunkX, chunkZ, 0);
    }

    private void scanAndMask(Player player, UUID id, XrayProfile profile, int chunkX, int chunkZ, int retryCount) {
        if (!player.isOnline()) return;
        ServerPlayer sp = ((CraftPlayer) player).getHandle();
        ServerLevel level = (ServerLevel) sp.level();
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            if (retryCount < 5) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> scanAndMask(player, id, profile, chunkX, chunkZ, retryCount + 1), 5L);
            } else if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[xray] chunk " + chunkX + "," + chunkZ + " never loaded for "
                        + player.getName() + " in " + level.dimension());
            }
            return;
        }

        int minY = Math.max(level.getMinY(), profile.minY);
        int maxY = Math.min(level.getMaxY(), profile.maxY);
        Set<Material> hiddenBlocks = profile.hiddenBlocks;

        ConcurrentMap<Long, Material> orePositions =
                playerHidden.computeIfAbsent(id, k -> new ConcurrentHashMap<>());

        List<long[]> packed = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        int chunkMinY = level.getMinY();
        int skippedExposed = 0;
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) continue;
            int baseY = chunkMinY + (si << 4);
            if (baseY + 16 < minY || baseY > maxY) continue;
            for (int dy = 0; dy < 16; dy++) {
                int wy = baseY + dy;
                if (wy < minY || wy > maxY) continue;
                for (int dz = 0; dz < 16; dz++) {
                    for (int dx = 0; dx < 16; dx++) {
                        BlockState state = sec.getBlockState(dx, dy, dz);
                        Block b = state.getBlock();
                        Material mat = CraftBlockType.minecraftToBukkit(b);
                        if (!hiddenBlocks.contains(mat)) continue;
                        int wx = (chunkX << 4) + dx;
                        int wz = (chunkZ << 4) + dz;
                        // Skip ores that already touch air — they are naturally visible
                        // in a cave, and masking them creates a "why is my cave all stone"
                        // UX for legitimate players. Xray is still blocked for any ore
                        // buried in solid rock (the common cheat target). Nether / end can
                        // opt back in via mask-cave-ores: true in config.
                        if (!profile.maskCaveOres && hasOpenFace(chunk, level, wx, wy, wz)) {
                            skippedExposed++;
                            continue;
                        }
                        long key = VisibilityEngine.pack(wx, wy, wz);
                        orePositions.put(key, mat);
                        packed.add(new long[]{wx, wy, wz});
                    }
                }
            }
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[xray] " + player.getName() + " env=" + player.getWorld().getEnvironment()
                    + " chunk=" + chunkX + "," + chunkZ + " profile-reveal=" + profile.revealDistance
                    + " profile-hidden-types=" + profile.hiddenBlocks.size()
                    + " masked=" + packed.size() + " exposed-skipped=" + skippedExposed);
        }

        if (packed.isEmpty()) return;

        for (long[] pos : packed) {
            int wx = (int) pos[0], wy = (int) pos[1], wz = (int) pos[2];
            Material rep = pickReplacement(player, wx, wy, wz);
            BlockState fake = blockStateOf(rep);
            sp.connection.send(new ClientboundBlockUpdatePacket(new BlockPos(wx, wy, wz), fake));

            long key = VisibilityEngine.pack(wx, wy, wz);
            visibility.watch(player, key, (visible, k) -> onVisibilityChange(player, profile, wx, wy, wz, visible));
        }

        if (profile.fakeOreInjection && profile.fakeOreDensity > 0) {
            injectFakeOres(player, sp, profile, chunk, chunkX, chunkZ, minY, maxY);
        }
    }

    private void injectFakeOres(Player player, ServerPlayer sp, XrayProfile profile, LevelChunk chunk,
                                int chunkX, int chunkZ, int minY, int maxY) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double density = profile.fakeOreDensity;
        Material[] choices = profile.hiddenBlocks.toArray(new Material[0]);
        if (choices.length == 0) return;
        int section = (maxY - minY) / 16;
        for (int i = 0; i < section * 16; i++) {
            if (rng.nextDouble() >= density) continue;
            int dx = rng.nextInt(16);
            int dz = rng.nextInt(16);
            int wy = minY + rng.nextInt(maxY - minY);
            int wx = (chunkX << 4) + dx;
            int wz = (chunkZ << 4) + dz;
            BlockPos bp = new BlockPos(wx, wy, wz);
            BlockState bg = chunk.getBlockState(bp);
            Material bgMat = CraftBlockType.minecraftToBukkit(bg.getBlock());
            if (bgMat != Material.STONE && bgMat != Material.DEEPSLATE && bgMat != Material.NETHERRACK) continue;
            Material fake = choices[rng.nextInt(choices.length)];
            sp.connection.send(new ClientboundBlockUpdatePacket(bp, blockStateOf(fake)));
        }
    }

    private void onVisibilityChange(Player player, XrayProfile profile, int wx, int wy, int wz, boolean visible) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            ServerLevel level = (ServerLevel) sp.level();
            BlockPos pos = new BlockPos(wx, wy, wz);
            boolean reveal = visible && isRevealed(player, profile, wx, wy, wz);
            ConcurrentMap<Long, Material> map = playerHidden.get(player.getUniqueId());
            long packedPos = VisibilityEngine.pack(wx, wy, wz);
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[xray-reveal] " + player.getName()
                        + " pos=(" + wx + "," + wy + "," + wz + ")"
                        + " env=" + player.getWorld().getEnvironment()
                        + " visible=" + visible
                        + " reveal=" + reveal
                        + " wasMasked=" + (map != null && map.containsKey(packedPos)));
            }
            if (reveal) {
                // IMPORTANT: remove from masked map BEFORE sending packets. The outbound
                // BE packet we are about to enqueue will be read back by this same filter
                // on the Netty thread (ContainerEspModule.rewriteBlockEntity), which
                // consults isMasked() to decide drop-or-pass. If we removed afterwards,
                // the BE packet would be dropped and e.g. a revealed spawner would show
                // as an empty cage because its spawn-entity NBT never reached the client.
                if (map != null) map.remove(packedPos);
                // Mark the reveal packet as authoritative so the filter doesn't re-mask.
                pendingReveals.computeIfAbsent(player.getUniqueId(),
                        k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(packedPos);
                sp.connection.send(new ClientboundBlockUpdatePacket(level, pos));
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    var update = be.getUpdatePacket();
                    if (update != null) sp.connection.send(update);
                }
            } else {
                // Re-mask path. Before we force stone over the block, double-check that
                // it's actually hidden — a false-negative raytrace after breaking a
                // neighbor would otherwise mask a block the player can still plainly
                // see. If the block has any air-facing face, it's naturally visible and
                // we must not mask it (bug: "I placed X, broke a block nearby, X
                // despawned into stone").
                if (!profile.maskCaveOres && hasOpenFaceFromLevel(player, wx, wy, wz)) {
                    if (map != null && map.remove(packedPos) != null) {
                        pendingReveals.computeIfAbsent(player.getUniqueId(),
                                k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(packedPos);
                        sp.connection.send(new ClientboundBlockUpdatePacket(level, pos));
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null) {
                            var update = be.getUpdatePacket();
                            if (update != null) sp.connection.send(update);
                        }
                    }
                    return;
                }
                Material rep = pickReplacement(player, wx, wy, wz);
                if (map != null) map.put(packedPos, rep);
                sp.connection.send(new ClientboundBlockUpdatePacket(pos, blockStateOf(rep)));
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("xray reveal failed: " + t.getMessage());
        }
    }

    private Material pickReplacement(Player player, int wx, int wy, int wz) {
        var env = player.getWorld().getEnvironment();
        List<Material> pool = switch (env) {
            case NETHER -> cfg.netherReplacements();
            case THE_END -> cfg.endReplacements();
            default -> cfg.overworldReplacements();
        };
        if (pool.isEmpty()) return Material.STONE;
        int idx = Math.floorMod(wx * 31 ^ wy * 17 ^ wz, pool.size());
        return pool.get(idx);
    }

    private static BlockState blockStateOf(Material mat) {
        return CraftBlockType.bukkitToMinecraft(mat).defaultBlockState();
    }

    public void clearPlayer(UUID id) {
        playerHidden.remove(id);
        pendingReveals.remove(id);
    }

    /**
     * Called from inbound packet handler when the player attempts to interact with
     * a block (mine / right-click). If the position is currently masked for this
     * player, immediately send a real-block + BE update so the client and server
     * agree on the block before the action is processed. Eliminates ghost-block
     * desync (chest opens but client sees stone, etc).
     */
    public void revealOnInteract(Player player, int wx, int wy, int wz) {
        UUID id = player.getUniqueId();
        ConcurrentMap<Long, Material> map = playerHidden.get(id);
        if (map == null) return;
        long key = VisibilityEngine.pack(wx, wy, wz);
        // Already removed here — any outbound BE packet for this pos will now see
        // isMasked()=false in ContainerEspModule and pass through.
        Material masked = map.remove(key);
        if (masked == null) return;
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            ServerLevel level = (ServerLevel) sp.level();
            BlockPos pos = new BlockPos(wx, wy, wz);
            pendingReveals.computeIfAbsent(id,
                    k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(key);
            sp.connection.send(new ClientboundBlockUpdatePacket(level, pos));
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                var update = be.getUpdatePacket();
                if (update != null) sp.connection.send(update);
            }
            // Also mark cache as visible so tickReveal doesn't immediately re-mask us.
            VisibilityEngine.PlayerView view = visibility.view(player);
            VisibilityEngine.Watched w = view.watched.get(key);
            if (w != null) {
                w.lastVisible = true;
                w.lastChecked = System.currentTimeMillis();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Returns true if this exact position is currently masked for this player. */
    public boolean isMasked(Player player, int wx, int wy, int wz) {
        ConcurrentMap<Long, Material> map = playerHidden.get(player.getUniqueId());
        return map != null && map.containsKey(VisibilityEngine.pack(wx, wy, wz));
    }

    /**
     * Thread-safe variant: resolves the chunk from the player's level and delegates.
     * Returns false (conservative: "assume enclosed") if chunk isn't loaded, which
     * keeps the masking path safe against race-conditions at chunk boundaries.
     */
    private boolean hasOpenFaceFromLevel(Player player, int wx, int wy, int wz) {
        try {
            ServerLevel level = (ServerLevel) ((CraftPlayer) player).getHandle().level();
            LevelChunk chunk = level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
            if (chunk == null) return false;
            return hasOpenFace(chunk, level, wx, wy, wz);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True if the block at (wx,wy,wz) has at least one face neighbor that is not a
     * solid/occluding block (air, water, glass, lava, etc.). Exposed ores are naturally
     * visible to any client rendering the chunk, so masking them is pure UX tax — the
     * cheater sees them anyway. Only buried ores (fully enclosed by opaque rock) are
     * the actual xray-cheat target. Runs on main thread (called from scanAndMask).
     */
    private boolean hasOpenFace(LevelChunk chunk, ServerLevel level, int wx, int wy, int wz) {
        int cx = wx >> 4;
        int cz = wz >> 4;
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int[][] deltas = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : deltas) {
            int nx = wx + d[0], ny = wy + d[1], nz = wz + d[2];
            if (ny < minY || ny >= maxY) return true;
            BlockState neighbor;
            int ncx = nx >> 4, ncz = nz >> 4;
            if (ncx == cx && ncz == cz) {
                neighbor = chunk.getBlockState(new BlockPos(nx, ny, nz));
            } else {
                LevelChunk nc = level.getChunkSource().getChunkNow(ncx, ncz);
                // Unknown neighbor chunk — conservatively assume solid so we don't miss
                // a masking opportunity. The border ores will re-evaluate on subsequent
                // rescans if the neighbor chunk loads.
                if (nc == null) continue;
                neighbor = nc.getBlockState(new BlockPos(nx, ny, nz));
            }
            Material nmat = CraftBlockType.minecraftToBukkit(neighbor.getBlock());
            if (!nmat.isOccluding()) return true;
        }
        return false;
    }
}
