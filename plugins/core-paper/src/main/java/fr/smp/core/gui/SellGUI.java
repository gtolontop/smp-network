package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.commands.SellCommand;
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

import java.util.LinkedHashMap;
import java.util.Map;

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
        plugin.getLogger().info("[SELL] " + p.getName() + " a ouvert le GUI sell");
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
        Map<Material, double[]> breakdown = new LinkedHashMap<>();
        Map<Material, Integer> returnedBreakdown = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            double v = plugin.worth().worth(s);
            if (v <= 0) {
                // Return to player
                var overflow = p.getInventory().addItem(s.clone());
                overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                returned += s.getAmount();
                returnedBreakdown.merge(s.getType(), s.getAmount(), Integer::sum);
            } else {
                total += v;
                items += s.getAmount();
                SellCommand.accumulate(breakdown, s.getType(), s.getAmount(), v);
            }
            inventory.setItem(i, null);
        }
        if (total > 0) {
            double before = plugin.economy().balance(p.getUniqueId());
            plugin.economy().deposit(p.getUniqueId(), total, "sell.gui");
            double after = plugin.economy().balance(p.getUniqueId());
            p.sendMessage(Msg.ok("<green>Vendu <yellow>×" + items + "</yellow> pour <yellow>$" +
                    Msg.money(total) + "</yellow>.</green>"));
            String log = SellCommand.formatLog("gui", items, total, before, after, breakdown);
            if (returned > 0) {
                StringBuilder rb = new StringBuilder();
                returnedBreakdown.forEach((m, c) -> {
                    if (rb.length() > 0) rb.append(" | ");
                    rb.append(m.name()).append(" x").append(c);
                });
                log += " returned=" + returned + " returnedBreakdown={" + rb + "}";
            }
            plugin.logs().log(LogCategory.SELL, p, log);
            plugin.getLogger().info("[SELL] " + p.getName() + " " + log);
        } else if (returned == 0) {
            p.sendMessage(Msg.info("<gray>Rien à vendre.</gray>"));
        }
        if (returned > 0) {
            p.sendMessage(Msg.info("<yellow>" + returned + " items sans valeur retournés.</yellow>"));
        }
    }
}
