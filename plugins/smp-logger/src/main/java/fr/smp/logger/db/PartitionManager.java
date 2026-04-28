package fr.smp.logger.db;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.PartitionKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Owns the lifecycle of events_YYYYMMDD partition tables.
 *  - {@link #ensure(PartitionKey)} creates today's partition lazily on first write.
 *  - {@link #purge(int)} drops partitions older than retention. DROP is O(1) and
 *    needs no VACUUM, vs DELETE which would fragment the file.
 *
 * Thread-safety: ensure() can be called from the writer thread; purge() runs
 * on a scheduled task. We track ensured tables in a memory set so the hot path
 * is a single hashset hit per event.
 */
public class PartitionManager {

    private final SMPLogger plugin;
    private final Database db;
    private final Set<String> ensured = ConcurrentHashMap.newKeySet();

    public PartitionManager(SMPLogger plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        // Today's partition up-front so first event isn't slowed by DDL.
        ensure(PartitionKey.today());
        // Re-scan registry after restart so we don't try to recreate existing tables.
        try (Connection c = db.reader();
             PreparedStatement ps = c.prepareStatement("SELECT table_name FROM partitions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ensured.add(rs.getString(1));
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load partition registry: " + e.getMessage());
        }

        // Daily purge — UTC midnight + a few minutes. Run every 6h to be safe.
        long sixHoursTicks = 6L * 60 * 60 * 20;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                () -> purge(plugin.getConfig().getInt("retention.days", 7)),
                sixHoursTicks, sixHoursTicks);
    }

    /** Idempotent — returns the table name for the partition. */
    public String ensure(PartitionKey key) {
        String table = key.table();
        if (ensured.contains(table)) return table;
        synchronized (ensured) {
            if (ensured.contains(table)) return table;
            createTable(table, key);
            ensured.add(table);
        }
        return table;
    }

    private void createTable(String table, PartitionKey key) {
        // We CAN'T parameterize table names. The PartitionKey constructor only
        // accepts LocalDate so the name is always 'events_YYYYMMDD' — safe.
        String ddl = """
            CREATE TABLE IF NOT EXISTS %s (
              id INTEGER PRIMARY KEY,
              t INTEGER NOT NULL,
              action INTEGER NOT NULL,
              actor INTEGER NOT NULL DEFAULT 0,
              target INTEGER NOT NULL DEFAULT 0,
              world INTEGER NOT NULL DEFAULT 0,
              x INTEGER NOT NULL DEFAULT 0,
              y INTEGER NOT NULL DEFAULT 0,
              z INTEGER NOT NULL DEFAULT 0,
              material INTEGER NOT NULL DEFAULT 0,
              amount INTEGER NOT NULL DEFAULT 0,
              item_hash BLOB,
              text_id INTEGER NOT NULL DEFAULT 0,
              meta INTEGER NOT NULL DEFAULT 0
            )
            """.formatted(table);

        // Minimal indexes — every extra index ~= 2x storage on hot tables.
        // (actor, t) covers /lookup player:foo, (world, x, z, t) covers spatial /inspect.
        String idxActor = "CREATE INDEX IF NOT EXISTS idx_" + table + "_actor ON " + table + "(actor, t)";
        String idxLoc = "CREATE INDEX IF NOT EXISTS idx_" + table + "_loc ON " + table + "(world, x, z, y, t)";
        String idxAction = "CREATE INDEX IF NOT EXISTS idx_" + table + "_action ON " + table + "(action, t)";

        try (Connection c = db.writer();
             Statement s = c.createStatement()) {
            s.execute(ddl);
            s.execute(idxActor);
            s.execute(idxLoc);
            s.execute(idxAction);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create partition " + table + ": " + e.getMessage());
            throw new RuntimeException(e);
        }

        try (Connection c = db.writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO partitions(date, table_name, created_at) VALUES (?, ?, ?)")) {
            ps.setString(1, key.date().format(PartitionKey.FMT));
            ps.setString(2, table);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not register partition " + table + ": " + e.getMessage());
        }
    }

    public void purge(int retentionDays) {
        LocalDate cutoff = LocalDate.now(PartitionKey.ZONE).minusDays(retentionDays);
        Set<String> toDrop = new HashSet<>();
        try (Connection c = db.reader();
             PreparedStatement ps = c.prepareStatement("SELECT date, table_name FROM partitions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDate d = LocalDate.parse(rs.getString(1), PartitionKey.FMT);
                if (d.isBefore(cutoff)) toDrop.add(rs.getString(2));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Purge scan failed: " + e.getMessage());
            return;
        }
        if (toDrop.isEmpty()) return;
        try (Connection c = db.writer(); Statement s = c.createStatement()) {
            for (String tbl : toDrop) {
                s.execute("DROP TABLE IF EXISTS " + tbl);
                s.execute("DELETE FROM partitions WHERE table_name = '" + tbl + "'");
                ensured.remove(tbl);
            }
            s.execute("PRAGMA incremental_vacuum");
            plugin.getLogger().info("Purged " + toDrop.size() + " expired partitions");
        } catch (SQLException e) {
            plugin.getLogger().warning("Purge drop failed: " + e.getMessage());
        }
    }

    /** Dump current ensured set (for /loggerstats). */
    public Set<String> ensuredTables() {
        return Set.copyOf(ensured);
    }
}
