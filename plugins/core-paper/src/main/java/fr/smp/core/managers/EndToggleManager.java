package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;

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
        if (v) broadcastEndOpen();
    }

    private void broadcastEndOpen() {
        MiniMessage mm = MiniMessage.miniMessage();
        Title title = Title.title(
                mm.deserialize("<gradient:gold:yellow><bold>L'END EST OUVERT !</bold></gradient>"),
                mm.deserialize("<gray>Utilisez <yellow>/end</yellow> pour vous y rendre !</gray>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(1000))
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
        Bukkit.broadcast(Msg.mm("<gold><bold>✦</bold></gold> <yellow>L'End vient d'être ouvert !</yellow> <gray>(<yellow>/end</yellow>)</gray>"));
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEyeInFrame(PlayerInteractEvent event) {
        if (enabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.END_PORTAL_FRAME) return;
        if (event.getPlayer().hasPermission("smp.admin")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Msg.err("<red>L'End est désactivé.</red>"));
    }
}
