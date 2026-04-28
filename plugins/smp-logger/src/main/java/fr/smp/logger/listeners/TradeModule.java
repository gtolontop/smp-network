package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import fr.smp.logger.trade.TradeDetector;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

/**
 * Drives TradeDetector + emits raw DROP / PICKUP events. Villager trades are
 * detected via InventoryClickEvent on a MERCHANT inventory.
 */
public class TradeModule implements Listener {

    private final SMPLogger plugin;

    public TradeModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Item item = e.getItemDrop();
        ItemStack stack = item.getItemStack();
        EventBuilder.begin(plugin)
                .action(Action.DROP_ITEM)
                .actor(p)
                .at(p)
                .material(stack.getType())
                .amount(stack.getAmount())
                .item(stack)
                .submit();
        plugin.tradeDetector().recordDrop(p, item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Item it = e.getItem();
        ItemStack stack = it.getItemStack();
        EventBuilder.begin(plugin)
                .action(Action.PICKUP_ITEM)
                .actor(p)
                .at(p)
                .material(stack.getType())
                .amount(stack.getAmount())
                .item(stack)
                .submit();
        plugin.tradeDetector().tryPairPickup(p, it);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerchantClick(InventoryClickEvent e) {
        if (e.getInventory().getType() != InventoryType.MERCHANT) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() != 2) return; // result slot
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (!(e.getInventory() instanceof MerchantInventory)) return;
        plugin.tradeDetector().recordVillagerTrade(p, p.getLocation(), result);
    }
}
