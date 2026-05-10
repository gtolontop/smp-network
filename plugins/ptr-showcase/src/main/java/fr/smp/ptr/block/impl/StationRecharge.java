package fr.smp.ptr.block.impl;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.BaseDisplayBlock;
import fr.smp.ptr.item.ItemRegistry;
import fr.smp.ptr.util.Energy;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class StationRecharge extends BaseDisplayBlock {

    public StationRecharge(PtrShowcase plugin) { super(plugin); }
    @Override public String id() { return "station_recharge"; }
    @Override public String displayName() { return "Station de recharge"; }
    @Override protected Material baseDisplayMaterial() { return Material.RESPAWN_ANCHOR; }
    @Override protected int customModelData() { return 200002; }

    @Override
    public void onInteract(Player by, Location at) {
        ItemStack hand = by.getInventory().getItemInMainHand();
        if (ItemRegistry.idOf(hand) == null) {
            by.sendActionBar(net.kyori.adventure.text.Component.text("Tiens un item PTR pour le recharger.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        Energy.set(hand, plugin, 100);
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        by.sendActionBar(net.kyori.adventure.text.Component.text("Energie restauree.",
                net.kyori.adventure.text.format.NamedTextColor.AQUA));
    }
}
