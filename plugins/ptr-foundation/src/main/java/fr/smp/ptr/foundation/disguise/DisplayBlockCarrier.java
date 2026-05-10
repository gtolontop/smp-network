package fr.smp.ptr.foundation.disguise;

import fr.smp.ptr.foundation.platform.RegionLocator;
import fr.smp.ptr.foundation.storage.PtrPdcCodec;
import fr.smp.ptr.foundation.storage.PtrPdcKeys;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.jetbrains.annotations.NotNull;

/**
 * BlockDisplay-backed carrier (used for non-cubic furniture and machines).
 *
 * <p>Capacity: <b>unbounded</b> — every placed instance gets a unique
 * identifier in its PDC, so the carrier never has to encode the ptr id in a
 * block state. Cost: one entity per placement.
 *
 * <p>The combo is: a {@code BARRIER} block for collision (so the player can
 * stand on it), a {@code BlockDisplay} for the visual, and an {@code
 * Interaction} entity for click detection.
 *
 * <p>Leaks:
 *
 * <ul>
 *   <li>Each placement is three entities; budget aggressively before
 *       allowing player-placed furniture (limit per chunk / per player).
 *   <li>BlockDisplays are not pathfind-aware; mobs walk through the visual.
 *   <li>Spectator camera locks onto the {@code BARRIER} block class, which
 *       can look odd if the displayed material is non-cubic.
 * </ul>
 */
public final class DisplayBlockCarrier implements DisguiseCarrier<DisplayBlockCarrier.State> {

    /** Per-placement state: ({@code visual}, {@code displayId}). */
    public record State(@NotNull BlockData visual, @NotNull NamespacedKey displayId) {}

    @Override
    public @NotNull String kind() {
        return "display_block";
    }

    @Override
    public int capacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public @NotNull List<String> knownLeaks() {
        return List.of(
                "each placement is BlockDisplay + barrier + Interaction = 3 entities,"
                        + " budget aggressively before letting players spam this",
                "BlockDisplay is not pathfind-aware — mobs walk through the visual",
                "spectator camera locks onto the BARRIER class, which can look odd");
    }

    @Override
    public void place(@NotNull Location loc, @NotNull State state) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        block.setType(Material.BARRIER, false);
        BlockDisplay display =
                loc.getWorld()
                        .spawn(
                                loc.clone().add(0.5, 0.0, 0.5),
                                BlockDisplay.class,
                                e -> {
                                    e.setBlock(state.visual());
                                    PtrPdcCodec.NAMESPACED_KEY.write(
                                            e.getPersistentDataContainer(),
                                            PtrPdcKeys.BLOCK_ID,
                                            state.displayId());
                                });
        Interaction interaction =
                loc.getWorld()
                        .spawn(
                                loc.clone().add(0.5, 0.0, 0.5),
                                Interaction.class,
                                e -> {
                                    e.setInteractionWidth(1.0f);
                                    e.setInteractionHeight(1.0f);
                                    e.setResponsive(true);
                                    PtrPdcCodec.NAMESPACED_KEY.write(
                                            e.getPersistentDataContainer(),
                                            PtrPdcKeys.BLOCK_ID,
                                            state.displayId());
                                });
        // Keep variable references so the IDE doesn't flag them unused; the
        // entities are now owned by the chunk and will be cleaned up via
        // clear() or world unload.
        if (!display.isValid() || !interaction.isValid()) {
            throw new IllegalStateException("Failed to spawn display rig at " + loc);
        }
    }

    @Override
    public @NotNull Optional<State> readState(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        if (block.getType() != Material.BARRIER) {
            return Optional.empty();
        }
        for (var entity : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6)) {
            if (entity instanceof BlockDisplay bd) {
                if (!bd.getPersistentDataContainer().has(PtrPdcKeys.BLOCK_ID)) {
                    continue;
                }
                NamespacedKey id =
                        PtrPdcCodec.NAMESPACED_KEY.read(
                                bd.getPersistentDataContainer(), PtrPdcKeys.BLOCK_ID);
                return Optional.of(new State(bd.getBlock(), id));
            }
        }
        return Optional.empty();
    }

    @Override
    public void clear(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        if (block.getType() == Material.BARRIER) {
            block.setType(Material.AIR, false);
        }
        for (var entity : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6)) {
            if (entity instanceof BlockDisplay || entity instanceof Interaction) {
                if (entity.getPersistentDataContainer().has(PtrPdcKeys.BLOCK_ID)) {
                    entity.remove();
                }
            }
        }
    }
}
