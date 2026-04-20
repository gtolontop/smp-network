package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Waypoints = per-player "solo" markers + per-team "team" markers, stored in
 * shared SQLite so they are visible from every backend. No packet-level
 * rendering — the UI side uses actionbar + optional {@code /waypoints coords}
 * to push a compass/reminder.
 */
public class WaypointManager {

    public enum Kind { SOLO, TEAM }

    public record Waypoint(long id, Kind kind, String ownerId, String name,
                            String server, String world, double x, double y, double z,
                            float yaw, float pitch, String color, long createdAt) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final SMPCore plugin;
    private final Database db;

    public WaypointManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public List<Waypoint> list(Kind kind, String ownerId) {
        List<Waypoint> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, owner_type, owner_id, name, server, world, x, y, z, yaw, pitch, color, created_at " +
                     "FROM waypoints WHERE owner_type=? AND owner_id=? ORDER BY name")) {
            ps.setString(1, kind.name().toLowerCase());
            ps.setString(2, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("waypoints.list: " + e.getMessage());
        }
        return out;
    }

    public Waypoint get(Kind kind, String ownerId, String name) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, owner_type, owner_id, name, server, world, x, y, z, yaw, pitch, color, created_at " +
                     "FROM waypoints WHERE owner_type=? AND owner_id=? AND name=? COLLATE NOCASE")) {
            ps.setString(1, kind.name().toLowerCase());
            ps.setString(2, ownerId);
            ps.setString(3, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return read(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("waypoints.get: " + e.getMessage());
        }
        return null;
    }

    public boolean set(Kind kind, String ownerId, String name, Location l, String color) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO waypoints(owner_type, owner_id, name, server, world, x, y, z, yaw, pitch, color, created_at) " +
                     "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, kind.name().toLowerCase());
            ps.setString(2, ownerId);
            ps.setString(3, name);
            ps.setString(4, plugin.getServerType());
            ps.setString(5, l.getWorld().getName());
            ps.setDouble(6, l.getX());
            ps.setDouble(7, l.getY());
            ps.setDouble(8, l.getZ());
            ps.setFloat(9, l.getYaw());
            ps.setFloat(10, l.getPitch());
            ps.setString(11, color);
            ps.setLong(12, System.currentTimeMillis() / 1000L);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("waypoints.set: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(Kind kind, String ownerId, String name) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM waypoints WHERE owner_type=? AND owner_id=? AND name=? COLLATE NOCASE")) {
            ps.setString(1, kind.name().toLowerCase());
            ps.setString(2, ownerId);
            ps.setString(3, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("waypoints.del: " + e.getMessage());
            return false;
        }
    }

    private Waypoint read(ResultSet rs) throws SQLException {
        Kind kind = Kind.valueOf(rs.getString(2).toUpperCase());
        return new Waypoint(rs.getLong(1), kind, rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6),
                rs.getDouble(7), rs.getDouble(8), rs.getDouble(9),
                rs.getFloat(10), rs.getFloat(11),
                rs.getString(12), rs.getLong(13));
    }
}
