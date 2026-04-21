package fr.smp.core.holograms;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HologramManager {

    private static final String LINE_SEP = "␞";

    private final SMPCore plugin;
    private final Database db;
    private final Map<String, Hologram> holograms = new HashMap<>();

    public HologramManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, this::loadAll);
    }

    public void stop() {
        for (Hologram h : holograms.values()) h.despawn();
        holograms.clear();
    }

    public Map<String, Hologram> all() { return holograms; }
    public Hologram byName(String name) { return holograms.get(name.toLowerCase()); }

    private void loadAll() {
        holograms.clear();
        try (Connection c = db.get();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id, name, world, x, y, z, lines FROM holograms "
                             + "WHERE server = '" + plugin.getServerType() + "'")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String name = rs.getString("name");
                World w = Bukkit.getWorld(rs.getString("world"));
                if (w == null) continue;
                Location loc = new Location(w,
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                List<String> lines = Arrays.asList(rs.getString("lines").split(LINE_SEP, -1));
                Hologram h = new Hologram(id, name, loc, lines);
                h.spawn();
                holograms.put(name.toLowerCase(), h);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Load holograms: " + e.getMessage());
        }
    }

    public Hologram create(String name, Location loc) {
        if (holograms.containsKey(name.toLowerCase())) return null;
        List<String> lines = Hologram.defaultLines();
        long id;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO holograms (server, name, world, x, y, z, lines) VALUES (?,?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, plugin.getServerType());
            ps.setString(2, name);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setString(7, String.join(LINE_SEP, lines));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return null;
                id = keys.getLong(1);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Create hologram: " + e.getMessage());
            return null;
        }
        Hologram h = new Hologram(id, name, loc, lines);
        h.spawn();
        holograms.put(name.toLowerCase(), h);
        return h;
    }

    public boolean remove(Hologram h) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
            ps.setLong(1, h.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Remove hologram: " + e.getMessage());
            return false;
        }
        h.despawn();
        holograms.remove(h.name().toLowerCase());
        return true;
    }

    public void setLines(Hologram h, List<String> lines) {
        h.setLines(lines);
        persistLines(h);
    }

    public void addLine(Hologram h, String line) {
        List<String> l = new ArrayList<>(h.lines());
        l.add(line);
        setLines(h, l);
    }

    public void insertLine(Hologram h, int index, String line) {
        List<String> l = new ArrayList<>(h.lines());
        index = Math.max(0, Math.min(index, l.size()));
        l.add(index, line);
        setLines(h, l);
    }

    public boolean removeLine(Hologram h, int index) {
        List<String> l = new ArrayList<>(h.lines());
        if (index < 0 || index >= l.size()) return false;
        l.remove(index);
        setLines(h, l);
        return true;
    }

    public boolean editLine(Hologram h, int index, String newLine) {
        List<String> l = new ArrayList<>(h.lines());
        if (index < 0 || index >= l.size()) return false;
        l.set(index, newLine);
        setLines(h, l);
        return true;
    }

    public void move(Hologram h, Location loc) {
        h.setLocation(loc);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE holograms SET world=?, x=?, y=?, z=? WHERE id=?")) {
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setLong(5, h.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Move hologram: " + e.getMessage());
        }
    }

    private void persistLines(Hologram h) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE holograms SET lines=? WHERE id=?")) {
            ps.setString(1, String.join(LINE_SEP, h.lines()));
            ps.setLong(2, h.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Persist hologram lines: " + e.getMessage());
        }
    }
}
