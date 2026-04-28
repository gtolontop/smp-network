package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Logs ONLY chunk transitions per player. Filters in-chunk movement so the queue
 * isn't flooded by walking players. Heat-map / route reconstruction works at
 * chunk granularity (16-block resolution) — plenty for trail analysis.
 */
public class MovementModule implements Listener {

    private final SMPLogger plugin;
    private final Map<UUID, long[]> lastChunk = new HashMap<>(); // value: {x,z,worldId}

    public MovementModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        Player p = e.getPlayer();
        int cx = to.getBlockX() >> 4;
        int cz = to.getBlockZ() >> 4;
        int wid = plugin.worlds().idOf(p.getWorld());
        long[] prev = lastChunk.get(p.getUniqueId());
        if (prev != null && prev[0] == cx && prev[1] == cz && prev[2] == wid) return;
        lastChunk.put(p.getUniqueId(), new long[]{cx, cz, wid});
        EventBuilder.begin(plugin)
                .action(Action.CHUNK_TRANSITION)
                .actor(p)
                .world(p.getWorld())
                .coords(cx, to.getBlockY(), cz)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        lastChunk.remove(e.getPlayer().getUniqueId());
    }
}
