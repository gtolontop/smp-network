package fr.smp.ptr.foundation.disguise;

import fr.smp.ptr.foundation.platform.RegionLocator;
import java.util.List;
import java.util.Optional;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.jetbrains.annotations.NotNull;

/**
 * note_block disguise carrier.
 *
 * <p>Capacity: 16 instruments × 25 notes × 2 powered = <b>800</b> distinct
 * states. Useful for solid, mineable, collidable custom blocks; the resource
 * pack maps {@code (instrument, note, powered)} triples to custom models.
 *
 * <p>Leaks:
 *
 * <ul>
 *   <li>{@code paper-global.yml -> block-updates.disable-noteblock-updates}
 *       MUST be {@code true}. Otherwise a redstone neighbour change ticks
 *       the note_block back to a "valid" preset.
 *   <li>{@code NotePlayEvent} fires on right-click; cancel it to keep the
 *       pitch we set. {@link NoteBlockGuardListener} handles this.
 *   <li>Pistons see the note_block as movable — choose a different carrier
 *       for unbreakable / immovable disguises.
 * </ul>
 */
public final class NoteBlockCarrier implements DisguiseCarrier<NoteBlockCarrier.State> {

    /** ({@code instrument}, {@code note}, {@code powered}) tuple. */
    public record State(@NotNull Instrument instrument, @NotNull Note note, boolean powered) {}

    @Override
    public @NotNull String kind() {
        return "note_block";
    }

    @Override
    public int capacity() {
        return 16 * 25 * 2;
    }

    @Override
    public @NotNull List<String> knownLeaks() {
        return List.of(
                "neighbour redstone update resets instrument unless paper-global.yml"
                        + " -> block-updates.disable-noteblock-updates is true",
                "right-click plays a note and resets the pitch unless"
                        + " NoteBlockGuardListener cancels NotePlayEvent",
                "pistons can move note_blocks — pick another carrier for"
                        + " immovable disguises");
    }

    @Override
    public void place(@NotNull Location loc, @NotNull State state) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        block.setType(Material.NOTE_BLOCK, false);
        if (block.getBlockData() instanceof NoteBlock nb) {
            nb.setInstrument(state.instrument());
            nb.setNote(state.note());
            nb.setPowered(state.powered());
            block.setBlockData(nb, false);
        } else {
            throw new IllegalStateException(
                    "Block at " + loc + " did not become a NoteBlock after setType");
        }
    }

    @Override
    public @NotNull Optional<State> readState(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        Block block = loc.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return Optional.empty();
        }
        if (!(block.getBlockData() instanceof NoteBlock nb)) {
            return Optional.empty();
        }
        return Optional.of(new State(nb.getInstrument(), nb.getNote(), nb.isPowered()));
    }

    @Override
    public void clear(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        loc.getBlock().setType(Material.AIR, false);
    }
}
