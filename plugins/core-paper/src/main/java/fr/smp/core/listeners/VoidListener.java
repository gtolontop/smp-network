package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class VoidListener implements Listener {

    private final SMPCore plugin;

    public VoidListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getTo().getY() > -64.0) return;

        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.hasPermission("smp.admin")) return;

        if (plugin.getMessageChannel() != null) {
            plugin.getMessageChannel().sendTransfer(p, "lobby");
        }
    }
}
