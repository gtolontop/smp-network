package fr.smp.core.managers;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager implements Listener {

    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (frozenPlayers.contains(uuid)) {
            unfreeze(player);
            return false;
        }
        freeze(player);
        return true;
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    public void freeze(Player player) {
        frozenPlayers.add(player.getUniqueId());
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    public void unfreeze(Player player) {
        frozenPlayers.remove(player.getUniqueId());
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && !player.hasPermission("smp.admin")) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    public Set<UUID> getFrozenPlayers() {
        return Collections.unmodifiableSet(frozenPlayers);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!frozenPlayers.contains(e.getPlayer().getUniqueId())) return;
        if (e.getFrom().getX() == e.getTo().getX() && e.getFrom().getZ() == e.getTo().getZ()
                && e.getFrom().getY() == e.getTo().getY()) return;
        e.setTo(e.getFrom().setDirection(e.getTo().getDirection()));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (frozenPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (frozenPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        frozenPlayers.remove(e.getPlayer().getUniqueId());
    }
}
