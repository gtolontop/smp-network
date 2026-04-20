package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class RtpManager {

    private static final Set<Material> UNSAFE = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.WITHER_ROSE);

    private final SMPCore plugin;
    private final WorldBorderManager borders;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RtpManager(SMPCore plugin, WorldBorderManager borders) {
        this.plugin = plugin;
        this.borders = borders;
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
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        int attempts = plugin.getConfig().getInt("rtp.max-attempts", 16);
        tryOnce(p, world, attempts, result);
        return result;
    }

    private void tryOnce(Player p, World world, int remaining, CompletableFuture<Boolean> out) {
        if (remaining <= 0) {
            p.sendMessage(Msg.err("Impossible de trouver un lieu sûr. Réessaie."));
            out.complete(false);
            return;
        }
        var wb = world.getWorldBorder();
        double borderHalf = wb.getSize() / 2.0 - 8;
        double radius = Math.min(borders.rtpRadius(world), borderHalf);
        double cx = wb.getCenter().getX(), cz = wb.getCenter().getZ();
        double min = Math.min(plugin.getConfig().getDouble("rtp.min-distance", 500), Math.max(0, radius - 1));

        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double dist = min + ThreadLocalRandom.current().nextDouble() * Math.max(50, radius - min);
        int x = (int) (cx + Math.cos(angle) * dist);
        int z = (int) (cz + Math.sin(angle) * dist);

        world.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk -> {
            Location safe = findSafe(world, x, z);
            if (safe == null || !wb.isInside(safe)) {
                tryOnce(p, world, remaining - 1, out);
                return;
            }
            p.teleportAsync(safe).thenAccept(ok -> {
                if (ok) {
                    setCooldown(p);
                    plugin.logs().log(LogCategory.RTP, p, "rtp -> " + world.getName() + " " +
                            safe.getBlockX() + "," + safe.getBlockY() + "," + safe.getBlockZ());
                }
                out.complete(ok);
            });
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
