package fr.smp.ptr.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

public interface PtrItem {
    String id();
    String displayName();
    ItemStack create();
    default boolean isInstance(ItemStack stack) { return ItemRegistry.idOf(stack) != null && id().equals(ItemRegistry.idOf(stack)); }
    default boolean onLeftClickBlock(Player p, ItemStack stack, org.bukkit.block.Block block) { return false; }
    default boolean onRightClickBlock(Player p, ItemStack stack, org.bukkit.block.Block block) { return false; }
    default boolean onRightClickAir(Player p, ItemStack stack) { return false; }
}
