package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.order.OrderManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * 54-slot item picker.
 * Shows a curated list of orderable materials; clicking one calls {@code callback}.
 * The bottom row has a "Back" button.
 */
public class OrderItemPickGUI extends GUIHolder {

    /** Curated list of commonly ordered materials (44 items = 5 rows minus back button). */
    private static final Material[] ITEMS = {
        // Precious
        Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT,
        Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP, Material.COAL, Material.COPPER_INGOT,
        Material.LAPIS_LAZULI, Material.AMETHYST_SHARD, Material.QUARTZ,
        // Blocks
        Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.GOLD_BLOCK,
        Material.IRON_BLOCK, Material.NETHERITE_BLOCK, Material.COPPER_BLOCK,
        // Crops & food
        Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
        Material.MELON_SLICE, Material.PUMPKIN, Material.SUGAR_CANE,
        Material.NETHER_WART, Material.COCOA_BEANS,
        // Mob drops
        Material.STRING, Material.FEATHER, Material.LEATHER, Material.INK_SAC,
        Material.BONE_MEAL, Material.BLAZE_ROD, Material.ENDER_PEARL,
        Material.GHAST_TEAR, Material.MAGMA_CREAM, Material.SLIME_BALL, Material.GUNPOWDER,
        // Rare & special
        Material.NETHER_STAR, Material.SHULKER_SHELL, Material.ANCIENT_DEBRIS,
        Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS,
        // Wood
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
    };

    private static final int SLOT_BACK = 49;

    private final SMPCore plugin;
    private final Consumer<Material> callback;
    private final Runnable onBack;

    public OrderItemPickGUI(SMPCore plugin, Consumer<Material> callback, Runnable onBack) {
        this.plugin   = plugin;
        this.callback = callback;
        this.onBack   = onBack;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f093fb:#f5576c>Choisir un item</gradient>"));
        this.inventory = inv;

        for (int i = 0; i < ITEMS.length && i < 45; i++) {
            inv.setItem(i, GUIUtil.item(ITEMS[i],
                    "<white>" + OrderManager.prettyMat(ITEMS[i]),
                    "<gray>Cliquer pour sélectionner"));
        }

        // Bottom row filler + back button
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(SLOT_BACK, GUIUtil.item(Material.BARRIER, "<red>← Retour"));

        p.openInventory(inv);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            player.closeInventory();
            if (onBack != null) Bukkit.getScheduler().runTask(plugin, onBack);
            return;
        }
        if (slot >= 0 && slot < ITEMS.length) {
            Material chosen = ITEMS[slot];
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(chosen));
        }
    }

    @Override
    public void onClose(HumanEntity who) {}
}
