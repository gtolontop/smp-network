package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AFK detector driven by head-rotation and command activity. A player is
 * flagged AFK once they have not moved OR looked around for
 * {@code afk.inactive-seconds}. {@link #accumulatedAfkSec(Player)} + total
 * playtime is used by {@code /playtime} to report true engagement time.
 */
public class AfkManager implements Listener {

    private final SMPCore plugin;
    private final ConcurrentMap<UUID, Long> lastActivityMs = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> afkStartMs = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> totalAfkMs = new ConcurrentHashMap<>();

    public AfkManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                long threshold = plugin.getConfig().getLong("afk.inactive-seconds", 120) * 1000L;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    long last = lastActivityMs.getOrDefault(p.getUniqueId(), now);
                    boolean nowAfk = now - last >= threshold;
                    boolean wasAfk = afkStartMs.containsKey(p.getUniqueId());
                    if (nowAfk && !wasAfk) afkStartMs.put(p.getUniqueId(), now);
                    else if (!nowAfk && wasAfk) clearAfk(p.getUniqueId(), now);
                }
            }
        }.runTaskTimer(plugin, 13L, 20L);
    }

    public boolean isAfk(Player p) {
        return afkStartMs.containsKey(p.getUniqueId());
    }

    public long accumulatedAfkSec(Player p) {
        long base = totalAfkMs.getOrDefault(p.getUniqueId(), 0L);
        Long begin = afkStartMs.get(p.getUniqueId());
        if (begin != null) base += System.currentTimeMillis() - begin;
        return base / 1000L;
    }

    private void activity(Player p) {
        long now = System.currentTimeMillis();
        lastActivityMs.put(p.getUniqueId(), now);
        if (afkStartMs.containsKey(p.getUniqueId())) clearAfk(p.getUniqueId(), now);
    }

    private void clearAfk(UUID id, long now) {
        Long begin = afkStartMs.remove(id);
        if (begin != null) totalAfkMs.merge(id, now - begin, Long::sum);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Location f = event.getFrom(), t = event.getTo();
        if (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ()
                || Math.abs(f.getYaw() - t.getYaw()) > 5 || Math.abs(f.getPitch() - t.getPitch()) > 5) {
            activity(event.getPlayer());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        activity(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        activity(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastActivityMs.remove(id);
        clearAfk(id, System.currentTimeMillis());
        totalAfkMs.remove(id);
    }
}
