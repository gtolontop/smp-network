package fr.smp.ptr.foundation.disguise;

import fr.smp.ptr.foundation.platform.RegionLocator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.jetbrains.annotations.NotNull;

/**
 * mushroom-block disguise carrier (red / brown / stem).
 *
 * <p>Capacity: 3 base materials × 64 face combinations = <b>192</b> states.
 * Useful for connecting blocks where the {@code MultipleFacing} bool flags
 * align with a 6-bit identifier.
 *
 * <p>Leaks:
 *
 * <ul>
 *   <li>{@code paper-global.yml -> block-updates.disable-mushroom-block-updates}
 *       MUST be {@code true}. Otherwise the connection logic flips faces
 *       based on adjacency the next physics tick.
 *   <li>The block can still be sheared / harvested with silk-touch by
 *       default; restrict via permissions or a separate listener.
 * </ul>
 */
public final class MushroomCarrier implements DisguiseCarrier<MushroomCarrier.State> {

    /** Mushroom-block face bitmap. */
    public record State(@NotNull Material base, @NotNull Set<BlockFace> faces) {

        public State {
            if (base != Material.RED_MUSHROOM_BLOCK
                    && base != Material.BROWN_MUSHROOM_BLOCK
                    && base != Material.MUSHROOM_STEM) {
                throw new IllegalArgumentException("Not a mushroom carrier base: " + base);
            }
            for (BlockFace face : faces) {
                if (face != BlockFace.NORTH
                        && face != BlockFace.SOUTH
                        && face != BlockFace.EAST
                        && face != BlockFace.WEST
                        && face != BlockFace.UP
                        && face != BlockFace.DOWN) {
                    throw new IllegalArgumentException(
                            "Mushroom carrier only supports cardinal+vertical faces, got " + face);
                }
            }
        }
    }

    @Override
    public @NotNull String kind() {
        return "mushroom";
    }

    @Override
    public int capacity() {
        return 3 * 64;
    }

    @Override
    public @NotNull List<String> knownLeaks() {
        return List.of(
                "neighbour adjacency physics ticks reset face flags unless"
                        + " paper-global.yml -> block-updates.disable-mushroom-block-updates is true",
                "silk touch returns the base mushroom block — handle with"
                        + " a BlockBreakEvent listener if drops must match the PTR id");
    }

    @Override
    public void place(@NotNull Location loc, @NotNull State state) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        block.setType(state.base(), false);
        if (block.getBlockData() instanceof MultipleFacing facing) {
            for (BlockFace face : facing.getAllowedFaces()) {
                facing.setFace(face, state.faces().contains(face));
            }
            block.setBlockData(facing, false);
        } else {
            throw new IllegalStateException(
                    "Block at " + loc + " is not a MultipleFacing after setType");
        }
    }

    @Override
    public @NotNull Optional<State> readState(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        Material type = block.getType();
        if (type != Material.RED_MUSHROOM_BLOCK
                && type != Material.BROWN_MUSHROOM_BLOCK
                && type != Material.MUSHROOM_STEM) {
            return Optional.empty();
        }
        if (!(block.getBlockData() instanceof MultipleFacing facing)) {
            return Optional.empty();
        }
        Set<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
        for (BlockFace f : facing.getFaces()) {
            faces.add(f);
        }
        return Optional.of(new State(type, faces));
    }

    @Override
    public void clear(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        loc.getBlock().setType(Material.AIR, false);
    }
}
