package fr.smp.core.sync;

import fr.smp.core.SMPCore;
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
        sync.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sync.save(event.getPlayer());
    }
}
