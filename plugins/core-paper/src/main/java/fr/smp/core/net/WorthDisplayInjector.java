package fr.smp.core.net;

import fr.smp.core.SMPCore;
import io.netty.channel.Channel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Installs a {@link WorthOutboundHandler} on every player's Netty pipeline so
 * container/equipment packets are rewritten in-flight to include the worth
 * lore. The server-side ItemStacks are never modified.
 */
public final class WorthDisplayInjector implements Listener {

    public static final String HANDLER_NAME = "smp_worth_display";

    private final SMPCore plugin;
    private final ConcurrentMap<UUID, Channel> channels = new ConcurrentHashMap<>();

    public WorthDisplayInjector(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player p : plugin.getServer().getOnlinePlayers()) inject(p);
    }

    public void shutdown() {
        for (Player p : plugin.getServer().getOnlinePlayers()) uninject(p);
        channels.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        inject(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        uninject(e.getPlayer());
    }

    private void inject(Player player) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            Channel channel = sp.connection.connection.channel;
            if (channel == null || !channel.isActive()) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) return;

            WorthOutboundHandler handler = new WorthOutboundHandler(plugin, player);
            channel.eventLoop().submit(() -> {
                if (channel.pipeline().get(HANDLER_NAME) == null
                        && channel.pipeline().get("packet_handler") != null) {
                    channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                }
            });
            channels.put(player.getUniqueId(), channel);
        } catch (Throwable t) {
            plugin.getLogger().warning("worth-display inject failed for "
                    + player.getName() + ": " + t.getMessage());
        }
    }

    private void uninject(Player player) {
        Channel channel = channels.remove(player.getUniqueId());
        if (channel == null) return;
        try {
            channel.eventLoop().submit(() -> {
                if (channel.pipeline().get(HANDLER_NAME) != null) {
                    channel.pipeline().remove(HANDLER_NAME);
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
