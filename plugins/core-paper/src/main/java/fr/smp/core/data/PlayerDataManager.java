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

    /**
     * Snapshot of real (production) values for fields that must be isolated on PTR.
     * When the server is PTR, we read the real values from the shared DB, stash them
     * here, then zero-out the in-memory PlayerData so the PTR player starts fresh.
     * On save, we swap the real values back in before writing to the shared DB,
     * ensuring PTR activity never pollutes production stats.
     * Playtime is explicitly NOT isolated — it syncs normally.
     */
    private record PtrSnapshot(double money, long shards, int kills, int deaths, int dailyKills, String dailyKillsDate) {}
    private final Map<UUID, PtrSnapshot> ptrSnapshots = new ConcurrentHashMap<>();

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
        // On PTR, isolate money/kills/deaths so players cannot exploit /give + /sell.
        // We snapshot the real DB values and give the player fresh zeros.
        if (plugin.isPtr()) {
            ptrSnapshots.put(uuid, new PtrSnapshot(
                    data.money(), data.shards(), data.kills(), data.deaths(),
                    data.dailyKills(), data.dailyKillsDate()));
            data.setMoney(0);
            data.setShards(0);
            data.setKills(0);
            data.setDeaths(0);
            data.setDailyKills(0);
            data.setDailyKillsDate(null);
        }
        cache.put(uuid, data);
        return data;
    }

    public void unload(UUID uuid) {
        PlayerData d = cache.remove(uuid);
        if (d != null) save(d);
        ptrSnapshots.remove(uuid);
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
                      "SELECT name, money, shards, kills, deaths, playtime_sec, first_join, last_seen, team_id, scoreboard, fullbright, shards_last_mc_min, daily_kills, daily_kills_date, survival_joined, last_world, last_x, last_y, last_z, last_yaw, last_pitch, nickname FROM players WHERE uuid=?")) {
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
                d.setFullbrightEnabled(rs.getInt(11) != 0);
                d.setShardsLastMcMin(rs.getLong(12));
                d.setDailyKills(rs.getInt(13));
                d.setDailyKillsDate(rs.getString(14));
                d.setSurvivalJoined(rs.getInt(15) != 0);
                String lw = rs.getString(16);
                if (lw != null) {
                    double lx = rs.getDouble(17);
                    double ly = rs.getDouble(18);
                    double lz = rs.getDouble(19);
                    float lyaw = rs.getFloat(20);
                    float lpitch = rs.getFloat(21);
                    d.setLastLocation(lw, lx, ly, lz, lyaw, lpitch);
                }
                d.setNickname(rs.getString(22));
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
                      "INSERT INTO players(uuid, name, money, shards, kills, deaths, playtime_sec, first_join, last_seen, team_id, scoreboard, fullbright, shards_last_mc_min, daily_kills, daily_kills_date, nickname) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
            ps.setInt(12, d.fullbrightEnabled() ? 1 : 0);
            ps.setLong(13, d.shardsLastMcMin());
            ps.setInt(14, d.dailyKills());
            ps.setString(15, d.dailyKillsDate());
            ps.setString(16, d.nickname());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to insert player: " + e.getMessage());
        }
    }

    public void save(PlayerData d) {
        long now = System.currentTimeMillis() / 1000L;
        d.setLastSeen(now);

        // On PTR, restore real production values before writing to the shared DB
        // so that PTR activity (money from /sell, kills, deaths) never leaks.
        // Playtime is the only field that PTR is allowed to update.
        double writeMoney = d.money();
        long writeShards = d.shards();
        int writeKills = d.kills();
        int writeDeaths = d.deaths();
        int writeDailyKills = d.dailyKills();
        String writeDailyKillsDate = d.dailyKillsDate();
        long writeShardsLastMcMin = d.shardsLastMcMin();

        if (plugin.isPtr()) {
            PtrSnapshot snap = ptrSnapshots.get(d.uuid());
            if (snap != null) {
                writeMoney = snap.money();
                writeShards = snap.shards();
                writeKills = snap.kills();
                writeDeaths = snap.deaths();
                writeDailyKills = snap.dailyKills();
                writeDailyKillsDate = snap.dailyKillsDate();
                writeShardsLastMcMin = d.shardsLastMcMin(); // keep real shard timing
            }
        }

        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                      "UPDATE players SET name=?, money=?, shards=?, kills=?, deaths=?, playtime_sec=?, last_seen=?, team_id=?, scoreboard=?, fullbright=?, shards_last_mc_min=?, daily_kills=?, daily_kills_date=?, survival_joined=?, last_world=?, last_x=?, last_y=?, last_z=?, last_yaw=?, last_pitch=?, nickname=? WHERE uuid=?")) {
            ps.setString(1, d.name());
            ps.setDouble(2, writeMoney);
            ps.setLong(3, writeShards);
            ps.setInt(4, writeKills);
            ps.setInt(5, writeDeaths);
            ps.setLong(6, d.playtimeSec());  // playtime always syncs (not isolated)
            ps.setLong(7, now);
            ps.setString(8, d.teamId());
            ps.setInt(9, d.scoreboardEnabled() ? 1 : 0);
            ps.setInt(10, d.fullbrightEnabled() ? 1 : 0);
            ps.setLong(11, writeShardsLastMcMin);
            ps.setInt(12, writeDailyKills);
            ps.setString(13, writeDailyKillsDate);
            ps.setInt(14, d.survivalJoined() ? 1 : 0);
            if (d.hasLastLocation()) {
                ps.setString(15, d.lastWorld());
                ps.setDouble(16, d.lastX());
                ps.setDouble(17, d.lastY());
                ps.setDouble(18, d.lastZ());
                ps.setFloat(19, d.lastYaw());
                ps.setFloat(20, d.lastPitch());
            } else {
                ps.setNull(15, java.sql.Types.VARCHAR);
                ps.setNull(16, java.sql.Types.REAL);
                ps.setNull(17, java.sql.Types.REAL);
                ps.setNull(18, java.sql.Types.REAL);
                ps.setNull(19, java.sql.Types.REAL);
                ps.setNull(20, java.sql.Types.REAL);
            }
            ps.setString(21, d.nickname());
            ps.setString(22, d.uuid().toString());
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
