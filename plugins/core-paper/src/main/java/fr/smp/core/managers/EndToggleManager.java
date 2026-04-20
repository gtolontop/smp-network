package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Runtime-switchable End dimension access. Stored in config `world.end-enabled`.
 * When disabled, we cancel portal usage + teleport events that would land a
 * player in an End world.
 */
public class EndToggleManager implements Listener {

    private final SMPCore plugin;

    public EndToggleManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("world.end-enabled", true);
    }

    public void setEnabled(boolean v) {
        plugin.getConfig().set("world.end-enabled", v);
        plugin.saveConfig();
    }

    private boolean isEnd(World w) {
        return w != null && w.getEnvironment() == World.Environment.THE_END;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (enabled()) return;
        if (event.getTo() != null && isEnd(event.getTo().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Msg.err("<red>L'End est désactivé.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (enabled()) return;
        if (event.getTo() != null && isEnd(event.getTo().getWorld())
                && !event.getPlayer().hasPermission("smp.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Msg.err("<red>L'End est désactivé.</red>"));
        }
    }
}
