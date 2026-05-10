package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.RealNoteBlock;
import org.bukkit.Material;

public class PlasmaBlock extends RealNoteBlock {
    public PlasmaBlock(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "plasma_block"; }
    @Override public String displayName() { return "Bloc de Plasma"; }
    @Override protected int notePitch() { return 2; }
    @Override protected Material itemMaterial() { return Material.AMETHYST_BLOCK; }
    @Override protected int itemCustomModelData() { return 0; }
}
