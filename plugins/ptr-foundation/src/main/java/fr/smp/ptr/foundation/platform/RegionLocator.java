package fr.smp.ptr.foundation.platform;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Helpers around Folia's per-region ownership check.
 *
 * <p>Folia merges and splits regions at runtime to balance load. Caching the
 * result of {@code isOwnedByCurrentRegion} across more than one operation is
 * a footgun: by the time you act on the cached answer, the region boundary
 * may have moved. Every operation that touches per-region state must re-ask.
 *
 * <p>This class is intentionally just a thin wrapper: it adds the
 * documentation, and it gives us one place to add tracing if we need to
 * audit region-crossing patterns later.
 */
public final class RegionLocator {

    private RegionLocator() {}

    /** True if the current thread owns the region containing {@code location}. */
    public static boolean isOwnedByCurrentRegion(@NotNull Location location) {
        Objects.requireNonNull(location, "location");
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    /** True if the current thread owns the region containing the given chunk. */
    public static boolean isOwnedByCurrentRegion(@NotNull World world, int chunkX, int chunkZ) {
        Objects.requireNonNull(world, "world");
        return Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    /** True if the current thread owns the region containing {@code entity}. */
    public static boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    /**
     * Throws if the current thread does not own the region containing {@code
     * location}. Useful as a guard at the top of a method that mutates region
     * state.
     */
    public static void assertOwnedByCurrentRegion(@NotNull Location location) {
        if (!isOwnedByCurrentRegion(location)) {
            throw new IllegalStateException(
                    "Not on the region thread for " + location
                            + " — reschedule via SchedulerService#runOnRegion");
        }
    }
}
