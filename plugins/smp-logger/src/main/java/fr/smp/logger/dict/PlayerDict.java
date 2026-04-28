package fr.smp.logger.dict;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.db.Database;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches UUID → compact int id (and name → uuid for /lookup player:foo).
 * Players are inserted lazily on first encounter. The dict survives across days
 * and partition purges so historical lookups still resolve names.
 */
public class PlayerDict {

    public record Entry(int id, UUID uuid, String name) {}

    private final SMPLogger plugin;
    private final Database db;
    private final ConcurrentHashMap<UUID, Entry> byUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Entry> byNameLower = new ConcurrentHashMap<>();

    public PlayerDict(SMPLogger plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() {
        try (Connection c = db.reader();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, uuid, last_name FROM dict_players")) {
            while (rs.next()) {
                int id = rs.getInt(1);
                UUID u = bytesToUuid(rs.getBytes(2));
                String name = rs.getString(3);
                Entry e = new Entry(id, u, name);
                byUuid.put(u, e);
                byNameLower.put(name.toLowerCase(java.util.Locale.ROOT), e);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDict load failed: " + e.getMessage());
        }
        plugin.getLogger().info("PlayerDict loaded " + byUuid.size() + " entries");
    }

    /** Hot path. Returns the int id, inserting on first encounter. */
    public int idOf(UUID uuid, String name) {
        Entry e = byUuid.get(uuid);
        if (e != null) {
            // Refresh name lazily if changed.
            if (!e.name.equals(name)) refreshName(e, name);
            return e.id;
        }
        return insert(uuid, name);
    }

    /** Lookup-only — returns 0 if unknown. */
    public int idOfName(String name) {
        Entry e = byNameLower.get(name.toLowerCase(java.util.Locale.ROOT));
        return e == null ? 0 : e.id;
    }

    public Entry byId(int id) {
        for (Entry e : byUuid.values()) if (e.id == id) return e;
        return null;
    }

    public Entry byName(String name) {
        return byNameLower.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public Entry byUuid(UUID u) {
        return byUuid.get(u);
    }

    private synchronized int insert(UUID uuid, String name) {
        Entry existing = byUuid.get(uuid);
        if (existing != null) return existing.id;
        long now = System.currentTimeMillis();
        byte[] uuidBytes = uuidToBytes(uuid);
        try (Connection c = db.writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO dict_players(uuid, last_name, first_seen, last_seen) VALUES (?, ?, ?, ?)")) {
            ps.setBytes(1, uuidBytes);
            ps.setString(2, name);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();

            try (PreparedStatement select = c.prepareStatement(
                    "SELECT id, last_name FROM dict_players WHERE uuid = ?")) {
                select.setBytes(1, uuidBytes);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        String currentName = rs.getString(2);
                        if (!name.equals(currentName)) {
                            try (PreparedStatement update = c.prepareStatement(
                                    "UPDATE dict_players SET last_name=?, last_seen=? WHERE id=?")) {
                                update.setString(1, name);
                                update.setLong(2, now);
                                update.setInt(3, id);
                                update.executeUpdate();
                            }
                            currentName = name;
                        }
                        Entry e = new Entry(id, uuid, currentName);
                        byUuid.put(uuid, e);
                        byNameLower.put(currentName.toLowerCase(java.util.Locale.ROOT), e);
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDict insert failed: " + e.getMessage());
        }
        return 0;
    }

    private void refreshName(Entry old, String newName) {
        Entry fresh = new Entry(old.id, old.uuid, newName);
        byUuid.put(old.uuid, fresh);
        byNameLower.remove(old.name.toLowerCase(java.util.Locale.ROOT));
        byNameLower.put(newName.toLowerCase(java.util.Locale.ROOT), fresh);
        try (Connection c = db.writer();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE dict_players SET last_name=?, last_seen=? WHERE id=?")) {
            ps.setString(1, newName);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, old.id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static byte[] uuidToBytes(UUID u) {
        ByteBuffer b = ByteBuffer.allocate(16);
        b.putLong(u.getMostSignificantBits());
        b.putLong(u.getLeastSignificantBits());
        return b.array();
    }

    private static UUID bytesToUuid(byte[] bs) {
        ByteBuffer b = ByteBuffer.wrap(bs);
        return new UUID(b.getLong(), b.getLong());
    }
}
