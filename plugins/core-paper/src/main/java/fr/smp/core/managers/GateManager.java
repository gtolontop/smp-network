package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Animated gates. Admins select a cuboid with a wooden-shovel wand, /gate create
 * saves the region (block data snapshot + detection radius) and the manager
 * opens/closes the gate layer-by-layer when a player enters/leaves the radius.
 */
public class GateManager {

    public enum State { CLOSED, OPENING, OPEN, CLOSING }

    public static final class StoredBlock {
        public final int x, y, z;
        public final BlockData data;
        StoredBlock(int x, int y, int z, BlockData data) {
            this.x = x; this.y = y; this.z = z; this.data = data;
        }
    }

    public static final class Gate {
        public final String name;
        public final String server;
        public final String world;
        public final int x1, y1, z1, x2, y2, z2;
        public double radius;
        public final List<StoredBlock> blocks;

        volatile State state = State.CLOSED;
        int animY;               // layer currently being processed
        long closingScheduledAt; // ms — grace period before CLOSING kicks in

        Gate(String name, String server, String world,
             int x1, int y1, int z1, int x2, int y2, int z2,
             double radius, List<StoredBlock> blocks) {
            this.name = name; this.server = server; this.world = world;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.radius = radius; this.blocks = blocks;
        }

        public int minY() { return Math.min(y1, y2); }
        public int maxY() { return Math.max(y1, y2); }
        public Location center() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w,
                    (x1 + x2) / 2.0 + 0.5,
                    (y1 + y2) / 2.0 + 0.5,
                    (z1 + z2) / 2.0 + 0.5);
        }
        public State getState() { return state; }
    }

    public record Selection(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public int minX() { return Math.min(x1, x2); }
        public int maxX() { return Math.max(x1, x2); }
        public int minY() { return Math.min(y1, y2); }
        public int maxY() { return Math.max(y1, y2); }
        public int minZ() { return Math.min(z1, z2); }
        public int maxZ() { return Math.max(z1, z2); }
        public int volume() {
            return (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
        }
    }

    private static final long CLOSE_DELAY_MS = 200L;
    private static final int  ANIM_PERIOD_TICKS = 2;     // one layer per 100ms
    private static final int  MAX_VOLUME = 20000;        // anti-footgun on /gate create

    private final SMPCore plugin;
    private final Database db;
    private final Map<String, Gate> gates = new ConcurrentHashMap<>();
    private final Map<UUID, int[]> pos1 = new HashMap<>();   // [x,y,z], world in sel1World
    private final Map<UUID, int[]> pos2 = new HashMap<>();
    private final Map<UUID, String> selWorld = new HashMap<>();

    private BukkitTask tickTask;
    private BukkitTask animTask;

    public GateManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        loadAll();
        // Detection loop: every 10 ticks (0.5s) check which gates should open/close.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDetection, 20L, 10L);
        // Animation loop: progresses active OPENING/CLOSING gates.
        animTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAnimation, 20L, ANIM_PERIOD_TICKS);
    }

    public void stop() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        if (animTask != null) { animTask.cancel(); animTask = null; }
        // Restore every gate's blocks so the world isn't left half-open on reload/shutdown.
        for (Gate g : gates.values()) {
            World w = Bukkit.getWorld(g.world);
            if (w == null) continue;
            for (StoredBlock sb : g.blocks) {
                Block b = w.getBlockAt(sb.x, sb.y, sb.z);
                if (b.getType() == Material.AIR) b.setBlockData(sb.data, false);
            }
        }
    }

    // --------- Selection (wand) ---------

    public void setPos1(Player p, Location l) {
        pos1.put(p.getUniqueId(), new int[]{l.getBlockX(), l.getBlockY(), l.getBlockZ()});
        selWorld.put(p.getUniqueId(), l.getWorld().getName());
    }

    public void setPos2(Player p, Location l) {
        pos2.put(p.getUniqueId(), new int[]{l.getBlockX(), l.getBlockY(), l.getBlockZ()});
        selWorld.put(p.getUniqueId(), l.getWorld().getName());
    }

    public Selection getSelection(Player p) {
        int[] a = pos1.get(p.getUniqueId());
        int[] b = pos2.get(p.getUniqueId());
        String w = selWorld.get(p.getUniqueId());
        if (a == null || b == null || w == null) return null;
        return new Selection(w, a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    public void clearSelection(Player p) {
        pos1.remove(p.getUniqueId());
        pos2.remove(p.getUniqueId());
        selWorld.remove(p.getUniqueId());
    }

    // --------- CRUD ---------

    public Collection<Gate> all() { return gates.values(); }

    public Gate get(String name) { return gates.get(name.toLowerCase(Locale.ROOT)); }

    public boolean exists(String name) { return gates.containsKey(name.toLowerCase(Locale.ROOT)); }

    /**
     * Create a gate from a selection. Captures every non-air block, stores it, and
     * commits to SQL. Returns the created gate, or null if the selection is invalid.
     */
    public Gate create(String name, Selection sel, double radius) {
        World w = Bukkit.getWorld(sel.world());
        if (w == null) return null;
        if (sel.volume() > MAX_VOLUME) return null;

        List<StoredBlock> captured = new ArrayList<>();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int y = sel.minY(); y <= sel.maxY(); y++) {
                for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.AIR) continue;
                    captured.add(new StoredBlock(x, y, z, b.getBlockData().clone()));
                }
            }
        }
        if (captured.isEmpty()) return null;

        Gate g = new Gate(
                name.toLowerCase(Locale.ROOT),
                plugin.getServerType(),
                sel.world(),
                sel.x1(), sel.y1(), sel.z1(),
                sel.x2(), sel.y2(), sel.z2(),
                radius,
                captured);
        gates.put(g.name, g);
        persist(g);
        return g;
    }

    public boolean delete(String name) {
        Gate g = gates.remove(name.toLowerCase(Locale.ROOT));
        if (g == null) return false;
        // Make sure the world contains the gate's blocks after deletion so the admin
        // doesn't end up with a ghost opening if it was mid-animation.
        World w = Bukkit.getWorld(g.world);
        if (w != null) {
            for (StoredBlock sb : g.blocks) {
                Block b = w.getBlockAt(sb.x, sb.y, sb.z);
                if (b.getType() == Material.AIR) b.setBlockData(sb.data, false);
            }
        }
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM gates WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, g.name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("gates.delete: " + e.getMessage());
        }
        return true;
    }

    /** Force an open/close cycle regardless of current proximity. */
    public void forceAnimate(Gate g, boolean open) {
        if (open) {
            if (g.state == State.CLOSED || g.state == State.CLOSING) startOpen(g);
        } else {
            if (g.state == State.OPEN || g.state == State.OPENING) startClose(g);
        }
    }

    public void setRadius(Gate g, double radius) {
        g.radius = radius;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE gates SET radius=? WHERE name=?")) {
            ps.setDouble(1, radius);
            ps.setString(2, g.name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("gates.radius: " + e.getMessage());
        }
    }

    /** Admins can break blocks inside a gate; non-admins can't. */
    public boolean isProtectedBlock(org.bukkit.block.Block b) {
        for (Gate g : gates.values()) {
            if (!g.world.equals(b.getWorld().getName())) continue;
            int bx = b.getX(), by = b.getY(), bz = b.getZ();
            if (bx < Math.min(g.x1, g.x2) || bx > Math.max(g.x1, g.x2)) continue;
            if (by < g.minY() || by > g.maxY()) continue;
            if (bz < Math.min(g.z1, g.z2) || bz > Math.max(g.z1, g.z2)) continue;
            for (StoredBlock sb : g.blocks) {
                if (sb.x == bx && sb.y == by && sb.z == bz) return true;
            }
        }
        return false;
    }

    // --------- Tick loops ---------

    private void tickDetection() {
        long now = System.currentTimeMillis();
        for (Gate g : gates.values()) {
            Location center = g.center();
            if (center == null) continue;
            boolean playerNear = hasPlayerNear(center, g.radius);

            switch (g.state) {
                case CLOSED -> {
                    if (playerNear) startOpen(g);
                }
                case OPEN -> {
                    if (!playerNear) {
                        if (g.closingScheduledAt == 0L) g.closingScheduledAt = now + CLOSE_DELAY_MS;
                        else if (now >= g.closingScheduledAt) startClose(g);
                    } else {
                        g.closingScheduledAt = 0L; // cancel pending close
                    }
                }
                case OPENING -> {
                    g.closingScheduledAt = 0L;
                }
                case CLOSING -> {
                    // If a player re-enters while closing, flip back to opening so they aren't trapped.
                    if (playerNear) {
                        g.state = State.OPENING;
                        g.animY = g.minY(); // restart from bottom (portcullis rising)
                    }
                }
            }
        }
    }

    private void tickAnimation() {
        for (Gate g : gates.values()) {
            if (g.state == State.OPENING) {
                animateOpenStep(g);
            } else if (g.state == State.CLOSING) {
                animateCloseStep(g);
            }
        }
    }

    private boolean hasPlayerNear(Location center, double radius) {
        World w = center.getWorld();
        if (w == null) return false;
        double r2 = radius * radius;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || !p.isOnline()) continue;
            if (p.getLocation().distanceSquared(center) <= r2) return true;
        }
        return false;
    }

    private void startOpen(Gate g) {
        g.state = State.OPENING;
        g.animY = g.minY();
        g.closingScheduledAt = 0L;
        Location c = g.center();
        if (c != null && c.getWorld() != null) {
            c.getWorld().playSound(c, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.7f);
        }
    }

    private void startClose(Gate g) {
        g.state = State.CLOSING;
        g.animY = g.maxY();
        g.closingScheduledAt = 0L;
        Location c = g.center();
        if (c != null && c.getWorld() != null) {
            c.getWorld().playSound(c, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.6f);
        }
    }

    private void animateOpenStep(Gate g) {
        World w = Bukkit.getWorld(g.world);
        if (w == null) return;
        int y = g.animY;
        boolean removedAny = false;
        for (StoredBlock sb : g.blocks) {
            if (sb.y != y) continue;
            Block b = w.getBlockAt(sb.x, sb.y, sb.z);
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false);
                w.spawnParticle(Particle.BLOCK, sb.x + 0.5, sb.y + 0.5, sb.z + 0.5,
                        6, 0.25, 0.25, 0.25, 0, sb.data);
                removedAny = true;
            }
        }
        if (removedAny) {
            Location c = g.center();
            if (c != null) w.playSound(c, Sound.BLOCK_CHAIN_STEP, 0.6f, 1.4f);
        }
        g.animY++;
        if (g.animY > g.maxY()) {
            g.state = State.OPEN;
            g.closingScheduledAt = 0L;
        }
    }

    private void animateCloseStep(Gate g) {
        World w = Bukkit.getWorld(g.world);
        if (w == null) return;
        int y = g.animY;
        boolean placedAny = false;
        for (StoredBlock sb : g.blocks) {
            if (sb.y != y) continue;
            Block b = w.getBlockAt(sb.x, sb.y, sb.z);
            if (b.getType() != Material.AIR) continue;
            // Don't suffocate a player standing in the column.
            if (isEntityBlocking(w, sb.x, sb.y, sb.z)) {
                // Flip back to opening so the gate doesn't stall on top of someone.
                g.state = State.OPENING;
                g.animY = g.minY();
                return;
            }
            b.setBlockData(sb.data, false);
            w.spawnParticle(Particle.BLOCK, sb.x + 0.5, sb.y + 0.5, sb.z + 0.5,
                    4, 0.2, 0.2, 0.2, 0, sb.data);
            placedAny = true;
        }
        if (placedAny) {
            Location c = g.center();
            if (c != null) w.playSound(c, Sound.BLOCK_CHAIN_STEP, 0.6f, 0.9f);
        }
        g.animY--;
        if (g.animY < g.minY()) {
            g.state = State.CLOSED;
        }
    }

    private boolean isEntityBlocking(World w, int x, int y, int z) {
        var box = new org.bukkit.util.BoundingBox(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        return !w.getNearbyEntities(box, e -> e instanceof Player).isEmpty();
    }

    // --------- Persistence ---------

    private void persist(Gate g) {
        byte[] blob = serialize(g.blocks);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO gates(name, server, world, x1,y1,z1, x2,y2,z2, radius, blocks, created_at) " +
                             "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, g.name);
            ps.setString(2, g.server);
            ps.setString(3, g.world);
            ps.setInt(4, g.x1); ps.setInt(5, g.y1); ps.setInt(6, g.z1);
            ps.setInt(7, g.x2); ps.setInt(8, g.y2); ps.setInt(9, g.z2);
            ps.setDouble(10, g.radius);
            ps.setBytes(11, blob);
            ps.setLong(12, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("gates.persist: " + e.getMessage());
        }
    }

    private void loadAll() {
        String server = plugin.getServerType();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, server, world, x1,y1,z1, x2,y2,z2, radius, blocks FROM gates WHERE server=?")) {
            ps.setString(1, server);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<StoredBlock> blocks = deserialize(rs.getBytes(11));
                    Gate g = new Gate(
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getInt(4), rs.getInt(5), rs.getInt(6),
                            rs.getInt(7), rs.getInt(8), rs.getInt(9),
                            rs.getDouble(10), blocks);
                    gates.put(g.name, g);
                }
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().warning("gates.load: " + e.getMessage());
        }
        plugin.getLogger().info("Gates loaded: " + gates.size());
    }

    private static byte[] serialize(List<StoredBlock> blocks) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(blocks.size());
            for (StoredBlock sb : blocks) {
                out.writeInt(sb.x);
                out.writeInt(sb.y);
                out.writeInt(sb.z);
                out.writeUTF(sb.data.getAsString(true));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private static List<StoredBlock> deserialize(byte[] data) throws IOException {
        List<StoredBlock> out = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String raw = in.readUTF();
                try {
                    out.add(new StoredBlock(x, y, z, Bukkit.createBlockData(raw)));
                } catch (IllegalArgumentException ex) {
                    // Ignore unknown/removed blockstates; the gate keeps working with a hole.
                }
            }
        }
        return out;
    }
}
