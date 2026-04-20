package fr.smp.core.managers;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Renders the blood trail + red aura around the current hunted.
 * Runs every 5 ticks; self-cancels when there is no hunted online.
 */
public class HuntedParticleTask extends BukkitRunnable {

    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(130, 10, 10), 1.6f);
    private static final Particle.DustOptions AURA  = new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.0f);

    private final HuntedManager manager;
    private UUID lastTracked;
    private Location lastFootprint;
    private double auraPhase;

    public HuntedParticleTask(HuntedManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        UUID hunted = manager.currentHunted();
        if (hunted == null) { cancel(); return; }
        Player p = org.bukkit.Bukkit.getPlayer(hunted);
        if (p == null || !p.isOnline()) return;
        if (!p.getUniqueId().equals(lastTracked)) {
            lastTracked = p.getUniqueId();
            lastFootprint = null;
        }

        Location base = p.getLocation();
        spawnAura(p, base);
        spawnBloodTrail(p, base);
    }

    private void spawnAura(Player p, Location base) {
        auraPhase += 0.45;
        double radius = 1.0;
        for (int i = 0; i < 3; i++) {
            double angle = auraPhase + (i * (Math.PI * 2 / 3));
            double x = base.getX() + Math.cos(angle) * radius;
            double z = base.getZ() + Math.sin(angle) * radius;
            Location ring = new Location(base.getWorld(), x, base.getY() + 0.2, z);
            p.getWorld().spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0, AURA);
        }
        // Occasional ember above the head to make them spottable from afar.
        Location head = base.clone().add(0, 2.2, 0);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, head, 1, 0.1, 0.05, 0.1, 0.01);
    }

    private void spawnBloodTrail(Player p, Location base) {
        if (lastFootprint == null
                || !lastFootprint.getWorld().equals(base.getWorld())
                || lastFootprint.distanceSquared(base) > 0.81) { // ~0.9 blocks
            // Splat at player's feet.
            Location drop = base.clone().add(0, 0.05, 0);
            p.getWorld().spawnParticle(Particle.DUST, drop, 6, 0.25, 0.0, 0.25, 0, BLOOD);
            lastFootprint = base.clone();
        }
    }
}
