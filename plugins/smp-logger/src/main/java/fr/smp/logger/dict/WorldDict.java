package fr.smp.logger.dict;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.db.Database;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

public class WorldDict {

    private final SMPLogger plugin;
    private final Database db;
    private final ConcurrentHashMap<String, Integer> nameToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> idToName = new ConcurrentHashMap<>();

    public WorldDict(SMPLogger plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() {
        try (Connection c = db.reader();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name FROM dict_worlds")) {
            while (rs.next()) {
                nameToId.put(rs.getString(2), rs.getInt(1));
                idToName.put(rs.getInt(1), rs.getString(2));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("WorldDict load failed: " + e.getMessage());
        }
        for (World w : plugin.getServer().getWorlds()) idOf(w.getName());
        plugin.getLogger().info("WorldDict ready (" + nameToId.size() + " entries)");
    }

    public int idOf(String name) {
        if (name == null) return 0;
        Integer cached = nameToId.get(name);
        if (cached != null) return cached;
        return insert(name);
    }

    public int idOf(World w) { return w == null ? 0 : idOf(w.getName()); }

    public String nameOf(int id) { return idToName.get(id); }

    private synchronized int insert(String name) {
        Integer cached = nameToId.get(name);
        if (cached != null) return cached;
        try (Connection c = db.writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO dict_worlds(name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (PreparedStatement select = c.prepareStatement(
                    "SELECT id FROM dict_worlds WHERE name = ?")) {
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
            plugin.getLogger().warning("WorldDict insert failed: " + e.getMessage());
        }
        return 0;
    }
}
