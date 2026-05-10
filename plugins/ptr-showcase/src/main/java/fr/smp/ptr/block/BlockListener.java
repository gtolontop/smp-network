package fr.smp.ptr.block;

import fr.smp.ptr.PtrShowcase;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class BlockListener implements Listener {

    private final PtrShowcase plugin;

    public BlockListener(PtrShowcase plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction inter)) return;
        var key = plugin.key("ptr_block_id");
        if (!inter.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
        String id = inter.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        PtrBlock b = plugin.blocks().get(id);
        if (b != null) {
            b.onInteract(e.getPlayer(), inter.getLocation());
            e.setCancelled(true);
        }
    }
}
