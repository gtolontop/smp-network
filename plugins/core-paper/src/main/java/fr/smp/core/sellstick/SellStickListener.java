package fr.smp.core.sellstick;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class SellStickListener implements Listener {

    private final SMPCore plugin;
    private final SellStickManager manager;

    public SellStickListener(SMPCore plugin, SellStickManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!manager.isSellStick(hand)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST
                && block.getType() != Material.BARREL) return;

        if (!(block.getState() instanceof Chest chest)) return;

        event.setCancelled(true);

        int level = manager.getLevel(hand);
        double mult = manager.multiplier(level);

        Inventory inv = chest.getBlockInventory();
        double total = 0;
        int items = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            double v = plugin.worth().worth(s);
            if (v <= 0) continue;
            total += v * mult;
            items += s.getAmount();
            inv.setItem(i, null);
        }

        if (total <= 0) {
            player.sendMessage(Msg.info("<gray>Rien a vendre dans ce coffre.</gray>"));
            return;
        }

        plugin.economy().deposit(player.getUniqueId(), total, "sell.stick.l" + level);
        plugin.getSyncManager().markDirty(player);
        player.sendMessage(Msg.ok("<green>Vendu <yellow>x" + items + "</yellow> pour <yellow>$"
                + Msg.money(total) + "</yellow> <gray>(x" + mult + ")</gray>.</green>"));
        plugin.logs().log(LogCategory.SELL, player, "stick l" + level + " items=" + items + " $" + total);
        plugin.getLogger().info("[SELL] " + player.getName() + " sell-stick L" + level + " x" + items + " pour $" + Msg.money(total) + " (x" + mult + ")");
    }
}
