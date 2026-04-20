package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects a per-unit "Worth" line into item lore while inventories are open.
 * Lore is constant per material so identical items keep stacking. The total
 * for the held stack is shown via actionbar so it never touches the item.
 */
public class WorthHoverListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final SMPCore plugin;
    private final NamespacedKey markerKey;

    public WorthHoverListener(SMPCore plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "worth_lore_lines");
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getOpenInventory() != null) decorate(p.getInventory());
                    sendHeldTotal(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        decorate(p.getInventory());
        Inventory top = event.getInventory();
        if (top != p.getInventory()) decorate(top);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        strip(p.getInventory());
        Inventory top = event.getInventory();
        if (top != p.getInventory()) strip(top);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        strip(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (stack == null || stack.getType().isAir()) return;
        if (decorateStack(stack)) entity.setItemStack(stack);
    }

    private void sendHeldTotal(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return;
        double unit = plugin.worth().worth(held.getType());
        if (unit <= 0) return;
        if (held.getAmount() <= 1) return;
        double total = unit * held.getAmount();
        p.sendActionBar(MM.deserialize(
                "<gray>Total: <yellow>$" + Msg.money(total) +
                "</yellow> <dark_gray>(" + held.getAmount() + " × $" + Msg.money(unit) + ")</dark_gray>"));
    }

    private void decorate(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            decorateStack(stack);
        }
    }

    private boolean decorateStack(ItemStack stack) {
        double unit = plugin.worth().worth(stack.getType());
        if (unit <= 0) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (meta.getPersistentDataContainer().has(markerKey, PersistentDataType.INTEGER)) return false;
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        Component line = MM.deserialize(
                "<!italic><green>$</green> <white>Worth:</white> <yellow>$" + Msg.money(unit) + "</yellow>");
        lore.add(line);
        meta.lore(lore);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.INTEGER, 1);
        stack.setItemMeta(meta);
        return true;
    }

    private void strip(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) continue;
            if (stripFromMeta(meta)) stack.setItemMeta(meta);
        }
    }

    private boolean stripFromMeta(ItemMeta meta) {
        Integer count = meta.getPersistentDataContainer().get(markerKey, PersistentDataType.INTEGER);
        if (count == null || count <= 0) return false;
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            meta.getPersistentDataContainer().remove(markerKey);
            return true;
        }
        List<Component> trimmed = new ArrayList<>(lore);
        for (int k = 0; k < count && !trimmed.isEmpty(); k++) {
            trimmed.remove(trimmed.size() - 1);
        }
        meta.lore(trimmed.isEmpty() ? null : trimmed);
        meta.getPersistentDataContainer().remove(markerKey);
        return true;
    }
}
