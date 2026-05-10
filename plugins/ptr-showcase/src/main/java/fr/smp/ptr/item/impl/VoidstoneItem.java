package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import org.bukkit.Material;

import java.util.List;

public class VoidstoneItem extends BasePtrItem {
    public VoidstoneItem(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "voidstone"; }
    @Override public String displayName() { return "Voidstone"; }
    @Override protected Material material() { return Material.AMETHYST_SHARD; }
    @Override protected int customModelData() { return 100005; }
    @Override protected List<String> lore() {
        return List.of(
                "Stocke matieres a l'interieur (a venir).",
                "Showcase visuel only."
        );
    }
}
