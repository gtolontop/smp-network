package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Sell drop chest: a double-chest-sized inventory the player can drop items into.
 * On close, every item present with worth > 0 is sold. Items without worth are returned.
 */
public class SellGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;

    public SellGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f6d365:#fda085><bold>Sell</bold></gradient>"));
        this.inventory = inv;
        p.openInventory(inv);
        p.sendMessage(Msg.info("<gray>Dépose les items dans le coffre. Ferme pour vendre.</gray>"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // Allow free interaction inside this GUI (do NOT cancel in GUIListener).
        event.setCancelled(false);
    }

    @Override
    public void onClose(HumanEntity who) {
        if (!(who instanceof Player p) || inventory == null) return;
        double total = 0;
        int items = 0;
        int returned = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            double v = plugin.worth().worth(s);
            if (v <= 0) {
                // Return to player
                var overflow = p.getInventory().addItem(s.clone());
                overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                returned += s.getAmount();
            } else {
                total += v;
                items += s.getAmount();
            }
            inventory.setItem(i, null);
        }
        if (total > 0) {
            plugin.economy().deposit(p.getUniqueId(), total, "sell.gui");
            p.sendMessage(Msg.ok("<green>Vendu <yellow>×" + items + "</yellow> pour <yellow>$" +
                    Msg.money(total) + "</yellow>.</green>"));
            plugin.logs().log(LogCategory.SELL, p, "gui items=" + items + " $" + total);
        } else if (returned == 0) {
            p.sendMessage(Msg.info("<gray>Rien à vendre.</gray>"));
        }
        if (returned > 0) {
            p.sendMessage(Msg.info("<yellow>" + returned + " items sans valeur retournés.</yellow>"));
        }
    }
}
