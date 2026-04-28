package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Glue between Bukkit events and the match state machine: on death we end the
 * match (treating the death as a kill for the other side), on quit we award by
 * forfeit, on respawn we send the player home, and on out-of-bounds movement
 * we tug them back into the cylinder.
 */
public class DuelMatchListener implements Listener {

    private final SMPCore plugin;

    public DuelMatchListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        DuelMatch m = plugin.duelMatches() == null ? null : plugin.duelMatches().byPlayer(victim.getUniqueId());
        if (m == null) return;
        // Keep their items so they don't lose gear when death = match end.
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        plugin.duelMatches().handleDeath(m, victim);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        // If the player respawns while still flagged as in-match (race), send
        // them to the hub. By the time end() finished, byPlayer is cleared.
        Player p = e.getPlayer();
        DuelMatch m = plugin.duelMatches() == null ? null : plugin.duelMatches().byPlayer(p.getUniqueId());
        if (m != null && m.world() != null) {
            Location hub = plugin.spawns().hub();
            if (hub != null) e.setRespawnLocation(hub);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        DuelMatch m = plugin.duelMatches() == null ? null : plugin.duelMatches().byPlayer(e.getPlayer().getUniqueId());
        if (m == null) return;
        plugin.duelMatches().handleQuit(m, e.getPlayer().getUniqueId());
    }

    /**
     * Cylinder containment: if a dueler walks past the radius (or below
     * floor-dig, or above ceiling), nudge them back to the closest legal
     * point on the cylinder edge. Cheap squared-distance check on every move.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // Only test when the block coords change — otherwise we'd allocate a
        // PlayerMoveEvent payload's worth of state every sub-block tick.
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()) {
            return;
        }
        Player p = e.getPlayer();
        DuelMatch m = plugin.duelMatches() == null ? null : plugin.duelMatches().byPlayer(p.getUniqueId());
        if (m == null) return;
        if (m.state() != DuelMatch.State.FIGHTING && m.state() != DuelMatch.State.STARTING) return;
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) return;
        DuelArena a = m.arena();
        Location to = e.getTo();
        double dx = to.getX() - a.centerX();
        double dz = to.getZ() - a.centerZ();
        double r2 = dx * dx + dz * dz;
        double rmax = a.radius();
        double rmax2 = rmax * rmax;
        boolean outOfXz = r2 > rmax2;
        boolean outOfY = to.getY() < (a.floorY() - a.digDepth() - 1) || to.getY() > (a.floorY() + a.ceiling() + 4);
        if (outOfXz || outOfY) {
            // Project them back to a safe spot on the rim, just inside.
            double r = Math.sqrt(r2);
            double scale = (rmax - 1.0) / Math.max(0.001, r);
            double nx = a.centerX() + dx * scale;
            double nz = a.centerZ() + dz * scale;
            double ny = Math.max(a.floorY(), Math.min(to.getY(), a.floorY() + a.ceiling() - 2));
            Location fixed = new Location(to.getWorld(), nx, ny, nz, to.getYaw(), to.getPitch());
            e.setTo(fixed);
        }
    }

    /**
     * Pre-fight grace damage block: during STARTING, dueler-vs-dueler hits are
     * dropped (so the countdown isn't a free first-hit window).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        DuelMatch m = plugin.duelMatches() == null ? null : plugin.duelMatches().byPlayer(victim.getUniqueId());
        if (m == null) return;
        if (m.state() == DuelMatch.State.STARTING) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSelfDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        DuelMatch m = plugin.duelMatches() == null ? null : plugin.duelMatches().byPlayer(p.getUniqueId());
        if (m == null) return;
        if (m.state() == DuelMatch.State.STARTING) {
            // Block fall / fire / suffocation during the freeze.
            e.setCancelled(true);
        }
    }
}
