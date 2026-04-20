package fr.smp.core.data;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final SMPCore plugin;
    private final Database db;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerData get(Player p) {
        return cache.get(p.getUniqueId());
    }

    public PlayerData loadOrCreate(UUID uuid, String name) {
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            existing.setName(name);
            return existing;
        }
        PlayerData data = loadFromDb(uuid);
        if (data == null) {
            data = new PlayerData(uuid, name);
            insertNew(data);
        } else {
            data.setName(name);
        }
        cache.put(uuid, data);
        return data;
    }

    public void unload(UUID uuid) {
        PlayerData d = cache.remove(uuid);
        if (d != null) save(d);
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) save(d);
    }

    public Iterable<PlayerData> onlineData() {
        return cache.values();
    }

    private PlayerData loadFromDb(UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, money, shards, kills, deaths, playtime_sec, first_join, last_seen, team_id, scoreboard, shards_last_mc_min, daily_kills, daily_kills_date, survival_joined, last_world, last_x, last_y, last_z, last_yaw, last_pitch FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PlayerData d = new PlayerData(uuid, rs.getString(1));
                d.setMoney(rs.getDouble(2));
                d.setShards(rs.getLong(3));
                d.setKills(rs.getInt(4));
                d.setDeaths(rs.getInt(5));
                d.setPlaytimeSec(rs.getLong(6));
                d.setFirstJoin(rs.getLong(7));
                d.setLastSeen(rs.getLong(8));
                d.setTeamId(rs.getString(9));
                d.setScoreboardEnabled(rs.getInt(10) != 0);
                d.setShardsLastMcMin(rs.getLong(11));
                d.setDailyKills(rs.getInt(12));
                d.setDailyKillsDate(rs.getString(13));
                d.setSurvivalJoined(rs.getInt(14) != 0);
                String lw = rs.getString(15);
                if (lw != null) {
                    double lx = rs.getDouble(16);
                    double ly = rs.getDouble(17);
                    double lz = rs.getDouble(18);
                    float lyaw = rs.getFloat(19);
                    float lpitch = rs.getFloat(20);
                    d.setLastLocation(lw, lx, ly, lz, lyaw, lpitch);
                }
                return d;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load player " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    private void insertNew(PlayerData d) {
        long now = System.currentTimeMillis() / 1000L;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO players(uuid, name, money, shards, kills, deaths, playtime_sec, first_join, last_seen, team_id, scoreboard, shards_last_mc_min, daily_kills, daily_kills_date) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, d.uuid().toString());
            ps.setString(2, d.name());
            ps.setDouble(3, d.money());
            ps.setLong(4, d.shards());
            ps.setInt(5, d.kills());
            ps.setInt(6, d.deaths());
            ps.setLong(7, d.playtimeSec());
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.setString(10, d.teamId());
            ps.setInt(11, d.scoreboardEnabled() ? 1 : 0);
            ps.setLong(12, d.shardsLastMcMin());
            ps.setInt(13, d.dailyKills());
            ps.setString(14, d.dailyKillsDate());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to insert player: " + e.getMessage());
        }
    }

    public void save(PlayerData d) {
        long now = System.currentTimeMillis() / 1000L;
        d.setLastSeen(now);

        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE players SET name=?, money=?, shards=?, kills=?, deaths=?, playtime_sec=?, last_seen=?, team_id=?, scoreboard=?, shards_last_mc_min=?, daily_kills=?, daily_kills_date=?, survival_joined=?, last_world=?, last_x=?, last_y=?, last_z=?, last_yaw=?, last_pitch=? WHERE uuid=?")) {
            ps.setString(1, d.name());
            ps.setDouble(2, d.money());
            ps.setLong(3, d.shards());
            ps.setInt(4, d.kills());
            ps.setInt(5, d.deaths());
            ps.setLong(6, d.playtimeSec());
            ps.setLong(7, now);
            ps.setString(8, d.teamId());
            ps.setInt(9, d.scoreboardEnabled() ? 1 : 0);
            ps.setLong(10, d.shardsLastMcMin());
            ps.setInt(11, d.dailyKills());
            ps.setString(12, d.dailyKillsDate());
            ps.setInt(13, d.survivalJoined() ? 1 : 0);
            if (d.hasLastLocation()) {
                ps.setString(14, d.lastWorld());
                ps.setDouble(15, d.lastX());
                ps.setDouble(16, d.lastY());
                ps.setDouble(17, d.lastZ());
                ps.setFloat(18, d.lastYaw());
                ps.setFloat(19, d.lastPitch());
            } else {
                ps.setNull(14, java.sql.Types.VARCHAR);
                ps.setNull(15, java.sql.Types.REAL);
                ps.setNull(16, java.sql.Types.REAL);
                ps.setNull(17, java.sql.Types.REAL);
                ps.setNull(18, java.sql.Types.REAL);
                ps.setNull(19, java.sql.Types.REAL);
            }
            ps.setString(20, d.uuid().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save player: " + e.getMessage());
        }
    }

    public UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
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

    public PlayerData loadOffline(UUID uuid) {
        PlayerData d = cache.get(uuid);
        if (d != null) return d;
        return loadFromDb(uuid);
    }

    public record MoneyEntry(UUID uuid, String name, double money) {}

    public List<MoneyEntry> topMoney(int limit) {
        // Merge any in-memory values first to avoid stale top.
        saveAll();
        List<MoneyEntry> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, money FROM players ORDER BY money DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MoneyEntry(UUID.fromString(rs.getString(1)),
                            rs.getString(2), rs.getDouble(3)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("topMoney: " + e.getMessage());
        }
        return out;
    }
}
