package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains the cross-day `sessions` table:
 *   - join opens a row
 *   - quit closes it (sets left_at + position + reason)
 *   - kicks set kicked=1
 *
 * Also emits a corresponding event into the daily partition so /lookup time:1d
 * sees joins/quits.
 */
public class SessionModule implements Listener {

    private final SMPLogger plugin;
    private final Map<UUID, Long> openSessionId = new HashMap<>();

    public SessionModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String ip = ipOf(p);
        String brand = "";
        try { brand = p.getClientBrandName() != null ? p.getClientBrandName() : ""; } catch (Throwable ignored) {}
        String locale = p.locale() != null ? p.locale().toString() : "";
        String version = String.valueOf(p.getProtocolVersion());

        long t = System.currentTimeMillis();
        int playerId = plugin.players().idOf(p.getUniqueId(), p.getName());

        long sid = openSession(playerId, ip, brand, locale, version, t);
        if (sid > 0) openSessionId.put(p.getUniqueId(), sid);

        EventBuilder.begin(plugin)
                .action(Action.SESSION_JOIN)
                .actor(p)
                .at(p)
                .text(ip + "|" + brand + "|" + locale + "|" + version)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Long sid = openSessionId.remove(p.getUniqueId());
        long t = System.currentTimeMillis();
        if (sid != null) {
            closeSession(sid, t, p, false, null);
        }
        EventBuilder.begin(plugin)
                .action(Action.SESSION_QUIT)
                .actor(p)
                .at(p)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        Player p = e.getPlayer();
        Long sid = openSessionId.remove(p.getUniqueId());
        long t = System.currentTimeMillis();
        String reason = "";
        try {
            reason = e.reason() != null
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.reason())
                    : "";
        } catch (Throwable ignored) {}
        if (sid != null) closeSession(sid, t, p, true, reason);
        EventBuilder.begin(plugin)
                .action(Action.SESSION_KICK)
                .actor(p)
                .at(p)
                .text(reason)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        EventBuilder.begin(plugin)
                .action(Action.WORLD_CHANGE)
                .actor(e.getPlayer())
                .at(e.getPlayer())
                .meta(plugin.worlds().idOf(e.getFrom()))
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getFrom() == null || e.getTo() == null) return;
        if (e.getFrom().getWorld() == e.getTo().getWorld()
                && e.getFrom().distanceSquared(e.getTo()) < 4) return; // ignore tiny shifts
        EventBuilder.begin(plugin)
                .action(Action.TELEPORT)
                .actor(e.getPlayer())
                .at(e.getTo())
                .meta(e.getCause().ordinal())
                .submit();
    }

    private static String ipOf(Player p) {
        try {
            InetSocketAddress addr = p.getAddress();
            return addr == null ? "unknown" : addr.getAddress().getHostAddress();
        } catch (Throwable t) { return "unknown"; }
    }

    private long openSession(int playerId, String ip, String brand, String locale, String version, long t) {
        try (Connection c = plugin.db().writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sessions(player_id, ip, brand, locale, version, joined_at) "
                             + "VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, playerId);
            ps.setString(2, ip);
            ps.setString(3, brand);
            ps.setString(4, locale);
            ps.setString(5, version);
            ps.setLong(6, t);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Open session failed: " + e.getMessage());
        }
        return 0;
    }

    private void closeSession(long sid, long t, Player p, boolean kicked, String reason) {
        try (Connection c = plugin.db().writer();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sessions SET left_at=?, last_world_id=?, last_x=?, last_y=?, last_z=?, "
                             + "kicked=?, quit_reason=? WHERE id=?")) {
            ps.setLong(1, t);
            ps.setInt(2, plugin.worlds().idOf(p.getWorld()));
            ps.setInt(3, p.getLocation().getBlockX());
            ps.setInt(4, p.getLocation().getBlockY());
            ps.setInt(5, p.getLocation().getBlockZ());
            ps.setInt(6, kicked ? 1 : 0);
            ps.setString(7, reason == null ? "" : reason);
            ps.setLong(8, sid);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Close session failed: " + e.getMessage());
        }
    }
}
