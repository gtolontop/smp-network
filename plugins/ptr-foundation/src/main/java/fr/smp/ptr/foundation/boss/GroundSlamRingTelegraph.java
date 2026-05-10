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

/** Pulsing red ring at ground level — telegraphs a ground-slam attack. */
public final class GroundSlamRingTelegraph implements Telegraph {

    private static final NamespacedKey ID = PtrIds.key("telegraph/ground_slam_ring");
    private static final int RING_RESOLUTION = 64;

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
                                Particle.DustOptions dust =
                                        new Particle.DustOptions(Color.RED, 1.4f);
                                for (int i = 0; i < RING_RESOLUTION; i++) {
                                    double angle = (2 * Math.PI * i) / RING_RESOLUTION;
                                    double dx = Math.cos(angle) * radius;
                                    double dz = Math.sin(angle) * radius;
                                    center.getWorld()
                                            .spawnParticle(
                                                    Particle.DUST,
                                                    center.getX() + dx,
                                                    center.getY() + 0.1,
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
                            2L);
                });
    }
}
