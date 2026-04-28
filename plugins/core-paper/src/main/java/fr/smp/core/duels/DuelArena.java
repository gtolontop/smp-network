package fr.smp.core.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Template definition of a duel arena. The arena lives in a Bukkit world (the
 * "template" world) that is replicated on disk for each running match — block
 * protection only matters in the match worlds, not here.
 *
 * Geometry is a cylinder: (centerX, centerZ) + radius. Vertical bounds run from
 * floorY - digDepth (inclusive) up to floorY + ceiling (exclusive). Anything
 * outside that volume is read-only for non-admin duelers.
 *
 * Spawn pairs come ordered: pair index = match slot. With N pairs an arena
 * can host N concurrent matches once the world is replicated.
 */
public final class DuelArena {

    public record SpawnPair(Location a, Location b) {}

    private final String name;
    private final String server;
    private String world;
    private double centerX, centerY, centerZ;
    private double radius;
    private int floorY;
    private int digDepth;
    private int ceiling;
    private final List<SpawnPair> spawns;
    private String kitYaml;
    private boolean enabled;
    private final long createdAt;

    public DuelArena(String name, String server, String world,
                     double centerX, double centerY, double centerZ,
                     double radius, int floorY, int digDepth, int ceiling,
                     List<SpawnPair> spawns, String kitYaml,
                     boolean enabled, long createdAt) {
        this.name = name;
        this.server = server;
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
        this.floorY = floorY;
        this.digDepth = digDepth;
        this.ceiling = ceiling;
        this.spawns = new ArrayList<>(spawns);
        this.kitYaml = kitYaml;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public String name() { return name; }
    public String server() { return server; }
    public String world() { return world; }
    public void setWorld(String world) { this.world = world; }
    public double centerX() { return centerX; }
    public double centerY() { return centerY; }
    public double centerZ() { return centerZ; }
    public void setCenter(double x, double y, double z) { this.centerX = x; this.centerY = y; this.centerZ = z; }
    public double radius() { return radius; }
    public void setRadius(double r) { this.radius = r; }
    public int floorY() { return floorY; }
    public void setFloorY(int y) { this.floorY = y; }
    public int digDepth() { return digDepth; }
    public void setDigDepth(int d) { this.digDepth = d; }
    public int ceiling() { return ceiling; }
    public void setCeiling(int c) { this.ceiling = c; }
    public List<SpawnPair> spawns() { return spawns; }
    public String kitYaml() { return kitYaml; }
    public void setKitYaml(String k) { this.kitYaml = k; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public long createdAt() { return createdAt; }

    public Location center() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, centerX, centerY, centerZ);
    }

    /** Cylinder + vertical bounds containment check, in the template world's coords. */
    public boolean contains(double x, double y, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        if (dx * dx + dz * dz > radius * radius) return false;
        return y >= (floorY - digDepth) && y < (floorY + ceiling);
    }

    /** True if (x,z) is within the cylinder XZ footprint (any Y). */
    public boolean withinCylinderXZ(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }
}
