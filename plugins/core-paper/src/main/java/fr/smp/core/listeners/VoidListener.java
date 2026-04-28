package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

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

        // Anti-fuite combat: si tag, on le tue et son stuff drop chez l'attaquant.
        if (plugin.combat() != null && plugin.combat().isTagged(p)) {
            UUID attackerId = plugin.combat().lastAttacker(p.getUniqueId());
            Player attacker = attackerId != null ? Bukkit.getPlayer(attackerId) : null;
            p.setFallDistance(0f);
            if (attacker != null && attacker.isOnline()) {
                p.teleport(attacker.getLocation());
                p.damage(p.getHealth() + 1000.0, attacker);
            } else {
                Location s = plugin.spawns().spawn();
                if (s != null) p.teleport(s);
                p.setHealth(0);
            }
            return;
        }

        // Sinon: pas de mort, pas de perte d'items. Lit s'il existe, sinon RTP.
        p.setFallDistance(0f);
        Location bed = p.getRespawnLocation();
        if (bed != null) {
            p.teleport(bed);
            p.sendMessage(Msg.info("<aqua>Tu as failli mourir dans le void — téléporté à ton lit.</aqua>"));
            return;
        }

        Location spawn = plugin.spawns().spawn();
        if (spawn != null) p.teleport(spawn);

        World world = event.getTo().getWorld();
        if (world != null && plugin.rtp() != null) {
            p.sendMessage(Msg.info("<aqua>Tu as failli mourir dans le void — téléportation aléatoire...</aqua>"));
            plugin.rtp().teleport(p, world, false);
        }
    }
}
