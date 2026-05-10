package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import fr.smp.ptr.util.Energy;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ForeuseItem extends BasePtrItem {

    public ForeuseItem(PtrShowcase plugin) { super(plugin); }

    @Override public String id() { return "foreuse"; }
    @Override public String displayName() { return "Foreuse 3x3"; }
    @Override protected Material material() { return Material.NETHERITE_PICKAXE; }
    @Override protected int customModelData() { return 100001; }
    @Override protected List<String> lore() {
        return List.of(
                "Mine 3x3 face au joueur.",
                "Sneak + clic droit sur un bloc.",
                "Consomme 1 charge par utilisation."
        );
    }

    @Override
    public boolean onRightClickBlock(Player p, ItemStack stack, Block block) {
        if (!p.isSneaking()) return false;
        if (!Energy.consume(stack, plugin, 1)) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("Plus d'energie.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        BlockFace face = p.getFacing();
        // Build 3x3 plane perpendicular to facing
        int[][] offsets;
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            offsets = new int[][]{
                    {-1,0,-1},{0,0,-1},{1,0,-1},
                    {-1,0, 0},{0,0, 0},{1,0, 0},
                    {-1,0, 1},{0,0, 1},{1,0, 1}};
        } else if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
            offsets = new int[][]{
                    {-1,-1,0},{0,-1,0},{1,-1,0},
                    {-1, 0,0},{0, 0,0},{1, 0,0},
                    {-1, 1,0},{0, 1,0},{1, 1,0}};
        } else {
            offsets = new int[][]{
                    {0,-1,-1},{0,-1,0},{0,-1,1},
                    {0, 0,-1},{0, 0,0},{0, 0,1},
                    {0, 1,-1},{0, 1,0},{0, 1,1}};
        }
        for (int[] o : offsets) {
            Block b = block.getRelative(o[0], o[1], o[2]);
            if (!isMineable(b.getType())) continue;
            if (b.getType().getHardness() < 0) continue;
            // Drop directly into inventory (overflow drops on ground at player feet)
            var drops = b.getDrops(stack);
            b.setType(Material.AIR);
            for (ItemStack drop : drops) {
                var leftover = p.getInventory().addItem(drop);
                for (ItemStack rem : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
            }
        }
        p.getWorld().playSound(block.getLocation(), Sound.BLOCK_NETHERITE_BLOCK_HIT, 1f, 0.7f);
        return true;
    }

    private boolean isMineable(Material m) {
        return m.isSolid() && m != Material.BEDROCK && m != Material.OBSIDIAN && m.getHardness() < 50;
    }
}
