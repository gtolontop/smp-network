package fr.smp.ptr.block;

import fr.smp.ptr.PtrShowcase;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Guards note_block-tuning custom blocks against vanilla mechanics:
 * - Right-click (which would advance the pitch by 1)
 * - Note play sound on redstone trigger
 * - Block physics updates that would re-derive instrument from the block below
 *
 * The Oraxen/ItemsAdder approach: cancel these events whenever the block at the location
 * is one of ours. We register based on PlacedBlocks tracker.
 */
public class NoteBlockGuardListener implements Listener {

    private final PtrShowcase plugin;

    public NoteBlockGuardListener(PtrShowcase plugin) { this.plugin = plugin; }

    /**
     * Cancel right-click on ANY note_block. Required because the resource pack
     * blockstates override applies to all variants — if a player tunes a vanilla
     * note_block to note=1..5 it would render as one of our custom blocks. We
     * "freeze" all note_blocks in the world to keep textures stable.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.NOTE_BLOCK) return;
        e.setCancelled(true);
    }

    /** Mute note plays globally on tuned blocks (prevent confusing sounds on redstone). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlay(NotePlayEvent e) {
        if (plugin.placedBlocks().get(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    /**
     * Re-stabilize note_block instrument/pitch on physics update. If the block under our
     * note_block changes (e.g. player breaks it / replaces with a different material), the
     * vanilla instrument auto-updates and breaks our blockstate mapping. Force it back.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.NOTE_BLOCK) return;
        String id = plugin.placedBlocks().get(b.getLocation());
        if (id == null) return;
        PtrBlock ptr = plugin.blocks().get(id);
        if (!(ptr instanceof RealNoteBlock real)) return;
        // Cancel + restore the desired note pitch on next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (b.getType() != Material.NOTE_BLOCK) return;
            if (b.getBlockData() instanceof NoteBlock nb) {
                nb.setInstrument(Instrument.PIANO);
                nb.setNote(new Note(real.notePitchPublic()));
                nb.setPowered(false);
                b.setBlockData(nb, false);
            }
        });
    }
}
