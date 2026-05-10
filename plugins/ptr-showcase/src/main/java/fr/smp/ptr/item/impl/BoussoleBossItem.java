package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class BoussoleBossItem extends BasePtrItem {

    public BoussoleBossItem(PtrShowcase plugin) { super(plugin); }

    @Override public String id() { return "boussole_boss"; }
    @Override public String displayName() { return "Boussole de boss"; }
    @Override protected Material material() { return Material.RECOVERY_COMPASS; }
    @Override protected int customModelData() { return 100006; }
    @Override protected List<String> lore() {
        return List.of(
                "Pointe vers le boss custom le plus proche.",
                "Active uniquement pendant un event boss."
        );
    }

    @Override
    public boolean onRightClickAir(Player p, ItemStack stack) {
        return locate(p);
    }

    @Override
    public boolean onRightClickBlock(Player p, ItemStack stack, org.bukkit.block.Block b) {
        return locate(p);
    }

    private boolean locate(Player p) {
        var key = plugin.key("ptr_mob_id");
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : p.getWorld().getEntities()) {
            if (!(e instanceof LivingEntity)) continue;
            if (!e.getPersistentDataContainer().has(key, PersistentDataType.STRING)) continue;
            double d = e.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; nearest = e; }
        }
        if (nearest == null) {
            p.sendActionBar(Component.text("Aucun boss actif.", NamedTextColor.GRAY));
            return true;
        }
        p.setCompassTarget(nearest.getLocation());
        p.sendActionBar(Component.text("Boss a " + (int) Math.sqrt(best) + " blocs.", NamedTextColor.GOLD));
        return true;
    }
}
