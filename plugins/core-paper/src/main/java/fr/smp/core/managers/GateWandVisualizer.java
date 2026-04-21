package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.listeners.GateListener;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Per-player particle overlay for admins holding the Gate Setup wand:
 *   - cyan wireframe around each nearby gate's cuboid
 *   - red circle on the ground at the detection radius
 *   - gold beam rising at the gate center to highlight the action zone
 *   - white wireframe on the currently-selected pos1/pos2 cuboid
 * Particles are sent to the holding player only.
 */
public class GateWandVisualizer {

    private static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(0x3AD0FF), 1.0f);
    private static final Particle.DustOptions RED  = new Particle.DustOptions(Color.fromRGB(0xFF3A3A), 1.0f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xFFC83A), 1.1f);
    private static final double VISIBLE_RANGE = 50.0;

    private final SMPCore plugin;
    private BukkitTask task;
    private int tickCounter;

    public GateWandVisualizer(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        tickCounter++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("smp.admin")) continue;
            if (!GateListener.isWand(p.getInventory().getItemInMainHand())
                    && !GateListener.isWand(p.getInventory().getItemInOffHand())) continue;
            drawSelection(p);
            drawNearbyGates(p);
        }
    }

    private void drawSelection(Player p) {
        GateManager.Selection sel = plugin.gates().getSelection(p);
        if (sel == null) return;
        World w = Bukkit.getWorld(sel.world());
        if (w == null || !w.equals(p.getWorld())) return;
        drawCuboid(p,
                sel.minX(), sel.minY(), sel.minZ(),
                sel.maxX() + 1, sel.maxY() + 1, sel.maxZ() + 1,
                null, 0.5); // END_ROD, tight spacing
    }

    private void drawNearbyGates(Player p) {
        double r2 = VISIBLE_RANGE * VISIBLE_RANGE;
        for (GateManager.Gate g : plugin.gates().all()) {
            if (!g.world.equals(p.getWorld().getName())) continue;
            Location c = g.center();
            if (c == null) continue;
            if (p.getLocation().distanceSquared(c) > r2) continue;

            int minX = Math.min(g.x1, g.x2);
            int maxX = Math.max(g.x1, g.x2) + 1;
            int minY = g.minY();
            int maxY = g.maxY() + 1;
            int minZ = Math.min(g.z1, g.z2);
            int maxZ = Math.max(g.z1, g.z2) + 1;

            // Gate block cuboid (cyan wireframe)
            drawCuboid(p, minX, minY, minZ, maxX, maxY, maxZ, CYAN, 1.0);
            // Detection radius (red circle around center at ground Y)
            drawCircle(p, c.clone().subtract(0, (maxY - minY) / 2.0 - 0.5, 0), g.radius, RED, 28);
            // Rising beam of gold sparks — animated column so the admin sees "action zone"
            drawRisingBeam(p, c, g.radius);
        }
    }

    private void drawCuboid(Player p,
                            double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            Particle.DustOptions dust, double step) {
        double[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2},
                {x1, y2, z1, x2, y2, z1}, {x1, y2, z2, x2, y2, z2},
                {x1, y1, z1, x1, y1, z2}, {x2, y1, z1, x2, y1, z2},
                {x1, y2, z1, x1, y2, z2}, {x2, y2, z1, x2, y2, z2},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1},
                {x1, y1, z2, x1, y2, z2}, {x2, y1, z2, x2, y2, z2},
        };
        for (double[] e : edges) {
            drawLine(p, e[0], e[1], e[2], e[3], e[4], e[5], step, dust);
        }
    }

    private void drawLine(Player p, double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          double step, Particle.DustOptions dust) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Math.max(1, Math.min(80, (int) Math.round(len / step)));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double x = x1 + dx * t, y = y1 + dy * t, z = z1 + dz * t;
            if (dust != null) {
                p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
            } else {
                p.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void drawCircle(Player p, Location center, double radius, Particle.DustOptions dust, int segments) {
        double cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int i = 0; i < segments; i++) {
            double a = 2.0 * Math.PI * i / segments;
            double x = cx + radius * Math.cos(a);
            double z = cz + radius * Math.sin(a);
            p.spawnParticle(Particle.DUST, x, cy, z, 1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * Animated rising column of gold sparks at the gate center — offsets step up
     * with tickCounter so it reads as motion without being noisy.
     */
    private void drawRisingBeam(Player p, Location center, double radius) {
        double cx = center.getX(), cz = center.getZ();
        // Beam height ≈ gate height + radius for a visible pillar.
        double top = center.getY() + Math.min(8.0, radius);
        double bottom = center.getY() - Math.min(4.0, radius / 2.0);
        double phase = (tickCounter % 10) / 10.0;
        for (double y = bottom + phase; y <= top; y += 1.0) {
            p.spawnParticle(Particle.DUST, cx, y, cz, 1, 0.05, 0, 0.05, 0, GOLD);
        }
    }
}
