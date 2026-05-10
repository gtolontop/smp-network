package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import fr.smp.ptr.util.Energy;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public class GrappinItem extends BasePtrItem {

    public GrappinItem(PtrShowcase plugin) { super(plugin); }

    @Override public String id() { return "grappin"; }
    @Override public String displayName() { return "Grappin"; }
    @Override protected Material material() { return Material.FISHING_ROD; }
    @Override protected int customModelData() { return 100003; }
    @Override protected List<String> lore() {
        return List.of(
                "Vise un bloc et clique droit pour t'y projeter.",
                "Pas de pull joueur (PvP safe).",
                "Consomme 1 charge."
        );
    }

    @Override
    public boolean onRightClickAir(Player p, ItemStack stack) {
        return doGrapple(p, stack);
    }

    @Override
    public boolean onRightClickBlock(Player p, ItemStack stack, Block block) {
        return doGrapple(p, stack);
    }

    private boolean doGrapple(Player p, ItemStack stack) {
        var target = p.getTargetBlockExact(48);
        if (target == null) return false;
        if (!Energy.consume(stack, plugin, 1)) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("Plus d'energie.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        Vector dir = target.getLocation().add(0.5, 0.8, 0.5).toVector().subtract(p.getLocation().toVector());
        double dist = dir.length();
        Vector pull = dir.normalize().multiply(Math.min(2.0, 0.4 + dist * 0.05));
        pull.setY(Math.max(pull.getY(), 0.4));
        p.setVelocity(pull);
        p.setFallDistance(0f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1.6f);
        return true;
    }
}
