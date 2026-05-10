package fr.smp.ptr.item;

import fr.smp.ptr.PtrShowcase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ItemListener implements Listener {

    private final PtrShowcase plugin;

    public ItemListener(PtrShowcase plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != null && !e.getHand().name().equals("HAND")) return;
        ItemStack stack = e.getItem();
        if (stack == null) return;
        PtrItem item = plugin.items().fromStack(stack);
        if (item == null) return;
        Action a = e.getAction();
        boolean handled = false;
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            handled = item.onRightClickBlock(e.getPlayer(), stack, e.getClickedBlock());
        } else if (a == Action.RIGHT_CLICK_AIR) {
            handled = item.onRightClickAir(e.getPlayer(), stack);
        } else if (a == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            handled = item.onLeftClickBlock(e.getPlayer(), stack, e.getClickedBlock());
        }
        if (handled) {
            e.setCancelled(true);
        }
    }
}
