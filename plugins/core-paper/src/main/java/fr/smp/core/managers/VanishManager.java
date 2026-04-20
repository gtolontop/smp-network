package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff vanish. Hides the player from every non-admin viewer, disables
 * mob targeting + damage + item interactions. Restores state on toggle
 * off or quit. Uses {@link Player#hidePlayer(org.bukkit.plugin.Plugin, Player)}
 * which also removes them from tablist entries — our TabListManager
 * updates network counts on the next tick.
 */
public class VanishManager implements Listener {

    private final SMPCore plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    public Set<UUID> all() {
        return new HashSet<>(vanished);
    }

    public boolean toggle(Player p) {
        if (isVanished(p)) { show(p); return false; }
        hide(p); return true;
    }

    private void hide(Player p) {
        vanished.add(p.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.hasPermission("smp.vanish.see")) other.hidePlayer(plugin, p);
        }
        p.setAllowFlight(true);
        p.setFlying(true);
        p.sendMessage(Msg.ok("<gray>Vanish <green>activé</green>.</gray>"));
    }

    private void show(Player p) {
        vanished.remove(p.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, p);
        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        p.sendMessage(Msg.ok("<gray>Vanish <red>désactivé</red>.</gray>"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && !joined.hasPermission("smp.vanish.see")) joined.hidePlayer(plugin, v);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isVanished(event.getPlayer())) vanished.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && isVanished(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p && isVanished(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isVanished(event.getPlayer())) return;
        // Allow air interactions (tools) but block block/entity interactions
        // that would reveal the vanished player's presence.
        if (event.getClickedBlock() != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (isVanished(event.getPlayer())) event.setCancelled(true);
    }
}
