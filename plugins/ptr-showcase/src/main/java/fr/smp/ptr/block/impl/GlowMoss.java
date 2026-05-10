package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.RealNoteBlock;
import org.bukkit.Material;

public class GlowMoss extends RealNoteBlock {
    public GlowMoss(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "glow_moss"; }
    @Override public String displayName() { return "Mousse Lumineuse"; }
    @Override protected int notePitch() { return 4; }
    @Override protected Material itemMaterial() { return Material.MOSS_BLOCK; }
    @Override protected int itemCustomModelData() { return 0; }
}
