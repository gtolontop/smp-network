package fr.smp.ptr.foundation.boss;

import fr.smp.ptr.foundation.platform.SchedulerService;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * A visual "tell" rendered to players before a boss attack lands.
 *
 * <p>Implementations emit a sequence of per-player particle / sound packets.
 * The foundation impls use Bukkit's audience helpers (which under the hood
 * become individual particle packets); a future optimisation can replace
 * each {@code render} call with a single NMS {@code ClientboundBundlePacket}
 * combining every particle / sound for one frame — see
 * {@code docs/V3_ROADMAP.md}.
 */
public interface Telegraph {

    /** Stable id, used by boss phases to reference the telegraph. */
    @NotNull NamespacedKey id();

    /**
     * Render the telegraph centred on {@code center}.
     *
     * @param plugin owning plugin (used for task scheduling).
     * @param scheduler region-aware scheduler.
     * @param audience players who should see the telegraph (usually nearby).
     * @param center world-space anchor.
     * @param radius effect radius (interpreted per impl).
     * @param warmupTicks how many ticks the telegraph should display before
     *     resolving (caller's hit logic is separate).
     */
    void render(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull Audience audience,
            @NotNull Location center,
            double radius,
            int warmupTicks);
}
