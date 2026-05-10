package fr.smp.ptr.mob;

import fr.smp.ptr.PtrShowcase;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Telegraph {

    private Telegraph() {}

    /**
     * Spawns a red BlockDisplay disc at the given location that pulses for {@code warmupTicks},
     * then triggers an explosion-style hit dealing {@code damage} to all players within {@code radius}.
     */
    public static void groundSlam(PtrShowcase plugin, Location center, double radius, int warmupTicks, double damage) {
        var world = center.getWorld();
        BlockData red = Bukkit.createBlockData(Material.RED_CONCRETE);
        BlockDisplay disc = world.spawn(center.clone().add(0, 0.05, 0), BlockDisplay.class, e -> {
            e.setBlock(red);
            float r = (float) (radius * 2);
            e.setTransformation(new Transformation(
                    new Vector3f(-(float) radius, -0.05f, -(float) radius),
                    new Quaternionf(),
                    new Vector3f(r, 0.1f, r),
                    new Quaternionf()));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setGlowColorOverride(Color.RED);
            e.setBillboard(Display.Billboard.FIXED);
        });

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!disc.isValid()) { cancel(); return; }
                t++;
                world.spawnParticle(Particle.DUST,
                        center.clone().add(0, 0.1, 0),
                        15, radius, 0.05, radius,
                        new Particle.DustOptions(Color.RED, 1.4f));
                if (t >= warmupTicks / 5) {
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                    for (var entity : world.getNearbyEntities(center, radius, 4, radius)) {
                        if (entity instanceof Player pl) {
                            if (pl.getLocation().distance(center) <= radius) {
                                pl.damage(damage);
                                pl.setVelocity(pl.getVelocity().add(new org.bukkit.util.Vector(0, 0.6, 0)));
                            }
                        }
                    }
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
                    disc.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /** Marks a line beam from source to target. Useful for laser-style telegraphs. */
    public static void laserBeam(PtrShowcase plugin, Location from, Location to, int durationTicks) {
        var world = from.getWorld();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > durationTicks) { cancel(); return; }
                var dir = to.toVector().subtract(from.toVector());
                double len = dir.length();
                dir.normalize();
                for (double d = 0; d <= len; d += 0.5) {
                    var p = from.toVector().clone().add(dir.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0, 0, 0,
                            new Particle.DustOptions(Color.RED, 0.8f));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Periodically slams the area around the given mob until it dies. */
    public static void attachGroundSlamLoop(PtrShowcase plugin, LivingEntity mob, double radius, double damage, int periodTicks) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!mob.isValid() || mob.isDead()) { cancel(); return; }
                Player nearest = nearestPlayer(mob, 16);
                if (nearest == null) return;
                Telegraph.groundSlam(plugin, nearest.getLocation(), radius, 25, damage);
            }
        }.runTaskTimer(plugin, periodTicks, periodTicks);
    }

    private static Player nearestPlayer(LivingEntity from, double maxDist) {
        Player best = null;
        double bestD = maxDist * maxDist;
        for (var p : from.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(from.getLocation());
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }
}
