package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import org.bukkit.Material;

import java.util.List;

public class TotemAlarmeItem extends BasePtrItem {
    public TotemAlarmeItem(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "totem_alarme"; }
    @Override public String displayName() { return "Totem d'alarme"; }
    @Override protected Material material() { return Material.TOTEM_OF_UNDYING; }
    @Override protected int customModelData() { return 100008; }
    @Override protected List<String> lore() {
        return List.of(
                "Place ce totem dans ta base.",
                "(Showcase: trigger via cmd /ptr alarm.)"
        );
    }
}
