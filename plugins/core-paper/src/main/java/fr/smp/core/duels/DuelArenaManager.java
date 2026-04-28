package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owner of all DuelArena templates: load, persist, lookup. Geometry-only —
 * runtime match orchestration lives in DuelMatchManager.
 *
 * Spawn pairs and the optional kit are stored as inline YAML so we don't have
 * to drag two more tables around for what is at most a few hundred bytes per
 * arena.
 */
public class DuelArenaManager {

    private final SMPCore plugin;
    private final Database db;
    private final Map<String, DuelArena> arenas = new ConcurrentHashMap<>();

    public DuelArenaManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() {
        arenas.clear();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server, world, center_x, center_y, center_z, radius, " +
                             "floor_y, dig_depth, ceiling, spawns_yaml, kit_yaml, enabled, created_at " +
                             "FROM duel_arenas")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    List<DuelArena.SpawnPair> spawns = parseSpawns(rs.getString(11));
                    DuelArena a = new DuelArena(
                            name, rs.getString(2), rs.getString(3),
                            rs.getDouble(4), rs.getDouble(5), rs.getDouble(6),
                            rs.getDouble(7),
                            rs.getInt(8), rs.getInt(9), rs.getInt(10),
                            spawns, rs.getString(12),
                            rs.getInt(13) == 1, rs.getLong(14));
                    arenas.put(name.toLowerCase(Locale.ROOT), a);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_arenas.load: " + e.getMessage());
        }
        plugin.getLogger().info("Duel arenas loaded: " + arenas.size());
    }

    public Collection<DuelArena> all() { return arenas.values(); }

    public DuelArena get(String name) {
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return arenas.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public DuelArena create(String name, Location center) {
        if (exists(name)) return null;
        World w = center.getWorld();
        if (w == null) return null;
        DuelArena a = new DuelArena(
                name.toLowerCase(Locale.ROOT),
                plugin.getServerType(),
                w.getName(),
                center.getX(), center.getY(), center.getZ(),
                30.0,                              // default radius
                center.getBlockY(),                // floor = where admin stands
                5, 30,                             // dig 5 down, ceiling 30 up
                new ArrayList<>(), null,
                true, System.currentTimeMillis() / 1000L);
        arenas.put(a.name(), a);
        persist(a);
        return a;
    }

    public boolean delete(String name) {
        DuelArena a = arenas.remove(name.toLowerCase(Locale.ROOT));
        if (a == null) return false;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM duel_arenas WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, a.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_arenas.delete: " + e.getMessage());
        }
        return true;
    }

    public void save(DuelArena a) { persist(a); }

    private void persist(DuelArena a) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO duel_arenas(name, server, world, " +
                             "center_x, center_y, center_z, radius, " +
                             "floor_y, dig_depth, ceiling, spawns_yaml, kit_yaml, enabled, created_at) " +
                             "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, a.name());
            ps.setString(2, a.server());
            ps.setString(3, a.world());
            ps.setDouble(4, a.centerX());
            ps.setDouble(5, a.centerY());
            ps.setDouble(6, a.centerZ());
            ps.setDouble(7, a.radius());
            ps.setInt(8, a.floorY());
            ps.setInt(9, a.digDepth());
            ps.setInt(10, a.ceiling());
            ps.setString(11, serializeSpawns(a.spawns()));
            ps.setString(12, a.kitYaml());
            ps.setInt(13, a.enabled() ? 1 : 0);
            ps.setLong(14, a.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("duel_arenas.persist: " + e.getMessage());
        }
    }

    /**
     * Lookup the arena whose template world matches the given Bukkit world.
     * Used by listeners to gate block break/place inside the template world,
     * and by match-world bounds checks (since match worlds copy the same name).
     */
    public DuelArena byWorldName(String worldName) {
        if (worldName == null) return null;
        for (DuelArena a : arenas.values()) {
            if (a.world().equalsIgnoreCase(worldName)) return a;
        }
        return null;
    }

    /* ------------------------------ Spawn helpers ------------------------------ */

    public boolean addSpawn(DuelArena a, Location loc) {
        // Spawns alternate A/B; we store them as a flat list and pair them up.
        // Add to the most recent open pair (last has only A) or open a new pair.
        List<DuelArena.SpawnPair> list = a.spawns();
        if (!list.isEmpty()) {
            DuelArena.SpawnPair last = list.get(list.size() - 1);
            if (last.b() == null) {
                list.set(list.size() - 1, new DuelArena.SpawnPair(last.a(), loc.clone()));
                persist(a);
                return true;
            }
        }
        list.add(new DuelArena.SpawnPair(loc.clone(), null));
        persist(a);
        return true;
    }

    public void clearSpawns(DuelArena a) {
        a.spawns().clear();
        persist(a);
    }

    /** Pairs that have BOTH spawns set are usable for a match; half-pairs are skipped. */
    public List<DuelArena.SpawnPair> usableSpawns(DuelArena a) {
        List<DuelArena.SpawnPair> out = new ArrayList<>();
        for (DuelArena.SpawnPair p : a.spawns()) {
            if (p.a() != null && p.b() != null) out.add(p);
        }
        return out;
    }

    /* --------------------------- YAML serialization ---------------------------- */

    private static String serializeSpawns(List<DuelArena.SpawnPair> spawns) {
        YamlConfiguration y = new YamlConfiguration();
        for (int i = 0; i < spawns.size(); i++) {
            DuelArena.SpawnPair p = spawns.get(i);
            String base = "p" + i + ".";
            if (p.a() != null) writeLoc(y, base + "a", p.a());
            if (p.b() != null) writeLoc(y, base + "b", p.b());
        }
        return y.saveToString();
    }

    private static List<DuelArena.SpawnPair> parseSpawns(String yaml) {
        List<DuelArena.SpawnPair> out = new ArrayList<>();
        if (yaml == null || yaml.isBlank()) return out;
        YamlConfiguration y = new YamlConfiguration();
        try { y.loadFromString(yaml); } catch (Exception e) { return out; }
        int i = 0;
        while (y.contains("p" + i)) {
            Location a = readLoc(y, "p" + i + ".a");
            Location b = readLoc(y, "p" + i + ".b");
            out.add(new DuelArena.SpawnPair(a, b));
            i++;
        }
        return out;
    }

    private static void writeLoc(YamlConfiguration y, String path, Location l) {
        y.set(path + ".w", l.getWorld() != null ? l.getWorld().getName() : "");
        y.set(path + ".x", l.getX());
        y.set(path + ".y", l.getY());
        y.set(path + ".z", l.getZ());
        y.set(path + ".yaw", l.getYaw());
        y.set(path + ".pitch", l.getPitch());
    }

    private static Location readLoc(YamlConfiguration y, String path) {
        if (!y.contains(path)) return null;
        String wn = y.getString(path + ".w", "");
        World w = wn.isEmpty() ? null : Bukkit.getWorld(wn);
        // World may be unloaded right now (template world for an arena that
        // hasn't been touched yet) — keep the coords; resolveSpawn() will retry
        // once the world is loaded by the match runtime.
        return new Location(w,
                y.getDouble(path + ".x"),
                y.getDouble(path + ".y"),
                y.getDouble(path + ".z"),
                (float) y.getDouble(path + ".yaw"),
                (float) y.getDouble(path + ".pitch"));
    }
}
