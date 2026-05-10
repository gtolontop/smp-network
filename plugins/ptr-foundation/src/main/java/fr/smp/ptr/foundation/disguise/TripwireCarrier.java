package fr.smp.ptr.foundation.disguise;

import fr.smp.ptr.foundation.platform.RegionLocator;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Tripwire;
import org.jetbrains.annotations.NotNull;

/**
 * tripwire disguise carrier.
 *
 * <p>Capacity: ~<b>127</b> states (5 boolean flags × NSEW + powered +
 * disarmed + attached). Useful for flat decorations and low-collision
 * markers.
 *
 * <p>Leaks:
 *
 * <ul>
 *   <li>Entity walking on the tripwire fires {@code EntityInteractEvent}
 *       and re-evaluates the powered state.
 *   <li>Tripwire updates can also be globally disabled via
 *       {@code paper-global.yml -> block-updates.disable-tripwire-updates}
 *       — required if the disguise must survive entity traffic.
 *   <li>Tripwire breaks easily; carrier is not suitable for blocks meant
 *       to take a hit.
 * </ul>
 */
public final class TripwireCarrier implements DisguiseCarrier<TripwireCarrier.State> {

    /** Tripwire flag tuple. */
    public record State(
            boolean powered,
            boolean disarmed,
            boolean attached,
            boolean north,
            boolean east,
            boolean south,
            boolean west) {}

    @Override
    public @NotNull String kind() {
        return "tripwire";
    }

    @Override
    public int capacity() {
        return 127;
    }

    @Override
    public @NotNull List<String> knownLeaks() {
        return List.of(
                "EntityInteractEvent fires when an entity walks on the wire"
                        + " — listener required if state must survive traffic",
                "set paper-global.yml -> block-updates.disable-tripwire-updates"
                        + " = true to keep the disguise stable",
                "tripwire is fragile; one hit destroys it");
    }

    @Override
    public void place(@NotNull Location loc, @NotNull State state) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        block.setType(Material.TRIPWIRE, false);
        if (block.getBlockData() instanceof Tripwire tw) {
            tw.setPowered(state.powered());
            tw.setDisarmed(state.disarmed());
            tw.setAttached(state.attached());
            tw.setFace(org.bukkit.block.BlockFace.NORTH, state.north());
            tw.setFace(org.bukkit.block.BlockFace.EAST, state.east());
            tw.setFace(org.bukkit.block.BlockFace.SOUTH, state.south());
            tw.setFace(org.bukkit.block.BlockFace.WEST, state.west());
            block.setBlockData(tw, false);
        } else {
            throw new IllegalStateException(
                    "Block at " + loc + " did not become a Tripwire after setType");
        }
    }

    @Override
    public @NotNull Optional<State> readState(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        if (block.getType() != Material.TRIPWIRE) {
            return Optional.empty();
        }
        if (!(block.getBlockData() instanceof Tripwire tw)) {
            return Optional.empty();
        }
        return Optional.of(
                new State(
                        tw.isPowered(),
                        tw.isDisarmed(),
                        tw.isAttached(),
                        tw.hasFace(org.bukkit.block.BlockFace.NORTH),
                        tw.hasFace(org.bukkit.block.BlockFace.EAST),
                        tw.hasFace(org.bukkit.block.BlockFace.SOUTH),
                        tw.hasFace(org.bukkit.block.BlockFace.WEST)));
    }

    @Override
    public void clear(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        loc.getBlock().setType(Material.AIR, false);
    }
}
