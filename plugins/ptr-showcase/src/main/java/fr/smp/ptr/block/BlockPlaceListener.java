package fr.smp.ptr.block;

import fr.smp.ptr.PtrShowcase;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class BlockPlaceListener implements Listener {

    private final PtrShowcase plugin;

    public BlockPlaceListener(PtrShowcase plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack stack = e.getItemInHand();
        if (stack == null || !stack.hasItemMeta()) return;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        var key = plugin.key("ptr_block_item");
        if (!pdc.has(key, PersistentDataType.STRING)) return;
        String blockId = pdc.get(key, PersistentDataType.STRING);
        PtrBlock block = plugin.blocks().get(blockId);
        if (block == null) return;
        // For RealNoteBlock: the placed block becomes the note_block at correct state
        // For BaseDisplayBlock: the placed block stays + display entities spawn on top
        var loc = e.getBlockPlaced().getLocation();
        // Schedule next tick so vanilla place is fully applied first
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            block.place(loc, e.getPlayer());
            plugin.placedBlocks().mark(loc, blockId);
        }, 1L);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        var loc = e.getBlock().getLocation();
        String blockId = plugin.placedBlocks().get(loc);
        if (blockId == null) return;
        PtrBlock block = plugin.blocks().get(blockId);
        if (block == null) return;
        e.setDropItems(false);
        plugin.placedBlocks().cleanupAt(loc);
        plugin.placedBlocks().remove(loc);
        // Drop the custom item form
        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), block.createItem());
        // Restore air
        e.getBlock().setType(Material.AIR, false);
    }
}
