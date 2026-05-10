package fr.smp.ptr.mob.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.mob.BasePtrMob;
import fr.smp.ptr.mob.Telegraph;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class GardienMine extends BasePtrMob {
    public GardienMine(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "gardien_mine"; }
    @Override public String displayName() { return "Gardien de la Mine"; }
    @Override protected EntityType baseEntity() { return EntityType.IRON_GOLEM; }
    @Override protected Material modelMaterial() { return Material.NETHERITE_HELMET; }
    @Override protected int customModelData() { return 300001; }
    @Override protected double maxHealth() { return 120; }
    @Override protected double damage() { return 8; }
    @Override protected float modelScale() { return 1.4f; }

    @Override
    public Entity spawn(Location at, Player by) {
        Entity e = super.spawn(at, by);
        if (e instanceof LivingEntity le) {
            // Slow heavy seism every 8s, 5-block radius, 5 dmg
            Telegraph.attachGroundSlamLoop(plugin, le, 5.0, 5.0, 160);
        }
        return e;
    }
}
