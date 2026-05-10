package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.BaseDisplayBlock;
import org.bukkit.Material;

public class ConsoleMarcheNoir extends BaseDisplayBlock {
    public ConsoleMarcheNoir(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "console_marche_noir"; }
    @Override public String displayName() { return "Console Marche Noir"; }
    @Override protected Material baseDisplayMaterial() { return Material.LECTERN; }
    @Override protected int customModelData() { return 200003; }
    @Override protected boolean glow() { return true; }
}
