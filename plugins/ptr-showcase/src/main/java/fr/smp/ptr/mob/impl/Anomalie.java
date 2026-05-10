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

public class Anomalie extends BasePtrMob {
    public Anomalie(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "anomalie"; }
    @Override public String displayName() { return "L'Anomalie"; }
    @Override protected EntityType baseEntity() { return EntityType.WITHER_SKELETON; }
    @Override protected Material modelMaterial() { return Material.ENDER_EYE; }
    @Override protected int customModelData() { return 300003; }
    @Override protected double maxHealth() { return 350; }
    @Override protected double damage() { return 14; }
    @Override protected float modelScale() { return 2.0f; }
    @Override protected Color glowColor() { return Color.PURPLE; }
    @Override protected BarColor barColor() { return BarColor.PURPLE; }

    @Override
    public Entity spawn(Location at, Player by) {
        Entity e = super.spawn(at, by);
        if (e instanceof LivingEntity le) {
            // Ground slam every 5s, 4-block radius, 6 damage
            Telegraph.attachGroundSlamLoop(plugin, le, 4.0, 6.0, 100);
        }
        return e;
    }
}
