package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.RealNoteBlock;
import org.bukkit.Material;

public class BlueGrass extends RealNoteBlock {
    public BlueGrass(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "blue_grass"; }
    @Override public String displayName() { return "Herbe Bleue"; }
    @Override protected int notePitch() { return 1; }
    @Override protected Material itemMaterial() { return Material.GRASS_BLOCK; }
    @Override protected int itemCustomModelData() { return 0; }
}
