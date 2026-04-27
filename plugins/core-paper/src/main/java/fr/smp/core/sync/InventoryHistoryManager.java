package fr.smp.core.sync;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps a rolling history of per-player state snapshots so an admin can
 * rollback an inventory after a dupe, a griefer-robbery, or a mistake.
 *
 * Snapshots are stored in the shared SQLite DB (table inv_snapshots) as YAML
 * text, reusing the exact serialization path that {@link SyncManager} uses for
 * cross-server sync. Capture happens on the main thread (needs Bukkit state);
 * most DB writes happen off-thread, but critical flows (shutdown, pre-apply)
 * can force a synchronous insert.
 */
public class InventoryHistoryManager implements Listener {

    public record Entry(long id, UUID uuid, String name, String source,
                        String server, long createdAt) {}

    public enum Source {
        PERIODIC, QUIT, MANUAL, PREAPPLY, SHUTDOWN;
        String key() { return name().toLowerCase(); }
    }

    private final SMPCore plugin;
    private final Database db;
    private final SyncManager sync;
    private final boolean enabled;
    private final int intervalTicks;
    private final int keepPerPlayer;
    private BukkitTask periodicTask;

    public InventoryHistoryManager(SMPCore plugin, Database db, SyncManager sync) {
        this.plugin = plugin;
        this.db = db;
        this.sync = sync;
        this.enabled = plugin.getConfig().getBoolean("sync.history.enabled", true);
        int minutes = Math.max(1, plugin.getConfig().getInt("sync.history.interval-minutes", 1));
        this.intervalTicks = minutes * 60 * 20;
        this.keepPerPlayer = Math.max(5, plugin.getConfig().getInt("sync.history.keep-per-player", 50));
    }

    public boolean isEnabled() { return enabled; }

    public void start() {
        if (!enabled) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll,
                intervalTicks, intervalTicks);
        plugin.getLogger().info("Inventory history enabled "
                + "(interval=" + (intervalTicks / 20 / 60) + "m, keep=" + keepPerPlayer + ")");
    }

    public void stop() {
        if (periodicTask != null) { periodicTask.cancel(); periodicTask = null; }
    }

    private void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("smp.sync.bypass")) continue;
            snapshot(p, Source.PERIODIC);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission("smp.sync.bypass")) return;
        snapshot(p, Source.QUIT);
    }

    /**
     * Capture the player's current state on the main thread and queue an async
     * DB write. Returns a future-less void — failures are logged, not thrown.
     */
    public void snapshot(Player player, Source source) {
        snapshot(player, source, true);
    }

    public boolean snapshotNow(Player player, Source source) {
        return snapshot(player, source, false);
    }

    public void snapshotAllOnlineNow(Source source) {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("smp.sync.bypass")) continue;
            snapshotNow(player, source);
        }
    }

    private boolean snapshot(Player player, Source source, boolean async) {
        if (!enabled) return false;
        YamlConfiguration yaml;
        try {
            yaml = sync.captureYaml(player);
        } catch (Throwable t) {
            plugin.getLogger().warning("invhistory.capture " + player.getName() + ": " + t.getMessage());
            return false;
        }
        String dump = yaml.saveToString();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String server = plugin.getServerType();
        String src = source.key();
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> insert(uuid, name, src, server, dump));
            return true;
        }
        return insert(uuid, name, src, server, dump);
    }

    private boolean insert(UUID uuid, String name, String source, String server, String yaml) {
        long now = System.currentTimeMillis();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inv_snapshots(uuid, name, source, server, created_at, yaml) VALUES(?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, source);
            ps.setString(4, server);
            ps.setLong(5, now);
            ps.setString(6, yaml);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("invhistory.insert " + name + ": " + e.getMessage());
            return false;
        }
        prune(uuid);
        return true;
    }

    private void prune(UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM inv_snapshots WHERE uuid=? AND id NOT IN ("
                             + "  SELECT id FROM inv_snapshots WHERE uuid=? ORDER BY created_at DESC LIMIT ?"
                             + ")")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setInt(3, keepPerPlayer);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("invhistory.prune " + uuid + ": " + e.getMessage());
        }
    }

    /** Most recent snapshots for a player (head = newest). */
    public List<Entry> list(UUID uuid, int limit) {
        List<Entry> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, uuid, name, source, server, created_at FROM inv_snapshots "
                             + "WHERE uuid=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(1, Math.min(200, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(
                            rs.getLong(1),
                            UUID.fromString(rs.getString(2)),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getLong(6)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("invhistory.list " + uuid + ": " + e.getMessage());
        }
        return out;
    }

    /** Load the YAML body of a snapshot by id. Null if not found or malformed. */
    public YamlConfiguration load(long id) {
        String text;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT yaml FROM inv_snapshots WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                text = rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("invhistory.load " + id + ": " + e.getMessage());
            return null;
        }
        if (text == null) return null;
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.loadFromString(text); }
        catch (InvalidConfigurationException e) {
            plugin.getLogger().warning("invhistory.load " + id + " parse: " + e.getMessage());
            return null;
        }
        return yaml;
    }

    public Entry meta(long id) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, uuid, name, source, server, created_at FROM inv_snapshots WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Entry(rs.getLong(1), UUID.fromString(rs.getString(2)),
                            rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("invhistory.meta " + id + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Apply a stored snapshot to a live player. Takes a PREAPPLY snapshot of
     * the player's current state first, so the operation is itself reversible.
     * Returns true on success.
     */
    public boolean applyTo(long snapshotId, Player target, String actor) {
        YamlConfiguration yaml = load(snapshotId);
        if (yaml == null) return false;
        if (!snapshotNow(target, Source.PREAPPLY)) {
            plugin.getLogger().warning("invhistory.apply " + snapshotId
                    + " -> " + target.getName() + ": failed to create PREAPPLY snapshot");
            return false;
        }
        try {
            sync.applyYaml(target, yaml);
            sync.save(target);
        } catch (Throwable t) {
            plugin.getLogger().warning("invhistory.apply " + snapshotId
                    + " -> " + target.getName() + ": " + t.getMessage());
            return false;
        }
        plugin.logs().log(LogCategory.ADMIN,
                "invrollback apply id=" + snapshotId
                        + " target=" + target.getName()
                        + " actor=" + actor);
        return true;
    }

    /** Public helper so /invrollback snap can request a manual snapshot. */
    public void snapshotManual(Player p, String actor) {
        snapshot(p, Source.MANUAL);
        plugin.logs().log(LogCategory.ADMIN,
                "invrollback snap target=" + p.getName() + " actor=" + actor);
    }

    /** Used by the command to resolve a name → uuid even when offline. */
    public UUID resolveUuid(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p.getUniqueId();
        }
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid FROM inv_snapshots WHERE name=? COLLATE NOCASE "
                             + "ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid FROM players WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return null;
    }
}
