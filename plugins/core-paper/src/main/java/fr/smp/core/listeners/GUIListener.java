package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GUIListener implements Listener {

    private final SMPCore plugin;

    public GUIListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.getServerSelector().isViewer(player.getUniqueId())) return;

        // Always cancel clicks inside our GUI
        event.setCancelled(true);

        String server = plugin.getServerSelector().getServerAtSlot(event.getRawSlot());
        if (server == null) return;

        player.closeInventory();
        plugin.getMessageChannel().sendTransfer(player, server);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        plugin.getServerSelector().removeViewer(event.getPlayer().getUniqueId());
    }
}
