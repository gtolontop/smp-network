package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.GUIHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    private final SMPCore plugin;

    public GUIListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder top = event.getView().getTopInventory().getHolder();
        if (top instanceof GUIHolder holder) {
            event.setCancelled(true);
            holder.onClick(event);
            return;
        }
        // Legacy server selector (pre-existing)
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getServerSelector() == null) return;
        if (!plugin.getServerSelector().isViewer(player.getUniqueId())) return;

        event.setCancelled(true);
        String server = plugin.getServerSelector().getServerAtSlot(event.getRawSlot());
        if (server == null) return;
        player.closeInventory();
        plugin.getMessageChannel().sendTransfer(player, server);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder top = event.getView().getTopInventory().getHolder();
        if (top instanceof GUIHolder holder) {
            holder.onClose(event.getPlayer());
        }
        if (plugin.getServerSelector() != null) {
            plugin.getServerSelector().removeViewer(event.getPlayer().getUniqueId());
        }
    }
}
