package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;

public class NetherToggleManager implements Listener {

    private final SMPCore plugin;

    public NetherToggleManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("world.nether-enabled", true);
    }

    public void setEnabled(boolean enabled) {
        plugin.getConfig().set("world.nether-enabled", enabled);
        plugin.saveConfig();
        if (enabled) broadcastNetherOpen();
    }

    private void broadcastNetherOpen() {
        MiniMessage mm = MiniMessage.miniMessage();
        Title title = Title.title(
                mm.deserialize("<gradient:#ef4444:#f97316><bold>LE NETHER EST OUVERT !</bold></gradient>"),
                mm.deserialize("<gray>Les portails Nether sont accessibles.</gray>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(1000))
        );
        Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
        Bukkit.broadcast(Msg.mm("<red><bold>✦</bold></red> <gold>Le Nether vient d'être ouvert !</gold>"));
    }

    private boolean isNether(World world) {
        return world != null && world.getEnvironment() == World.Environment.NETHER;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (enabled()) return;
        if (event.getTo() != null && isNether(event.getTo().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Msg.err("<red>Le Nether est désactivé.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (enabled()) return;
        if (event.getTo() != null && isNether(event.getTo().getWorld())
                && !event.getPlayer().hasPermission("smp.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Msg.err("<red>Le Nether est désactivé.</red>"));
        }
    }
}
