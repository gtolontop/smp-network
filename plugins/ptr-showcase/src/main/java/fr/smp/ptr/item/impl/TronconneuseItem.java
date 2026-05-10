package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import fr.smp.ptr.util.Energy;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TronconneuseItem extends BasePtrItem {

    private static final int CAP = 48;

    public TronconneuseItem(PtrShowcase plugin) { super(plugin); }

    @Override public String id() { return "tronconneuse"; }
    @Override public String displayName() { return "Tronconneuse"; }
    @Override protected Material material() { return Material.BLAZE_ROD; }
    @Override protected int customModelData() { return 100002; }
    @Override protected List<String> lore() {
        return List.of(
                "Coupe l'arbre entier (cap " + CAP + " logs).",
                "Sneak + clic gauche sur un log.",
                "Consomme 1 charge."
        );
    }

    @Override
    public boolean onLeftClickBlock(Player p, ItemStack stack, Block block) {
        if (!p.isSneaking()) return false;
        if (!Tag.LOGS.isTagged(block.getType())) return false;
        if (!Energy.consume(stack, plugin, 1)) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("Plus d'energie.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        Set<Block> visited = new HashSet<>();
        Deque<Block> stackBlocks = new ArrayDeque<>();
        stackBlocks.push(block);
        int felled = 0;
        while (!stackBlocks.isEmpty() && felled < CAP) {
            Block b = stackBlocks.pop();
            if (!visited.add(b)) continue;
            if (!Tag.LOGS.isTagged(b.getType())) continue;
            b.breakNaturally(stack);
            felled++;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, dy, dz);
                        if (!visited.contains(nb)) stackBlocks.push(nb);
                    }
        }
        p.getWorld().playSound(block.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 1.4f);
        return true;
    }
}
