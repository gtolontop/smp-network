package fr.smp.logger.query;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.model.PartitionKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs a {@link LookupFilter} against the rolling partition window. We UNION
 * matching partitions so /lookup time:7d traverses up to 7 daily tables. SQLite
 * compiles the UNION cheaply since each branch is a single index lookup.
 */
public class LookupEngine {

    public record Row(long timeMs, Action action, int actorId, int targetId, int worldId,
                      int x, int y, int z, int materialId, int amount, byte[] itemHash,
                      int textId, int meta) {}

    private final SMPLogger plugin;

    public LookupEngine(SMPLogger plugin) {
        this.plugin = plugin;
    }

    public List<Row> run(LookupFilter f) {
        // Resolve which partitions to scan.
        long until = f.untilMs != null ? f.untilMs : System.currentTimeMillis();
        long since = f.sinceMs != null ? f.sinceMs : (until - 86_400_000L); // default: 24h
        List<PartitionKey> parts = partitionsBetween(since, until);
        if (parts.isEmpty()) return List.of();

        StringBuilder sql = new StringBuilder();
        for (PartitionKey p : parts) {
            if (sql.length() > 0) sql.append(" UNION ALL ");
            sql.append("SELECT (CAST(t AS INTEGER) * 1000) + ").append(p.midnightMs())
                    .append(" AS tmsec, action, actor, target, world, x, y, z, material, amount, item_hash, text_id, meta FROM ")
                    .append(p.table()).append(" WHERE 1=1 ");
            if (f.playerId != null && f.playerId >= 0) sql.append(" AND actor = ").append(f.playerId);
            if (f.action != null) sql.append(" AND action = ").append(f.action.id());
            if (f.materialId != null && f.materialId >= 0) sql.append(" AND material = ").append(f.materialId);
            if (f.worldId != null && f.worldId >= 0) sql.append(" AND world = ").append(f.worldId);
            if (f.hasLocationFilter()) {
                int r = f.radius;
                sql.append(" AND x BETWEEN ").append(f.x - r).append(" AND ").append(f.x + r);
                sql.append(" AND z BETWEEN ").append(f.z - r).append(" AND ").append(f.z + r);
                if (f.y != null) sql.append(" AND y BETWEEN ").append(f.y - r).append(" AND ").append(f.y + r);
            }
        }
        sql.append(" ORDER BY tmsec DESC LIMIT ").append(f.limit)
                .append(" OFFSET ").append((f.page - 1) * f.limit);

        List<Row> out = new ArrayList<>();
        try (Connection c = plugin.db().reader();
             PreparedStatement ps = c.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Action a = Action.of(rs.getInt(2));
                if (a == null) continue;
                out.add(new Row(rs.getLong(1), a,
                        rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        rs.getInt(6), rs.getInt(7), rs.getInt(8),
                        rs.getInt(9), rs.getInt(10),
                        rs.getBytes(11),
                        rs.getInt(12), rs.getInt(13)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Lookup failed: " + e.getMessage());
        }
        return out;
    }

    /** Lookup at a specific block position (for /inspect). */
    public List<Row> blockHistory(int worldId, int x, int y, int z, int limit) {
        LookupFilter f = new LookupFilter();
        f.worldId = worldId; f.x = x; f.y = y; f.z = z; f.radius = 0;
        f.limit = limit;
        f.sinceMs = System.currentTimeMillis() - 7L * 86_400_000L;
        return run(f);
    }

    private List<PartitionKey> partitionsBetween(long since, long until) {
        LocalDate a = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(since), PartitionKey.ZONE);
        LocalDate b = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(until), PartitionKey.ZONE);
        List<PartitionKey> existing = new ArrayList<>();
        var ensured = plugin.partitions().ensuredTables();
        for (LocalDate d = a; !d.isAfter(b); d = d.plusDays(1)) {
            PartitionKey k = new PartitionKey(d);
            if (ensured.contains(k.table())) existing.add(k);
        }
        Collections.reverse(existing);
        return existing;
    }
}
