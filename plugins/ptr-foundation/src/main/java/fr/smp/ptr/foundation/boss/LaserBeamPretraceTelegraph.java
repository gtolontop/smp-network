package fr.smp.ptr.foundation.boss;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.registry.PtrIds;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Thin red beam from {@code center} extending {@code radius} blocks forward
 * along the location's facing — telegraphs a directional laser strike.
 */
public final class LaserBeamPretraceTelegraph implements Telegraph {

    private static final NamespacedKey ID = PtrIds.key("telegraph/laser_beam_pretrace");

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
                    Vector direction = center.getDirection().normalize();
                    scheduler.runOnRegionTimer(
                            center,
                            () -> {
                                if (elapsed[0]++ >= totalTicks) {
                                    return;
                                }
                                Particle.DustOptions dust =
                                        new Particle.DustOptions(Color.RED, 0.8f);
                                for (double d = 0; d <= radius; d += 0.5) {
                                    Vector offset = direction.clone().multiply(d);
                                    center.getWorld()
                                            .spawnParticle(
                                                    Particle.DUST,
                                                    center.getX() + offset.getX(),
                                                    center.getY() + offset.getY(),
                                                    center.getZ() + offset.getZ(),
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
