package fr.smp.ptr.item.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.BasePtrItem;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MarteauBuildItem extends BasePtrItem {

    public MarteauBuildItem(PtrShowcase plugin) { super(plugin); }

    @Override public String id() { return "marteau_build"; }
    @Override public String displayName() { return "Marteau de build"; }
    @Override protected Material material() { return Material.WARPED_FUNGUS_ON_A_STICK; }
    @Override protected int customModelData() { return 100007; }
    @Override protected List<String> lore() {
        return List.of(
                "Pose 5 blocs en ligne devant toi.",
                "Bloc a placer = main offhand."
        );
    }

    @Override
    public boolean onRightClickBlock(Player p, ItemStack stack, Block block) {
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off == null || !off.getType().isBlock() || off.getType() == Material.AIR) return false;
        BlockFace face = p.getFacing();
        int placed = 0;
        Block cursor = block.getRelative(0, 1, 0);
        for (int i = 0; i < 5; i++) {
            if (cursor.getType().isAir()) {
                cursor.setType(off.getType());
                placed++;
            }
            cursor = cursor.getRelative(face);
        }
        if (placed > 0) off.setAmount(Math.max(0, off.getAmount() - placed));
        return true;
    }
}
