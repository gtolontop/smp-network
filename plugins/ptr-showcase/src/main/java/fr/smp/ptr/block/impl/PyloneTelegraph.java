package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.BaseDisplayBlock;
import fr.smp.ptr.mob.Telegraph;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class PyloneTelegraph extends BaseDisplayBlock {
    public PyloneTelegraph(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "pylone_telegraph"; }
    @Override public String displayName() { return "Pylone telegraph"; }
    @Override protected Material baseDisplayMaterial() { return Material.END_ROD; }
    @Override protected int customModelData() { return 200004; }
    @Override protected float scale() { return 1.5f; }
    @Override protected boolean glow() { return true; }

    @Override
    public void place(Location at, Player by) {
        super.place(at, by);
        at.getWorld().spawnParticle(Particle.PORTAL, at.clone().add(0.5, 1.5, 0.5), 80, 0.3, 0.8, 0.3, 0.4);
    }

    @Override
    public void onInteract(Player by, Location at) {
        // Demo: trigger a 3-block ground slam at the pylon (telegraph showcase)
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3, 0.05);
        Telegraph.groundSlam(plugin, at, 3.0, 25, 4.0);
    }
}
