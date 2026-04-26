package fr.smp.anticheat.visibility;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.config.AntiCheatConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongConsumer;

/**
 * Per-player visibility tracker with a chunk-keyed spatial index.
 *
 * Modules register watched positions ("watch"). Each entry is stored twice:
 *   - flat map `watched: posKey → Watched` for O(1) state lookup
 *   - chunk index `byChunk: chunkKey → Set<posKey>` for O(chunks) bulk iteration
 *
 * All bulk operations (invalidateNearby, tick-re-check, consumer reconcile) scope
 * to chunks around a point instead of walking the whole watch list — without this,
 * every fluid flow / block change / reveal tick scanned every watched block of
 * every online player, which was consuming the majority of tick time on the server.
 *
 * Raytracing still runs on the main thread (Paper block lookups are not async-safe
 * outside of chunk snapshots). Budget per tick is capped by config.
 */
public final class VisibilityEngine {

    private final AntiCheatPlugin plugin;
    private final AntiCheatConfig cfg;

    private final ConcurrentMap<UUID, PlayerView> views = new ConcurrentHashMap<>();
    private BukkitTask task;

    public VisibilityEngine(AntiCheatPlugin plugin, AntiCheatConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public void start() {
        // 2L period: anti-ESP/anti-xray reveal cadence. 20Hz → 10Hz halves the
        // per-player chunk scan without any perceivable reveal lag.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        views.clear();
    }

    public PlayerView view(Player p) {
        return views.computeIfAbsent(p.getUniqueId(), id -> new PlayerView());
    }

    public void clear(UUID playerId) {
        views.remove(playerId);
    }

    /**
     * Watch a position for visibility changes. Idempotent per (player, packedPos).
     * Returns the Watched node so callers can mutate flags (e.g. xrayMasked) without a
     * second lookup.
     */
    public Watched watch(Player player, long packedPos) {
        PlayerView v = view(player);
        Watched fresh = new Watched(packedPos, false, 0L);
        Watched existing = v.watched.putIfAbsent(packedPos, fresh);
        Watched w = existing == null ? fresh : existing;
        if (existing == null) {
            long chunkKey = chunkKey(unpackX(packedPos) >> 4, unpackZ(packedPos) >> 4);
            v.byChunk.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(w);
        }
        v.pending.add(packedPos);
        return w;
    }

    public void unwatch(Player player, long packedPos) {
        PlayerView v = views.get(player.getUniqueId());
        if (v == null) return;
        Watched removed = v.watched.remove(packedPos);
        if (removed != null) {
            long chunkKey = chunkKey(unpackX(packedPos) >> 4, unpackZ(packedPos) >> 4);
            Set<Watched> set = v.byChunk.get(chunkKey);
            if (set != null) {
                set.remove(removed);
                if (set.isEmpty()) v.byChunk.remove(chunkKey);
            }
        }
    }

    /** Direct lookup by packedPos — used by Netty-thread callers that only have coords. */
    public Watched watchedAt(Player player, long packedPos) {
        PlayerView v = views.get(player.getUniqueId());
        return v == null ? null : v.watched.get(packedPos);
    }

    /** Synchronous LoS test. Costly — only for one-shot decisions. Main thread only. */
    public boolean hasLineOfSight(Player player, int bx, int by, int bz) {
        Location eye = player.getEyeLocation();
        return raytrace(eye, bx, by, bz);
    }

    /**
     * Thread-safe cached LoS lookup. Returns null if position is not watched for this
     * player, else the last computed visibility. Netty-thread callers MUST use this —
     * {@link #hasLineOfSight} reads world state which is not async-safe.
     */
    public Boolean cachedLineOfSight(Player player, long packedPos) {
        PlayerView v = views.get(player.getUniqueId());
        if (v == null) return null;
        Watched w = v.watched.get(packedPos);
        return w == null ? null : w.lastVisible;
    }

    /**
     * Drain watched positions whose cached visibility changed since the last drain.
     * XrayModule uses this to react only to real visibility transitions instead of
     * sweeping every nearby watch on a fixed timer.
     */
    public int drainDirty(Player player, int max, LongConsumer consumer) {
        PlayerView v = views.get(player.getUniqueId());
        if (v == null) return 0;
        int processed = 0;
        Long key;
        while (processed < max && (key = v.dirty.poll()) != null) {
            consumer.accept(key);
            processed++;
        }
        return processed;
    }

    /**
     * Force re-evaluation of all watched positions within {@code radius} blocks of
     * (wx,wy,wz). Called from block break/place/explosion listeners so LoS changes are
     * picked up immediately. Iterates only chunks overlapping the sphere — NOT all
     * watches — via the byChunk index.
     */
    public int invalidateNearby(World world, int wx, int wy, int wz, int radius) {
        int r2 = radius * radius;
        int cxMin = (wx - radius) >> 4;
        int cxMax = (wx + radius) >> 4;
        int czMin = (wz - radius) >> 4;
        int czMax = (wz + radius) >> 4;
        int touched = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world)) continue;
            PlayerView v = views.get(p.getUniqueId());
            if (v == null || v.byChunk.isEmpty()) continue;
            for (int cx = cxMin; cx <= cxMax; cx++) {
                for (int cz = czMin; cz <= czMax; cz++) {
                    Set<Watched> set = v.byChunk.get(chunkKey(cx, cz));
                    if (set == null || set.isEmpty()) continue;
                    for (Watched w : set) {
                        int kx = unpackX(w.key), ky = unpackY(w.key), kz = unpackZ(w.key);
                        int dx = kx - wx, dy = ky - wy, dz = kz - wz;
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        w.lastChecked = 0L;
                        v.pending.add(w.key);
                        touched++;
                    }
                }
            }
        }
        return touched;
    }

    /** Packed 64-bit position key. 26 bits X, 12 bits Y (offset by 2048), 26 bits Z. */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) ((y + 2048) & 0xFFF) << 26)
                | (long) (z & 0x3FFFFFF);
    }

    public static int unpackX(long k) {
        long v = (k >> 38) & 0x3FFFFFF;
        return (int) (v << 6 >> 6);
    }

    public static int unpackY(long k) {
        return (int) ((k >> 26) & 0xFFF) - 2048;
    }

    public static int unpackZ(long k) {
        long v = k & 0x3FFFFFF;
        return (int) (v << 38 >> 38);
    }

    /** Chunk key: 32-bit X in high bits, 32-bit Z in low bits. */
    public static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | ((long) cz & 0xFFFFFFFFL);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long ttl = cfg.cacheTtlMs();
        int budget = cfg.maxRaytracePerPlayerPerTick();

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerView v = views.get(p.getUniqueId());
            if (v == null || v.watched.isEmpty()) continue;
            World world = p.getWorld();
            Location eye = p.getEyeLocation();
            int processed = 0;

            // Drain pending first — these are targeted (recent invalidations + new watches).
            Long key;
            while (processed < budget && (key = v.pending.poll()) != null) {
                Watched w = v.watched.get(key);
                if (w == null) continue;
                refreshVisibility(v, w, world, eye, now);
                processed++;
            }

            if (processed >= budget) continue;

            // Stale re-check: iterate only chunks within view distance of the player.
            // Anything further is not rendered client-side anyway, so its LoS state is
            // irrelevant until the chunk is re-sent (at which point scanAndMask runs
            // again and freshly populates watches).
            int pcx = p.getLocation().getBlockX() >> 4;
            int pcz = p.getLocation().getBlockZ() >> 4;
            int r = Math.max(4, p.getViewDistance());
            outer:
            for (int dcx = -r; dcx <= r; dcx++) {
                for (int dcz = -r; dcz <= r; dcz++) {
                    Set<Watched> set = v.byChunk.get(chunkKey(pcx + dcx, pcz + dcz));
                    if (set == null || set.isEmpty()) continue;
                    for (Watched w : set) {
                        if (processed >= budget) break outer;
                        if (now - w.lastChecked < ttl) continue;
                        refreshVisibility(v, w, world, eye, now);
                        processed++;
                    }
                }
            }
        }
    }

    private void refreshVisibility(PlayerView view, Watched watched, World world, Location eye, long now) {
        int bx = unpackX(watched.key);
        int by = unpackY(watched.key);
        int bz = unpackZ(watched.key);
        if (world.getMinHeight() > by || by >= world.getMaxHeight()) return;
        boolean previousVisible = watched.lastVisible;
        long previousCheck = watched.lastChecked;
        boolean visible = raytrace(eye, bx, by, bz);
        watched.lastChecked = now;
        watched.lastVisible = visible;
        if (previousCheck == 0L || previousVisible != visible) {
            view.dirty.add(watched.key);
        }
    }

    /**
     * 4-sample raytrace (center + 3 face centers on the eye-facing sides). Partial
     * occlusion at the boundary of a cover block would flicker with single-sampling.
     */
    private boolean raytrace(Location eye, int bx, int by, int bz) {
        World w = eye.getWorld();
        if (w == null) return false;
        double ex = eye.getX() - (bx + 0.5);
        double ey = eye.getY() - (by + 0.5);
        double ez = eye.getZ() - (bz + 0.5);
        double fx = ex >= 0 ? 0.85 : 0.15;
        double fy = ey >= 0 ? 0.85 : 0.15;
        double fz = ez >= 0 ? 0.85 : 0.15;
        if (raytraceOne(w, eye, bx + 0.5, by + 0.5, bz + 0.5, bx, by, bz)) return true;
        if (raytraceOne(w, eye, bx + fx,  by + 0.5, bz + 0.5, bx, by, bz)) return true;
        if (raytraceOne(w, eye, bx + 0.5, by + fy,  bz + 0.5, bx, by, bz)) return true;
        if (raytraceOne(w, eye, bx + 0.5, by + 0.5, bz + fz,  bx, by, bz)) return true;
        return false;
    }

    private boolean raytraceOne(World w, Location eye, double tx, double ty, double tz,
                                int bx, int by, int bz) {
        Vector origin = eye.toVector();
        Vector dir = new Vector(tx - origin.getX(), ty - origin.getY(), tz - origin.getZ());
        double dist = dir.length();
        if (dist < 1e-3) return true;
        if (dist > 96.0) return false;
        dir.multiply(1.0 / dist);

        BlockIterator it = new BlockIterator(w, origin, dir, 0.0, (int) Math.ceil(dist) + 1);
        int eyeBx = eye.getBlockX(), eyeBy = eye.getBlockY(), eyeBz = eye.getBlockZ();
        while (it.hasNext()) {
            Block b = it.next();
            int x = b.getX(), y = b.getY(), z = b.getZ();
            if (x == eyeBx && y == eyeBy && z == eyeBz) continue;
            if (x == bx && y == by && z == bz) return true;
            if (b.getType().isOccluding()) return false;
        }
        return true;
    }

    public static final class PlayerView {
        public final ConcurrentMap<Long, Watched> watched = new ConcurrentHashMap<>();
        public final ConcurrentMap<Long, Set<Watched>> byChunk = new ConcurrentHashMap<>();
        public final ConcurrentLinkedDeque<Long> pending = new ConcurrentLinkedDeque<>();
        public final ConcurrentLinkedDeque<Long> dirty = new ConcurrentLinkedDeque<>();
    }

    /**
     * Per-(player, pos) tracking node. The packed {@link #key} is embedded so that
     * iteration over {@link PlayerView#byChunk} yields everything a reconcile loop
     * needs without a secondary {@code watched.get(key)} CHM lookup per position —
     * that second lookup was the single dominant cost of xray reconcile on the
     * profiler (TreeBin.find ~28 ms/tick with ~5k watches per player).
     *
     * {@code xrayMasked} replaces the old per-player {@code Map<Long, Material>}
     * that stored the mask state — the material was never read, only tested for
     * presence, so a volatile boolean on the Watched itself is both cheaper
     * (field read vs CHM.containsKey) and colocated with the reconcile's
     * per-position work.
     */
    public static final class Watched {
        public final long key;
        public volatile boolean lastVisible;
        public volatile long lastChecked;
        public volatile boolean xrayMasked;

        public Watched(long key, boolean lastVisible, long lastChecked) {
            this.key = key;
            this.lastVisible = lastVisible;
            this.lastChecked = lastChecked;
        }
    }
}
