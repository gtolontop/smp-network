package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.RealNoteBlock;
import org.bukkit.Material;

public class Darkstone extends RealNoteBlock {
    public Darkstone(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "darkstone"; }
    @Override public String displayName() { return "Pierre Sombre"; }
    @Override protected int notePitch() { return 3; }
    @Override protected Material itemMaterial() { return Material.BLACKSTONE; }
    @Override protected int itemCustomModelData() { return 0; }
}
