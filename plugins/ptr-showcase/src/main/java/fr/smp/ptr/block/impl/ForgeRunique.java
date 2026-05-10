package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.BaseDisplayBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class ForgeRunique extends BaseDisplayBlock {
    public ForgeRunique(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "forge_runique"; }
    @Override public String displayName() { return "Forge Runique"; }
    @Override protected Material baseDisplayMaterial() { return Material.SMITHING_TABLE; }
    @Override protected int customModelData() { return 200001; }
    @Override protected boolean glow() { return true; }
    @Override
    public void place(Location at, Player by) {
        super.place(at, by);
        at.getWorld().spawnParticle(Particle.ENCHANT, at.clone().add(0.5, 1.0, 0.5), 30, 0.4, 0.4, 0.4, 1.0);
    }
}
