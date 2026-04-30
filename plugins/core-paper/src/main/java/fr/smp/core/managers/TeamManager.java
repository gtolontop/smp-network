package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.data.PlayerDataManager;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.storage.Database;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class TeamManager {

    public record Team(String id, String tag, String name, String color, String owner,
                       double balance, Location home, long createdAt) {}

    public enum Role { OWNER, OFFICER, MEMBER }

    public record Member(UUID uuid, String role, long joinedAt) {}

    private final SMPCore plugin;
    private final Database db;
    private final PlayerDataManager players;

    public TeamManager(SMPCore plugin, Database db, PlayerDataManager players) {
        this.plugin = plugin;
        this.db = db;
        this.players = players;
    }

    public double creationCost() {
        return plugin.getConfig().getDouble("teams.creation-cost", 5000);
    }

    public int maxMembers() {
        return Math.max(1, plugin.getConfig().getInt("teams.max-members", 3));
    }

    public int memberCount(String teamId) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM team_members WHERE team_id=?")) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("team.memberCount: " + e.getMessage());
            return 0;
        }
    }

    public boolean isFull(String teamId) {
        return memberCount(teamId) >= maxMembers();
    }

    public Team create(String id, String tag, String name, UUID owner) {
        long now = System.currentTimeMillis() / 1000L;
        try (Connection c = db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO teams(id, tag, name, color, owner, created_at) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, tag);
                ps.setString(3, name);
                ps.setString(4, "<white>");
                ps.setString(5, owner.toString());
                ps.setLong(6, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO team_members(team_id, uuid, role, joined_at) VALUES(?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, owner.toString());
                ps.setString(3, Role.OWNER.name());
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("team.create: " + e.getMessage());
            return null;
        }
        PlayerData pd = players.loadOffline(owner);
        if (pd != null) {
            pd.setTeamId(id);
            players.save(pd);
        }
        plugin.logs().log(LogCategory.TEAM, "create id=" + id + " tag=" + tag + " owner=" + owner);
        return get(id);
    }

    public Team get(String id) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, tag, name, color, owner, balance, home_world, home_x, home_y, home_z, home_yaw, home_pitch, created_at FROM teams WHERE id=? COLLATE NOCASE")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return readTeam(rs);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public Team byTag(String tag) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, tag, name, color, owner, balance, home_world, home_x, home_y, home_z, home_yaw, home_pitch, created_at FROM teams WHERE tag=? COLLATE NOCASE")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return readTeam(rs);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public List<Team> list() {
        List<Team> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, tag, name, color, owner, balance, home_world, home_x, home_y, home_z, home_yaw, home_pitch, created_at FROM teams ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readTeam(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("team.list: " + e.getMessage());
        }
        return out;
    }

    public List<Member> members(String teamId) {
        List<Member> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, role, joined_at FROM team_members WHERE team_id=? ORDER BY joined_at")) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Member(
                        UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("team.members: " + e.getMessage());
        }
        return out;
    }

    public void addMember(String teamId, UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO team_members(team_id, uuid, role, joined_at) VALUES(?,?,?,?)")) {
            ps.setString(1, teamId);
            ps.setString(2, uuid.toString());
            ps.setString(3, Role.MEMBER.name());
            ps.setLong(4, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("team.addMember: " + e.getMessage());
        }
        PlayerData pd = players.loadOffline(uuid);
        if (pd != null) { pd.setTeamId(teamId); players.save(pd); }
    }

    public void removeMember(String teamId, UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM team_members WHERE team_id=? AND uuid=?")) {
            ps.setString(1, teamId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("team.rmMember: " + e.getMessage());
        }
        PlayerData pd = players.loadOffline(uuid);
        if (pd != null) { pd.setTeamId(null); players.save(pd); }
    }

    public void disband(String teamId) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM teams WHERE id=?")) {
            ps.setString(1, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("team.disband: " + e.getMessage());
        }
    }

    public void setHome(String teamId, Location l) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE teams SET home_world=?, home_x=?, home_y=?, home_z=?, home_yaw=?, home_pitch=? WHERE id=?")) {
            if (l == null) {
                ps.setNull(1, java.sql.Types.VARCHAR);
                ps.setNull(2, java.sql.Types.DOUBLE);
                ps.setNull(3, java.sql.Types.DOUBLE);
                ps.setNull(4, java.sql.Types.DOUBLE);
                ps.setNull(5, java.sql.Types.FLOAT);
                ps.setNull(6, java.sql.Types.FLOAT);
            } else {
                ps.setString(1, l.getWorld().getName());
                ps.setDouble(2, l.getX());
                ps.setDouble(3, l.getY());
                ps.setDouble(4, l.getZ());
                ps.setFloat(5, l.getYaw());
                ps.setFloat(6, l.getPitch());
            }
            ps.setString(7, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("team.setHome: " + e.getMessage());
        }
    }

    public void setColor(String teamId, String color) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE teams SET color=? WHERE id=?")) {
            ps.setString(1, color);
            ps.setString(2, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("team.setColor: " + e.getMessage());
        }
    }

    public void setName(String teamId, String name) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE teams SET name=? WHERE id=?")) {
            ps.setString(1, name);
            ps.setString(2, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("team.setName: " + e.getMessage());
        }
        plugin.logs().log(LogCategory.TEAM, "rename id=" + teamId + " name=" + name);
    }

    private Team readTeam(ResultSet rs) throws SQLException {
        Location home = null;
        String hw = rs.getString(7);
        if (hw != null) {
            org.bukkit.World w = plugin.getServer().getWorld(hw);
            if (w != null) home = new Location(w,
                    rs.getDouble(8), rs.getDouble(9), rs.getDouble(10),
                    rs.getFloat(11), rs.getFloat(12));
        }
        return new Team(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getDouble(6),
                home, rs.getLong(13));
    }
}
