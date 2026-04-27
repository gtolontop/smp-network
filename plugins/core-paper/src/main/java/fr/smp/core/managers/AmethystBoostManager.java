package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boosts vanilla budding amethyst growth at a configurable rate. Replicates
 * the vanilla random-tick growth (six-face try, air/water → small bud, then
 * small → medium → large → cluster) but on our own scheduler so we can run
 * many attempts per pulse instead of waiting on randomTickSpeed.
 */
public final class AmethystBoostManager {

    public enum Preset {
        OFF("off",        false, 0,   0,   0.0),
        SLOW("slow",      true,  40,  1,   0.50),
        MEDIUM("medium",  true,  20,  1,   1.00),
        FAST("fast",      true,  10,  2,   1.00),
        HYPER("hyper",    true,  5,   4,   1.00),
        INSANE("insane",  true,  1,   6,   1.00);

        public final String id;
        public final boolean enabled;
        public final int pulseTicks;
        public final int attemptsPerPulse;
        public final double chance;

        Preset(String id, boolean enabled, int pulseTicks, int attempts, double chance) {
            this.id = id;
            this.enabled = enabled;
            this.pulseTicks = pulseTicks;
            this.attemptsPerPulse = attempts;
            this.chance = chance;
        }

        public static Preset fromId(String id) {
            if (id == null) return null;
            String norm = id.trim().toLowerCase();
            for (Preset p : values()) {
                if (p.id.equals(norm)) return p;
            }
            return null;
        }
    }

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST
    };

    private final SMPCore plugin;

    /** Tracked budding amethyst positions, grouped by chunk for fast unload. */
    private final Map<ChunkKey, Set<BlockPos>> byChunk = new ConcurrentHashMap<>();

    private boolean enabled;
    private Preset preset;
    private int pulseTicks;
    private int attemptsPerPulse;
    private double chance;

    private BukkitTask task;

    public AmethystBoostManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var c = plugin.getConfig();
        String presetId = c.getString("amethyst.preset", "insane");
        Preset p = Preset.fromId(presetId);
        if (p == null) p = Preset.MEDIUM;
        applyPresetInternal(p, false);

        // Allow per-field overrides if present.
        if (c.isSet("amethyst.enabled"))         this.enabled = c.getBoolean("amethyst.enabled");
        if (c.isSet("amethyst.pulse-ticks"))     this.pulseTicks = Math.max(1, c.getInt("amethyst.pulse-ticks"));
        if (c.isSet("amethyst.attempts-per-pulse")) this.attemptsPerPulse = Math.max(0, c.getInt("amethyst.attempts-per-pulse"));
        if (c.isSet("amethyst.chance"))          this.chance = clamp01(c.getDouble("amethyst.chance"));
    }

    public void start() {
        load();
        // Initial scan of already-loaded chunks (server reload, /reload, late enable).
        for (World w : Bukkit.getWorlds()) {
            for (Chunk ch : w.getLoadedChunks()) {
                scanChunk(ch);
            }
        }
        rescheduleTask();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        byChunk.clear();
    }

    // ---- Public API ---------------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public Preset preset() { return preset; }
    public int pulseTicks() { return pulseTicks; }
    public int attemptsPerPulse() { return attemptsPerPulse; }
    public double chance() { return chance; }
    public int trackedCount() {
        int n = 0;
        for (Set<BlockPos> s : byChunk.values()) n += s.size();
        return n;
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
        plugin.getConfig().set("amethyst.enabled", v);
        plugin.saveConfig();
        rescheduleTask();
    }

    public void applyPreset(Preset p) {
        applyPresetInternal(p, true);
        rescheduleTask();
    }

    private void applyPresetInternal(Preset p, boolean save) {
        this.preset = p;
        this.enabled = p.enabled;
        this.pulseTicks = Math.max(1, p.pulseTicks);
        this.attemptsPerPulse = Math.max(0, p.attemptsPerPulse);
        this.chance = clamp01(p.chance);
        if (save) {
            var c = plugin.getConfig();
            c.set("amethyst.preset", p.id);
            c.set("amethyst.enabled", enabled);
            c.set("amethyst.pulse-ticks", pulseTicks);
            c.set("amethyst.attempts-per-pulse", attemptsPerPulse);
            c.set("amethyst.chance", chance);
            plugin.saveConfig();
        }
    }

    public boolean setField(String field, String rawValue) {
        switch (field.toLowerCase()) {
            case "pulse", "pulse-ticks", "period" -> {
                int v;
                try { v = Integer.parseInt(rawValue); } catch (NumberFormatException e) { return false; }
                if (v < 1) return false;
                this.pulseTicks = v;
                plugin.getConfig().set("amethyst.pulse-ticks", v);
                plugin.saveConfig();
                rescheduleTask();
                return true;
            }
            case "attempts", "attempts-per-pulse" -> {
                int v;
                try { v = Integer.parseInt(rawValue); } catch (NumberFormatException e) { return false; }
                if (v < 0) return false;
                this.attemptsPerPulse = v;
                plugin.getConfig().set("amethyst.attempts-per-pulse", v);
                plugin.saveConfig();
                return true;
            }
            case "chance" -> {
                double v;
                try { v = Double.parseDouble(rawValue.replace(",", ".")); } catch (NumberFormatException e) { return false; }
                if (v < 0 || v > 1) return false;
                this.chance = v;
                plugin.getConfig().set("amethyst.chance", v);
                plugin.saveConfig();
                return true;
            }
            default -> { return false; }
        }
    }

    // ---- Tracking -----------------------------------------------------------

    public void track(Block block) {
        if (block.getType() != Material.BUDDING_AMETHYST) return;
        ChunkKey k = ChunkKey.of(block);
        byChunk.computeIfAbsent(k, __ -> ConcurrentHashMap.newKeySet()).add(BlockPos.of(block));
    }

    public void untrack(Block block) {
        ChunkKey k = ChunkKey.of(block);
        Set<BlockPos> set = byChunk.get(k);
        if (set == null) return;
        set.remove(BlockPos.of(block));
        if (set.isEmpty()) byChunk.remove(k);
    }

    public void scanChunk(Chunk chunk) {
        World w = chunk.getWorld();
        ChunkKey k = ChunkKey.of(w, chunk.getX(), chunk.getZ());
        // Drop any stale entries for this chunk; rebuild from live state.
        byChunk.remove(k);

        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        Set<BlockPos> found = null;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = minY; y < maxY; y++) {
                    Block b = w.getBlockAt(baseX + dx, y, baseZ + dz);
                    if (b.getType() == Material.BUDDING_AMETHYST) {
                        if (found == null) found = ConcurrentHashMap.newKeySet();
                        found.add(BlockPos.of(b));
                    }
                }
            }
        }
        if (found != null) byChunk.put(k, found);
    }

    public void forgetChunk(Chunk chunk) {
        byChunk.remove(ChunkKey.of(chunk.getWorld(), chunk.getX(), chunk.getZ()));
    }

    // ---- Scheduler ----------------------------------------------------------

    private void rescheduleTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!enabled || attemptsPerPulse <= 0 || chance <= 0.0) return;
        long period = Math.max(1, pulseTicks);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, period, period);
    }

    private void pulse() {
        if (!enabled) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (Map.Entry<ChunkKey, Set<BlockPos>> entry : byChunk.entrySet()) {
            ChunkKey k = entry.getKey();
            World w = Bukkit.getWorld(k.world);
            if (w == null) continue;
            if (!w.isChunkLoaded(k.cx, k.cz)) continue;

            Set<BlockPos> set = entry.getValue();
            for (BlockPos pos : set) {
                Block b = w.getBlockAt(pos.x, pos.y, pos.z);
                if (b.getType() != Material.BUDDING_AMETHYST) {
                    // Stale entry — listener will eventually clean up, but drop now.
                    set.remove(pos);
                    continue;
                }
                for (int i = 0; i < attemptsPerPulse; i++) {
                    if (rng.nextDouble() < chance) {
                        tryGrow(b, rng);
                    }
                }
            }
        }
    }

    private void tryGrow(Block budding, ThreadLocalRandom rng) {
        BlockFace face = FACES[rng.nextInt(FACES.length)];
        Block target = budding.getRelative(face);
        Material t = target.getType();

        if (t == Material.AIR || t == Material.CAVE_AIR || t == Material.WATER) {
            BlockData newData = Material.SMALL_AMETHYST_BUD.createBlockData();
            applyFacing(newData, face);
            applyWaterlog(newData, t == Material.WATER);
            target.setBlockData(newData, true);
            return;
        }
        Material next = nextStage(t);
        if (next == null) return;
        if (!faceMatches(target, face)) return; // only grow buds attached on the same face.
        BlockData oldData = target.getBlockData();
        BlockData newData = next.createBlockData();
        applyFacing(newData, face);
        applyWaterlog(newData, isWaterlogged(oldData));
        target.setBlockData(newData, true);
    }

    private static Material nextStage(Material m) {
        return switch (m) {
            case SMALL_AMETHYST_BUD -> Material.MEDIUM_AMETHYST_BUD;
            case MEDIUM_AMETHYST_BUD -> Material.LARGE_AMETHYST_BUD;
            case LARGE_AMETHYST_BUD -> Material.AMETHYST_CLUSTER;
            default -> null;
        };
    }

    private static void applyFacing(BlockData data, BlockFace face) {
        if (data instanceof Directional d) d.setFacing(face);
    }

    private static void applyWaterlog(BlockData data, boolean water) {
        if (data instanceof Waterlogged w) w.setWaterlogged(water);
    }

    private static boolean isWaterlogged(BlockData data) {
        return data instanceof Waterlogged w && w.isWaterlogged();
    }

    private static boolean faceMatches(Block bud, BlockFace expected) {
        BlockData d = bud.getBlockData();
        return !(d instanceof Directional dir) || dir.getFacing() == expected;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // ---- Keys ---------------------------------------------------------------

    private record ChunkKey(UUID world, int cx, int cz) {
        static ChunkKey of(World w, int cx, int cz) { return new ChunkKey(w.getUID(), cx, cz); }
        static ChunkKey of(Block b) {
            return new ChunkKey(b.getWorld().getUID(), b.getX() >> 4, b.getZ() >> 4);
        }
    }

    private record BlockPos(int x, int y, int z) {
        static BlockPos of(Block b) { return new BlockPos(b.getX(), b.getY(), b.getZ()); }
    }

    // ---- Display ------------------------------------------------------------

    public Map<String, String> describe() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("enabled", String.valueOf(enabled));
        m.put("preset", preset == null ? "?" : preset.id);
        m.put("pulse-ticks", String.valueOf(pulseTicks));
        m.put("attempts-per-pulse", String.valueOf(attemptsPerPulse));
        m.put("chance", String.format("%.2f", chance));
        m.put("tracked", String.valueOf(trackedCount()));
        return m;
    }
}
