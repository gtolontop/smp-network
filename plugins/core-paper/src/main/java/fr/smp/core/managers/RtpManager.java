package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class RtpManager {

    private static final Set<Material> UNSAFE = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.WITHER_ROSE);

    private static final int BATCH_SIZE = 4;
    private static final int POOL_TARGET = 8;

    private final SMPCore plugin;
    private final WorldBorderManager borders;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<String, ConcurrentLinkedQueue<Location>> pools = new HashMap<>();
    private volatile BukkitTask fillTask;

    public RtpManager(SMPCore plugin, WorldBorderManager borders) {
        this.plugin = plugin;
        this.borders = borders;
    }

    public void startPoolFiller() {
        int interval = plugin.getConfig().getInt("rtp.pool-fill-interval-ticks", 100);
        fillTask = Bukkit.getScheduler().runTaskTimer(plugin, this::fillPools, interval, interval);
    }

    public void stopPoolFiller() {
        if (fillTask != null) {
            fillTask.cancel();
            fillTask = null;
        }
    }

    public long cooldownLeft(Player p) {
        if (p.hasPermission("smp.rtp.bypass")) return 0;
        Long until = cooldowns.get(p.getUniqueId());
        if (until == null) return 0;
        long left = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0, left);
    }

    public void setCooldown(Player p) {
        long secs = plugin.getConfig().getLong("rtp.cooldown-seconds", 600);
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
    }

    public CompletableFuture<Boolean> teleport(Player p, World world) {
        return teleport(p, world, true);
    }

    public CompletableFuture<Boolean> teleport(Player p, World world, boolean cooldown) {
        String key = world.getName();
        ConcurrentLinkedQueue<Location> pool = pools.get(key);
        if (pool != null) {
            Location cached = pool.poll();
            if (cached != null) {
                var wb = world.getWorldBorder();
                if (wb.isInside(cached)) {
                    return p.teleportAsync(cached).thenApply(ok -> {
                        if (ok) {
                            if (cooldown) setCooldown(p);
                            plugin.logs().log(LogCategory.RTP, p, "rtp (pool) -> " + world.getName() + " " +
                                    cached.getBlockX() + "," + cached.getBlockY() + "," + cached.getBlockZ());
                            plugin.getLogger().info("[RTP] " + p.getName() + " atterri (pool) à "
                                    + cached.getBlockX() + "," + cached.getBlockY() + "," + cached.getBlockZ()
                                    + " dans " + world.getName());
                        } else {
                            plugin.getLogger().warning("[RTP] " + p.getName() + " -> téléportation refusée à "
                                    + cached.getBlockX() + "," + cached.getBlockY() + "," + cached.getBlockZ()
                                    + " dans " + world.getName());
                        }
                        return ok;
                    });
                }
            }
        }
        return batchSearch(p, world, cooldown);
    }

    private CompletableFuture<Boolean> batchSearch(Player p, World world, boolean cooldown) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        int maxAttempts = plugin.getConfig().getInt("rtp.max-attempts", 16);
        int rounds = (maxAttempts + BATCH_SIZE - 1) / BATCH_SIZE;
        tryBatch(p, world, rounds, cooldown, result);
        return result;
    }

    private void tryBatch(Player p, World world, int roundsLeft, boolean cooldown, CompletableFuture<Boolean> out) {
        if (roundsLeft <= 0) {
            p.sendMessage(Msg.err("Impossible de trouver un lieu sûr. Réessaie."));
            plugin.getLogger().warning("[RTP] " + p.getName() + " -> " + world.getName() + " ÉCHEC (aucun lieu sûr trouvé)");
            out.complete(false);
            return;
        }

        var wb = world.getWorldBorder();
        double borderHalf = wb.getSize() / 2.0 - 8;
        double radius = Math.min(borders.rtpRadius(world), borderHalf);
        double cx = wb.getCenter().getX(), cz = wb.getCenter().getZ();
        double min = Math.min(plugin.getConfig().getDouble("rtp.min-distance", 500), Math.max(0, radius - 1));

        List<int[]> candidates = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double dist = min + ThreadLocalRandom.current().nextDouble() * Math.max(50, radius - min);
            int x = (int) (cx + Math.cos(angle) * dist);
            int z = (int) (cz + Math.sin(angle) * dist);
            candidates.add(new int[]{x, z});
        }

        List<CompletableFuture<Void>> chunkLoads = new ArrayList<>(BATCH_SIZE);
        for (int[] c : candidates) {
            chunkLoads.add(
                    preLoadNeighbors(world, c[0], c[1])
                            .thenCompose(vv -> world.getChunkAtAsync(c[0] >> 4, c[1] >> 4, true)).thenAccept(ch -> {})
            );
        }

        CompletableFuture.allOf(chunkLoads.toArray(new CompletableFuture[0])).thenAccept(v -> {
            Location found = null;
            for (int[] c : candidates) {
                Location loc = findSafe(world, c[0], c[1]);
                if (loc != null && wb.isInside(loc)) {
                    found = loc;
                    break;
                }
            }
            if (found == null) {
                tryBatch(p, world, roundsLeft - 1, cooldown, out);
                return;
            }
            Location teleport = found;
            p.teleportAsync(teleport).thenAccept(ok -> {
                if (ok) {
                    if (cooldown) setCooldown(p);
                    plugin.logs().log(LogCategory.RTP, p, "rtp -> " + world.getName() + " " +
                            teleport.getBlockX() + "," + teleport.getBlockY() + "," + teleport.getBlockZ());
                    plugin.getLogger().info("[RTP] " + p.getName() + " atterri à "
                            + teleport.getBlockX() + "," + teleport.getBlockY() + "," + teleport.getBlockZ()
                            + " dans " + world.getName());
                } else {
                    plugin.getLogger().warning("[RTP] " + p.getName() + " -> téléportation refusée à "
                            + teleport.getBlockX() + "," + teleport.getBlockY() + "," + teleport.getBlockZ()
                            + " dans " + world.getName());
                }
                out.complete(ok);
            });
        });
    }

    private CompletableFuture<Void> preLoadNeighbors(World world, int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                loads.add(world.getChunkAtAsync(cx + dx, cz + dz, true));
            }
        }
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]));
    }

    private void fillPools() {
        for (World world : Bukkit.getWorlds()) {
            String key = world.getName();
            ConcurrentLinkedQueue<Location> pool = pools.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
            int needed = POOL_TARGET - pool.size();
            if (needed <= 0) continue;
            int toGen = Math.min(needed, BATCH_SIZE);
            generatePoolCandidates(world, toGen).thenAccept(valid -> {
                pool.addAll(valid);
            });
        }
    }

    private CompletableFuture<List<Location>> generatePoolCandidates(World world, int count) {
        var wb = world.getWorldBorder();
        double borderHalf = wb.getSize() / 2.0 - 8;
        double radius = Math.min(borders.rtpRadius(world), borderHalf);
        double cx = wb.getCenter().getX(), cz = wb.getCenter().getZ();
        double min = Math.min(plugin.getConfig().getDouble("rtp.min-distance", 500), Math.max(0, radius - 1));

        List<int[]> candidates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double dist = min + ThreadLocalRandom.current().nextDouble() * Math.max(50, radius - min);
            int x = (int) (cx + Math.cos(angle) * dist);
            int z = (int) (cz + Math.sin(angle) * dist);
            candidates.add(new int[]{x, z});
        }

        List<CompletableFuture<Void>> loads = new ArrayList<>();
        for (int[] c : candidates) {
            loads.add(
                    preLoadNeighbors(world, c[0], c[1])
                            .thenCompose(v -> world.getChunkAtAsync(c[0] >> 4, c[1] >> 4, true)).thenAccept(ch -> {})
            );
        }

        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).thenApply(v -> {
            List<Location> valid = new ArrayList<>();
            for (int[] c : candidates) {
                Location loc = findSafe(world, c[0], c[1]);
                if (loc != null && wb.isInside(loc)) {
                    valid.add(loc);
                }
            }
            return valid;
        });
    }

    private Location findSafe(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            for (int y = 40; y < 120; y++) {
                if (isSafe(world, x, y, z)) return center(world, x, y, z);
            }
            return null;
        }
        int top = world.getHighestBlockYAt(x, z);
        if (isSafe(world, x, top + 1, z)) return center(world, x, top + 1, z);
        return null;
    }

    private boolean isSafe(World w, int x, int y, int z) {
        Block feet = w.getBlockAt(x, y, z);
        Block head = w.getBlockAt(x, y + 1, z);
        Block ground = w.getBlockAt(x, y - 1, z);
        if (UNSAFE.contains(ground.getType())) return false;
        if (!ground.getType().isSolid()) return false;
        if (feet.getType().isSolid() || head.getType().isSolid()) return false;
        if (feet.getType() == Material.WATER || head.getType() == Material.WATER) return false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Material m = w.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (m == Material.LAVA || m == Material.FIRE || m == Material.SOUL_FIRE
                            || m == Material.MAGMA_BLOCK) return false;
                }
            }
        }
        return true;
    }

    private Location center(World w, int x, int y, int z) {
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    public void clearCooldown(Player p) {
        cooldowns.remove(p.getUniqueId());
    }

    public void unload(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
