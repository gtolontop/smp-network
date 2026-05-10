package fr.smp.ptr.foundation.boss;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.registry.PtrIds;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/** Concentric expanding rings — telegraphs an area pulse / shockwave. */
public final class RingSeismPulseTelegraph implements Telegraph {

    private static final NamespacedKey ID = PtrIds.key("telegraph/ring_seism_pulse");
    private static final int RING_RESOLUTION = 96;

    @Override
    public @NotNull NamespacedKey id() {
        return ID;
    }

    @Override
    public void render(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull Audience audience,
            @NotNull Location center,
            double radius,
            int warmupTicks) {
        scheduler.runOnRegion(
                center,
                task -> {
                    int[] elapsed = {0};
                    final int totalTicks = Math.max(1, warmupTicks);
                    scheduler.runOnRegionTimer(
                            center,
                            () -> {
                                if (elapsed[0]++ >= totalTicks) {
                                    return;
                                }
                                double t = (double) elapsed[0] / totalTicks;
                                double currentRadius = radius * t;
                                Particle.DustOptions dust =
                                        new Particle.DustOptions(Color.ORANGE, 1.2f);
                                for (int i = 0; i < RING_RESOLUTION; i++) {
                                    double angle = (2 * Math.PI * i) / RING_RESOLUTION;
                                    double dx = Math.cos(angle) * currentRadius;
                                    double dz = Math.sin(angle) * currentRadius;
                                    center.getWorld()
                                            .spawnParticle(
                                                    Particle.DUST,
                                                    center.getX() + dx,
                                                    center.getY() + 0.15,
                                                    center.getZ() + dz,
                                                    1,
                                                    0.0,
                                                    0.0,
                                                    0.0,
                                                    0.0,
                                                    dust);
                                }
                            },
                            1L,
                            1L);
                });
    }
}
