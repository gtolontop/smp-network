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

/** Vertical swirl of dark-purple particles — telegraphs a void / portal strike. */
public final class VoidPortalSwirlTelegraph implements Telegraph {

    private static final NamespacedKey ID = PtrIds.key("telegraph/void_portal_swirl");
    private static final int SPIRAL_POINTS = 24;
    private static final double SPIRAL_HEIGHT = 3.0;

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
                                double phase = (double) elapsed[0] / 2.0;
                                Particle.DustOptions dust =
                                        new Particle.DustOptions(
                                                Color.fromRGB(0x66, 0x22, 0x99), 1.0f);
                                for (int i = 0; i < SPIRAL_POINTS; i++) {
                                    double t = (double) i / SPIRAL_POINTS;
                                    double angle = phase + t * 4 * Math.PI;
                                    double dx = Math.cos(angle) * radius;
                                    double dz = Math.sin(angle) * radius;
                                    double dy = t * SPIRAL_HEIGHT;
                                    center.getWorld()
                                            .spawnParticle(
                                                    Particle.DUST,
                                                    center.getX() + dx,
                                                    center.getY() + dy,
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
