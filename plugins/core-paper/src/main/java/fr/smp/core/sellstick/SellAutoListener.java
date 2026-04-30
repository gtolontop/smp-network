package fr.smp.core.sellstick;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.sell.SellCategory;
import fr.smp.core.sell.SellTierManager;
import fr.smp.core.utils.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class SellAutoListener implements Listener {

    private final SMPCore plugin;
    private final SellAutoManager manager;

    public SellAutoListener(SMPCore plugin, SellAutoManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager.isEnabled(player)) return;

        ItemStack item = event.getItem().getItemStack();
        double base = plugin.worth().worth(item);
        if (base <= 0) return;

        PlayerData data = plugin.players().get(player.getUniqueId());
        SellCategory cat = SellCategory.of(item.getType());
        double mult = (cat != null && data != null)
                ? plugin.sellTiers().multiplier(data, cat)
                : 1.0;
        double finalValue = base * mult;

        event.setCancelled(true);
        event.getItem().remove();

        plugin.economy().deposit(player.getUniqueId(), finalValue, "sell.auto");
        plugin.getSyncManager().markDirty(player);
        if (data != null && cat != null) {
            boolean levelUp = plugin.sellTiers().recordSale(player.getUniqueId(),
                    item.getType(), item.getAmount(), finalValue);
            if (levelUp) {
                int newTier = SellTierManager.tierFor(data.tierSellCount(cat.ordinal()));
                double newMult = SellTierManager.MULTIPLIERS[newTier];
                player.sendMessage(Msg.ok("<gold>★ Palier débloqué — <yellow>"
                        + cat.displayName() + " T" + newTier
                        + "</yellow> <gray>(<green>x" + fmtMult(newMult) + "</green>)</gray>"));
            }
        }
        manager.queueActionBar(player, finalValue);
        plugin.logs().log(LogCategory.SELL, player,
            "auto " + item.getType() + " x" + item.getAmount() + " $" + finalValue
                    + " (base=" + base + " mult=" + mult + ")");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.remove(event.getPlayer());
    }

    private static String fmtMult(double m) {
        if (m == Math.floor(m)) return String.format("%.0f", m);
        return String.format("%.2f", m);
    }
}
