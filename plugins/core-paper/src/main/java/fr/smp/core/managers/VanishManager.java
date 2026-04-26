package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff vanish.
 *
 * - Simule une déconnexion pour les non-admins (message de quit/join factice).
 * - Cache le joueur vanishé aux non-admins via hidePlayer.
 * - Désactive les collisions avec setCollidable(false).
 * - Annule les dégâts subis.
 * - Annule le ciblage par les mobs.
 * - Annule le drop d'items (un vanish qui jette des items révèle sa position).
 * - Par défaut, annule le ramassage d'items au sol ; /vanish pickup pour activer.
 */
public class VanishManager implements Listener {

    private final SMPCore plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pickupEnabled = ConcurrentHashMap.newKeySet();

    public VanishManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    public boolean isPickupEnabled(Player p) {
        return pickupEnabled.contains(p.getUniqueId());
    }

    /**
     * Toggle pickup d'items pour un joueur vanishé.
     * @return true si activé, false si désactivé
     */
    public boolean togglePickup(Player p) {
        if (pickupEnabled.contains(p.getUniqueId())) {
            pickupEnabled.remove(p.getUniqueId());
            return false;
        }
        pickupEnabled.add(p.getUniqueId());
        return true;
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

        // Fake quit pour les non-admins
        Component fakeQuit = Msg.mm("<yellow>" + p.getName() + " a quitté le jeu");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            if (!other.hasPermission("smp.vanish.see")) {
                other.hidePlayer(plugin, p);
                other.sendMessage(fakeQuit);
            }
        }

        // Info admins
        Component adminMsg = Msg.info("<gray>" + p.getName() + " est maintenant en vanish");
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("smp.vanish.see") && !admin.getUniqueId().equals(p.getUniqueId())) {
                admin.sendMessage(adminMsg);
            }
        }

        p.setAllowFlight(true);
        p.setFlying(true);
        p.setCollidable(false);
        p.sendMessage(Msg.ok("<gray>Vanish <green>activé</green>. <white>/vanish pickup</white> pour ramasser les items."));
    }

    private void show(Player p) {
        vanished.remove(p.getUniqueId());
        pickupEnabled.remove(p.getUniqueId());

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, p);
        }

        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        p.setCollidable(true);

        // Fake join pour les non-admins
        Component fakeJoin = Msg.mm("<yellow>" + p.getName() + " a rejoint le jeu");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(p.getUniqueId()) && !other.hasPermission("smp.vanish.see")) {
                other.sendMessage(fakeJoin);
            }
        }

        p.sendMessage(Msg.ok("<gray>Vanish <red>désactivé</red>."));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && !joined.hasPermission("smp.vanish.see")) {
                joined.hidePlayer(plugin, v);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isVanished(event.getPlayer())) {
            vanished.remove(event.getPlayer().getUniqueId());
            pickupEnabled.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && isVanished(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p && isVanished(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isVanished(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && isVanished(p) && !isPickupEnabled(p)) {
            event.setCancelled(true);
        }
    }
}
