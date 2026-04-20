package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WarpManager {

    public record Warp(String name, String server, String world, double x, double y, double z,
                       float yaw, float pitch, Material icon, String description) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final SMPCore plugin;
    private final Database db;

    public WarpManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public List<Warp> list() {
        List<Warp> all = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server, world, x, y, z, yaw, pitch, material, description FROM warps ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) all.add(read(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("warps.list: " + e.getMessage());
        }
        return all;
    }

    public Warp get(String name) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server, world, x, y, z, yaw, pitch, material, description FROM warps WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return read(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("warps.get: " + e.getMessage());
        }
        return null;
    }

    public void set(String name, Location l, Material icon, String description, UUID by) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO warps(name, server, world, x, y, z, yaw, pitch, material, description, created_by, created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, plugin.getServerType());
            ps.setString(3, l.getWorld().getName());
            ps.setDouble(4, l.getX());
            ps.setDouble(5, l.getY());
            ps.setDouble(6, l.getZ());
            ps.setFloat(7, l.getYaw());
            ps.setFloat(8, l.getPitch());
            ps.setString(9, icon.name());
            ps.setString(10, description);
            ps.setString(11, by != null ? by.toString() : null);
            ps.setLong(12, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("warps.set: " + e.getMessage());
        }
    }

    public boolean delete(String name) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM warps WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("warps.del: " + e.getMessage());
            return false;
        }
    }

    private Warp read(ResultSet rs) throws SQLException {
        Material m;
        try { m = Material.valueOf(rs.getString(9)); } catch (Exception e) { m = Material.COMPASS; }
        String server = rs.getString(2);
        if (server == null) server = "survival";
        return new Warp(rs.getString(1), server, rs.getString(3),
                rs.getDouble(4), rs.getDouble(5), rs.getDouble(6),
                rs.getFloat(7), rs.getFloat(8), m, rs.getString(10));
    }
}
