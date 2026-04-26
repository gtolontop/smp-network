package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.entity.Phantom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class PhantomToggleManager implements Listener {

    private final SMPCore plugin;

    public PhantomToggleManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("world.phantoms-enabled", true);
    }

    public void setEnabled(boolean v) {
        plugin.getConfig().set("world.phantoms-enabled", v);
        plugin.saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhantomSpawn(CreatureSpawnEvent event) {
        if (enabled()) return;
        if (event.getEntity() instanceof Phantom) {
            event.setCancelled(true);
        }
    }
}
