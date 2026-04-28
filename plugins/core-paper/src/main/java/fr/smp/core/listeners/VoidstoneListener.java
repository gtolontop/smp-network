package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.GUIHolder;
import fr.smp.core.gui.VoidstoneGUI;
import fr.smp.core.voidstone.VoidstoneManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.PlayerInventory;

public final class VoidstoneListener implements Listener {

    private final SMPCore plugin;

    public VoidstoneListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null || !manager.isVoidstoneRecipe(event.getRecipe())) return;
        event.getInventory().setResult(manager.createCraftResult());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryOpen(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;
        if (!event.getClick().isRightClick()) return;
        if (event.getView().getTopInventory().getHolder() instanceof GUIHolder) return;

        VoidstoneManager manager = plugin.voidstones();
        if (manager == null || !manager.isVoidstone(event.getCurrentItem())) return;

        event.setCancelled(true);
        String itemId = manager.readId(event.getCurrentItem());
        if (itemId == null) return;
        new VoidstoneGUI(plugin, itemId).open(player);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        VoidstoneManager manager = plugin.voidstones();
        if (manager == null || !manager.isVoidstone(event.getItem())) return;

        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && isInteractableBlock(event.getClickedBlock().getType())
                && !event.getPlayer().isSneaking()) {
            return;
        }

        event.setCancelled(true);
        String itemId = manager.readId(event.getItem());
        if (itemId == null) return;
        new VoidstoneGUI(plugin, itemId).open(event.getPlayer());
    }

    private static boolean isInteractableBlock(org.bukkit.Material material) {
        return org.bukkit.Tag.DOORS.isTagged(material)
                || org.bukkit.Tag.TRAPDOORS.isTagged(material)
                || org.bukkit.Tag.FENCE_GATES.isTagged(material)
                || org.bukkit.Tag.BUTTONS.isTagged(material)
                || org.bukkit.Tag.PRESSURE_PLATES.isTagged(material)
                || material == org.bukkit.Material.CHEST
                || material == org.bukkit.Material.TRAPPED_CHEST
                || material == org.bukkit.Material.ENDER_CHEST
                || material == org.bukkit.Material.FURNACE
                || material == org.bukkit.Material.BLAST_FURNACE
                || material == org.bukkit.Material.SMOKER
                || material == org.bukkit.Material.CRAFTING_TABLE
                || material == org.bukkit.Material.ANVIL
                || material == org.bukkit.Material.CHIPPED_ANVIL
                || material == org.bukkit.Material.DAMAGED_ANVIL
                || material == org.bukkit.Material.ENCHANTING_TABLE
                || material == org.bukkit.Material.GRINDSTONE
                || material == org.bukkit.Material.STONECUTTER
                || material == org.bukkit.Material.LOOM
                || material == org.bukkit.Material.CARTOGRAPHY_TABLE
                || material == org.bukkit.Material.SMITHING_TABLE
                || material == org.bukkit.Material.BARREL
                || material == org.bukkit.Material.SHULKER_BOX
                || material.name().endsWith("_SHULKER_BOX")
                || material == org.bukkit.Material.HOPPER
                || material == org.bukkit.Material.DISPENSER
                || material == org.bukkit.Material.DROPPER
                || material == org.bukkit.Material.BEACON
                || material == org.bukkit.Material.BREWING_STAND
                || material == org.bukkit.Material.CAULDRON
                || material == org.bukkit.Material.WATER_CAULDRON
                || material == org.bukkit.Material.LAVA_CAULDRON
                || material == org.bukkit.Material.POWDER_SNOW_CAULDRON
                || material == org.bukkit.Material.LEVER
                || material == org.bukkit.Material.NOTE_BLOCK
                || material == org.bukkit.Material.JUKEBOX
                || material == org.bukkit.Material.BELL
                || material == org.bukkit.Material.LECTERN
                || material == org.bukkit.Material.COMPOSTER
                || material == org.bukkit.Material.FLOWER_POT;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null || !manager.isVoidstone(event.getItem())) return;

        String itemId = manager.readId(event.getItem());
        int slot = manager.findSlot(event.getSource(), itemId);
        event.setCancelled(true);
        if (slot < 0) return;
        manager.pushNextStack(event.getSource(), slot, event.getDestination());
    }
}
