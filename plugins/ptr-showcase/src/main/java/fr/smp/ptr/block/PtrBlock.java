package fr.smp.ptr.block;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface PtrBlock {
    String id();
    String displayName();
    /** Place this furniture at the given location (block-aligned). */
    void place(Location at, Player by);
    /** Create the item form of this block - placeable in survival. */
    ItemStack createItem();
    default void onInteract(Player by, Location at) {}
    default void cleanup() {}
}
