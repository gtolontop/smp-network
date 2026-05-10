package fr.smp.ptr.block;

import fr.smp.ptr.PtrShowcase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * A "real" custom block that uses note_block tuning. The block in the world IS a real
 * vanilla note_block with a specific (instrument, note, powered) combination, which the
 * resource pack maps to a custom block model. Real block: collidable, mineable, holds
 * in inventory, drops as item on break.
 */
public abstract class RealNoteBlock implements PtrBlock {

    protected final PtrShowcase plugin;

    protected RealNoteBlock(PtrShowcase plugin) { this.plugin = plugin; }

    @Override public abstract String id();
    @Override public abstract String displayName();
    /** Note pitch (0..24) used to identify this custom block in the blockstate. */
    protected abstract int notePitch();
    /** Public accessor for the listener guard. */
    public int notePitchPublic() { return notePitch(); }
    /** Display item shown in inventory (paper map of the block's texture). */
    protected abstract Material itemMaterial();
    /** CMD on the inv item so the resource pack shows the block's texture. */
    protected abstract int itemCustomModelData();

    @Override
    public ItemStack createItem() {
        ItemStack stack = new ItemStack(itemMaterial());
        stack.editMeta(meta -> {
            meta.displayName(Component.text(displayName(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            int cmd = itemCustomModelData();
            if (cmd > 0) meta.setCustomModelData(cmd);
            meta.getPersistentDataContainer().set(plugin.key("ptr_block_item"),
                    PersistentDataType.STRING, id());
        });
        return stack;
    }

    @Override
    public void place(Location at, Player by) {
        Block b = at.getBlock();
        b.setType(Material.NOTE_BLOCK, false);
        if (b.getBlockData() instanceof NoteBlock nb) {
            nb.setInstrument(Instrument.PIANO); // = "harp" (default when block-below not specific)
            nb.setNote(new Note(notePitch()));
            nb.setPowered(false);
            b.setBlockData(nb, false);
        }
    }
}
