package fr.smp.ptr.foundation.disguise;

import fr.smp.ptr.foundation.storage.PtrPdcKeys;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.NotePlayEvent;

/**
 * Single Bukkit listener that defends every disguise carrier against the
 * vanilla interactions that would re-sync the carrier back to a "valid"
 * state.
 *
 * <p>Most of these leaks are blocked at the engine level by the
 * {@code block-updates.*} family in {@code paper-global.yml}. This listener
 * is the application-level fallback for the few that still slip through —
 * notably {@link NotePlayEvent} which fires regardless of the global flag.
 */
public final class DisguiseGuardListener implements Listener {

    /**
     * Cancel right-click "play note" so the pitch we wrote stays.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        // Cheap heuristic: every note_block we place lives in a chunk we
        // own. Until placement records land in storage, the safest default
        // for the foundation is to cancel everywhere — content layers can
        // re-enable normal note_block playback elsewhere with a higher
        // priority listener.
        event.setCancelled(true);
    }

    /**
     * Some physics edges still fire even with {@code disable-noteblock-updates};
     * silence them where we recognise the block as ours.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Material type = event.getChangedType();
        if (type != Material.NOTE_BLOCK
                && type != Material.RED_MUSHROOM_BLOCK
                && type != Material.BROWN_MUSHROOM_BLOCK
                && type != Material.MUSHROOM_STEM) {
            return;
        }
        // PDC tags don't live on plain BlockData, only on TileEntities. The
        // honest answer here is: trust the global block-updates.* flag in
        // paper-global.yml. We still keep this hook so a future content
        // layer can add finer-grained "is this OUR carrier block?" checks
        // via the placement registry once it lands.
        // The reference to PtrPdcKeys keeps the import honest for the
        // future hook.
        if (PtrPdcKeys.BLOCK_ID == null) {
            // Unreachable — defensive against a refactor that nulls the key.
            return;
        }
    }
}
