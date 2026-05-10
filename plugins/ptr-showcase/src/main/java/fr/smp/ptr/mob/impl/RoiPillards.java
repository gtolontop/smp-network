package fr.smp.ptr.mob.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.mob.BasePtrMob;
import fr.smp.ptr.mob.Telegraph;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class RoiPillards extends BasePtrMob {
    public RoiPillards(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "roi_pillards"; }
    @Override public String displayName() { return "Roi des Pillards"; }
    @Override protected EntityType baseEntity() { return EntityType.PILLAGER; }
    @Override protected Material modelMaterial() { return Material.GOLDEN_HELMET; }
    @Override protected int customModelData() { return 300002; }
    @Override protected double maxHealth() { return 200; }
    @Override protected double damage() { return 10; }
    @Override protected float modelScale() { return 1.6f; }
    @Override protected Color glowColor() { return Color.YELLOW; }
    @Override protected BarColor barColor() { return BarColor.YELLOW; }

    @Override
    public Entity spawn(Location at, Player by) {
        Entity e = super.spawn(at, by);
        if (e instanceof LivingEntity le) {
            // Charge attack every 7s: laser line telegraph toward nearest player + slam at endpoint
            new BukkitRunnable() {
                @Override public void run() {
                    if (!le.isValid() || le.isDead()) { cancel(); return; }
                    Player target = nearest(le, 18);
                    if (target == null) return;
                    Location from = le.getEyeLocation();
                    Location to = target.getLocation().add(0, 1, 0);
                    Telegraph.laserBeam(plugin, from, to, 30);
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> Telegraph.groundSlam(plugin, target.getLocation(), 3.5, 20, 8.0),
                            32L);
                }
            }.runTaskTimer(plugin, 140L, 140L);
        }
        return e;
    }

    private static Player nearest(LivingEntity from, double maxDist) {
        Player best = null;
        double bestD = maxDist * maxDist;
        for (var p : from.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(from.getLocation());
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }
}
