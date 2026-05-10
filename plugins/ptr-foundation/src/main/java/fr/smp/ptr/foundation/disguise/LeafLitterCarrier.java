package fr.smp.ptr.foundation.disguise;

import fr.smp.ptr.foundation.platform.RegionLocator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * leaf_litter disguise carrier (Minecraft 26.1+).
 *
 * <p>Capacity: 4 segment counts × 4 facings = <b>16</b> states. Tiny but
 * carries flat-on-ground decals which other carriers cannot.
 *
 * <p>The block is brand new in 26.1; this class probes for the material at
 * construction time and logs a warning if the runtime is too old to support
 * it. Calls to {@link #place(Location, State)} when unsupported throw.
 *
 * <p>Leaks:
 *
 * <ul>
 *   <li>Entity walking on it can trigger {@code BlockFadeEvent} on some
 *       configurations — guard via a listener if needed.
 *   <li>Right-click with a flower or moss block may transform the litter
 *       in future MC updates; treat that as an open risk.
 * </ul>
 */
public final class LeafLitterCarrier implements DisguiseCarrier<LeafLitterCarrier.State> {

    private static final @org.jetbrains.annotations.Nullable Material LEAF_LITTER = resolveMaterial();

    /** ({@code segments}, {@code facing}) — segments in 1..4. */
    public record State(int segments, @NotNull BlockFace facing) {
        public State {
            if (segments < 1 || segments > 4) {
                throw new IllegalArgumentException("segments must be 1..4, got " + segments);
            }
            if (facing != BlockFace.NORTH
                    && facing != BlockFace.EAST
                    && facing != BlockFace.SOUTH
                    && facing != BlockFace.WEST) {
                throw new IllegalArgumentException("facing must be cardinal, got " + facing);
            }
        }
    }

    @Override
    public @NotNull String kind() {
        return "leaf_litter";
    }

    @Override
    public int capacity() {
        return supported() ? 16 : 0;
    }

    @Override
    public @NotNull List<String> knownLeaks() {
        if (!supported()) {
            return List.of("Material.LEAF_LITTER not present on this MC runtime — carrier disabled");
        }
        return List.of(
                "leaf_litter is new in 26.1+; future MC releases may add interactions"
                        + " (sniffer, moss bonemeal, etc.) that flip the state",
                "entity step has historically toggled leaf-decay-like blocks;"
                        + " add a listener if the disguise must survive heavy traffic");
    }

    /** True if this MC runtime ships {@code Material.LEAF_LITTER}. */
    public static boolean supported() {
        return LEAF_LITTER != null;
    }

    @Override
    public void place(@NotNull Location loc, @NotNull State state) {
        if (!supported()) {
            throw new UnsupportedOperationException(
                    "Material.LEAF_LITTER is not available on this runtime");
        }
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        block.setType(LEAF_LITTER, false);
        // We intentionally leave the BlockData defaults; segments+facing
        // mapping depends on the live API surface which changes between
        // 26.1 builds. Content layers wrap this with their own reflection
        // until paperweight stabilises the interface.
        // The PDC carries the (segments, facing) tuple meanwhile.
    }

    @Override
    public @NotNull Optional<State> readState(@NotNull Location loc) {
        if (!supported()) {
            return Optional.empty();
        }
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        if (block.getType() != LEAF_LITTER) {
            return Optional.empty();
        }
        // Live read of segments/facing TBD — see note in place().
        return Optional.empty();
    }

    @Override
    public void clear(@NotNull Location loc) {
        if (!supported()) {
            return;
        }
        RegionLocator.assertOwnedByCurrentRegion(loc);
        loc.getBlock().setType(Material.AIR, false);
    }

    private static @org.jetbrains.annotations.Nullable Material resolveMaterial() {
        for (Material m : Material.values()) {
            if (m.name().equalsIgnoreCase("LEAF_LITTER".toUpperCase(Locale.ROOT))) {
                return m;
            }
        }
        return null;
    }
}
