package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.BaseDisplayBlock;
import org.bukkit.Material;

public class TableauEvents extends BaseDisplayBlock {
    public TableauEvents(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "tableau_events"; }
    @Override public String displayName() { return "Tableau Events"; }
    @Override protected Material baseDisplayMaterial() { return Material.PAINTING; }
    @Override protected int customModelData() { return 200005; }
    @Override protected float scale() { return 2.0f; }
}
