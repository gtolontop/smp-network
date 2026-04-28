package fr.smp.logger.dict;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.db.Database;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Material/EntityType/anything → small int id. Bulk-prepopulated with vanilla
 * materials at startup so the hot path is a pure cache hit, no DB roundtrip.
 */
public class MaterialDict {

    private final SMPLogger plugin;
    private final Database db;
    private final ConcurrentHashMap<String, Integer> nameToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> idToName = new ConcurrentHashMap<>();

    public MaterialDict(SMPLogger plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() {
        try (Connection c = db.reader();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name FROM dict_materials")) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String name = rs.getString(2);
                nameToId.put(name, id);
                idToName.put(id, name);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MaterialDict load failed: " + e.getMessage());
        }

        // Pre-warm with the entire vanilla Material registry. Single transaction.
        try (Connection c = db.writer()) {
            try {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO dict_materials(name) VALUES (?)")) {
                    for (Material m : Material.values()) {
                        if (nameToId.containsKey(m.name())) continue;
                        ps.setString(1, m.name());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MaterialDict prewarm failed: " + e.getMessage());
        }
        // Now reload IDs for the freshly inserted ones.
        try (Connection c = db.reader();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name FROM dict_materials")) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String name = rs.getString(2);
                nameToId.put(name, id);
                idToName.put(id, name);
            }
        } catch (SQLException ignored) {}
        plugin.getLogger().info("MaterialDict ready (" + nameToId.size() + " entries)");
    }

    public int idOf(String name) {
        if (name == null) return 0;
        Integer cached = nameToId.get(name);
        if (cached != null) return cached;
        return insert(name);
    }

    public int idOf(Material m) {
        return m == null ? 0 : idOf(m.name());
    }

    public String nameOf(int id) {
        return idToName.get(id);
    }

    private synchronized int insert(String name) {
        Integer cached = nameToId.get(name);
        if (cached != null) return cached;
        try (Connection c = db.writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO dict_materials(name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (PreparedStatement select = c.prepareStatement(
                    "SELECT id FROM dict_materials WHERE name = ?")) {
                select.setString(1, name);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        nameToId.put(name, id);
                        idToName.put(id, name);
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MaterialDict insert failed for " + name + ": " + e.getMessage());
        }
        return 0;
    }
}
