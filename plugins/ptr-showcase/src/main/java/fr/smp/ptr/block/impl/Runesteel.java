package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.RealNoteBlock;
import org.bukkit.Material;

public class Runesteel extends RealNoteBlock {
    public Runesteel(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "runesteel"; }
    @Override public String displayName() { return "Acier Runique"; }
    @Override protected int notePitch() { return 5; }
    @Override protected Material itemMaterial() { return Material.IRON_BLOCK; }
    @Override protected int itemCustomModelData() { return 0; }
}
