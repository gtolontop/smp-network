package fr.smp.core.sync;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SyncListener implements Listener {

    private final SMPCore plugin;
    private final SyncManager sync;

    public SyncListener(SMPCore plugin, SyncManager sync) {
        this.plugin = plugin;
        this.sync = sync;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        // Delay by 1 tick so Paper's vanilla playerdata loading is fully
        // complete before we overwrite the inventory with our synced state.
        // Without this delay the vanilla .dat file can overwrite our sync
        // data right after we apply it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                sync.load(event.getPlayer());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sync.save(event.getPlayer());
    }
}
