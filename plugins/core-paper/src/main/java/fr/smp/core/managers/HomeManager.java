package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    public record Home(int slot, String server, String world, double x, double y, double z, float yaw, float pitch) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final SMPCore plugin;
    private final Database db;

    public HomeManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public int maxSlots(Player p) {
        // Capped to 5 network-wide (single-row Homes GUI).
        int base = plugin.getConfig().getInt("homes.base-slots", 5);
        int max = plugin.getConfig().getInt("homes.max-slots", 5);
        for (int i = max; i >= 1; i--) {
            if (p.hasPermission("smp.homes." + i)) return Math.min(i, max);
        }
        return Math.min(base, max);
    }

    public Map<Integer, Home> list(UUID uuid) {
        Map<Integer, Home> map = new LinkedHashMap<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT slot, server, world, x, y, z, yaw, pitch FROM homes WHERE uuid=? ORDER BY slot")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt(1), readHome(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("homes.list: " + e.getMessage());
        }
        return map;
    }

    public Home get(UUID uuid, int slot) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT slot, server, world, x, y, z, yaw, pitch FROM homes WHERE uuid=? AND slot=?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return readHome(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("homes.get: " + e.getMessage());
        }
        return null;
    }

    public void set(UUID uuid, int slot, Location l) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO homes(uuid, slot, server, world, x, y, z, yaw, pitch, created_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.setString(3, plugin.getServerType());
            ps.setString(4, l.getWorld().getName());
            ps.setDouble(5, l.getX());
            ps.setDouble(6, l.getY());
            ps.setDouble(7, l.getZ());
            ps.setFloat(8, l.getYaw());
            ps.setFloat(9, l.getPitch());
            ps.setLong(10, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("homes.set: " + e.getMessage());
        }
    }

    public void delete(UUID uuid, int slot) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM homes WHERE uuid=? AND slot=?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("homes.del: " + e.getMessage());
        }
    }

    public int firstFreeSlot(UUID uuid, int max) {
        Map<Integer, Home> list = list(uuid);
        for (int i = 1; i <= max; i++) {
            if (!list.containsKey(i)) return i;
        }
        return -1;
    }

    private Home readHome(ResultSet rs) throws SQLException {
        String server = rs.getString(2);
        if (server == null) server = "survival"; // legacy rows default to survival
        return new Home(rs.getInt(1), server, rs.getString(3),
                rs.getDouble(4), rs.getDouble(5), rs.getDouble(6),
                rs.getFloat(7), rs.getFloat(8));
    }
}
