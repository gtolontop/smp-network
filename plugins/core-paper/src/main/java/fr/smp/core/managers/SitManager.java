package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Sit-on-block system. Spawns a tiny invisible armor stand at the block
 * the player targets, seats the player on it, and removes the stand when
 * the player sneaks / dismounts / quits.
 *
 * Why armor stand and not AreaEffectCloud: the seated offset for AEC
 * changed across MC versions and has ridden-in-by-player jitter on some
 * Paper builds. An invisible, marker armor stand is the most stable
 * seat entity across current releases.
 */
public class SitManager implements Listener {

    private final SMPCore plugin;
    private final ConcurrentMap<UUID, UUID> seats = new ConcurrentHashMap<>();

    public SitManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isSeated(Player p) {
        return seats.containsKey(p.getUniqueId());
    }

    public boolean sit(Player p, Location target) {
        if (seats.containsKey(p.getUniqueId())) return false;
        Location seat = target.clone().add(0.5, 0.05, 0.5);
        seat.setYaw(p.getLocation().getYaw());
        ArmorStand stand = (ArmorStand) p.getWorld().spawnEntity(seat, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        stand.setSilent(true);
        stand.setSmall(true);
        stand.setPersistent(false);
        stand.addPassenger(p);
        seats.put(p.getUniqueId(), stand.getUniqueId());
        return true;
    }

    public void stand(Player p) {
        UUID seat = seats.remove(p.getUniqueId());
        if (seat == null) return;
        p.getWorld().getEntitiesByClass(ArmorStand.class).stream()
                .filter(e -> e.getUniqueId().equals(seat))
                .findFirst()
                .ifPresent(e -> { e.eject(); e.remove(); });
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        if (isSeated(event.getPlayer())) stand(event.getPlayer());
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        stand(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stand(event.getPlayer());
    }
}
