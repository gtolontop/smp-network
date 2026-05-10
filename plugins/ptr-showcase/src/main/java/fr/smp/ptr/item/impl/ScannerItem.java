package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import fr.smp.ptr.util.Energy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

public class ScannerItem extends BasePtrItem {

    private static final Set<Material> ORES = Set.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.ANCIENT_DEBRIS, Material.NETHER_QUARTZ_ORE,
            Material.NETHER_GOLD_ORE, Material.AMETHYST_CLUSTER
    );

    public ScannerItem(PtrShowcase plugin) { super(plugin); }

    @Override public String id() { return "scanner"; }
    @Override public String displayName() { return "Scanner geologique"; }
    @Override protected Material material() { return Material.COMPASS; }
    @Override protected int customModelData() { return 100004; }
    @Override protected List<String> lore() {
        return List.of(
                "Compte les minerais dans un rayon de 10 blocs.",
                "Indication de densite, jamais de coords precises."
        );
    }

    @Override
    public boolean onRightClickAir(Player p, ItemStack stack) {
        return doScan(p, stack);
    }

    @Override
    public boolean onRightClickBlock(Player p, ItemStack stack, Block block) {
        return doScan(p, stack);
    }

    private boolean doScan(Player p, ItemStack stack) {
        if (!Energy.consume(stack, plugin, 1)) return true;
        int range = 10;
        int count = 0;
        var l = p.getLocation();
        for (int x = -range; x <= range; x++)
            for (int y = -range; y <= range; y++)
                for (int z = -range; z <= range; z++) {
                    if (ORES.contains(l.clone().add(x, y, z).getBlock().getType())) count++;
                }
        String label = count == 0 ? "vide" : count < 5 ? "faible" : count < 15 ? "moyenne" : count < 40 ? "forte" : "exceptionnelle";
        p.sendActionBar(Component.text("Densite: " + label + " (" + count + ")", NamedTextColor.AQUA));
        p.getWorld().playSound(l, Sound.BLOCK_BEACON_AMBIENT, 0.6f, count > 15 ? 1.6f : 0.9f);
        return true;
    }
}
