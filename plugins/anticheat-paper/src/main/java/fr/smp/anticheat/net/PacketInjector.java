package fr.smp.anticheat.net;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.containers.ContainerEspModule;
import fr.smp.anticheat.entity.EntityEspModule;
import fr.smp.anticheat.xray.XrayModule;
import io.netty.channel.Channel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PacketInjector implements Listener {

    public static final String HANDLER_NAME = "smp_anticheat_handler";

    private final AntiCheatPlugin plugin;
    private final XrayModule xray;
    private final ContainerEspModule containers;
    private final EntityEspModule entities;

    private final ConcurrentMap<UUID, Channel> channels = new ConcurrentHashMap<>();

    public PacketInjector(AntiCheatPlugin plugin,
                          XrayModule xray,
                          ContainerEspModule containers,
                          EntityEspModule entities) {
        this.plugin = plugin;
        this.xray = xray;
        this.containers = containers;
        this.entities = entities;
    }

    public void start() {
        // Inject for any already-online players (plugin reload scenario).
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            inject(p);
        }
    }

    public void shutdown() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            uninject(p);
        }
        channels.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        inject(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        uninject(p);
        clearPlayerState(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        clearPlayerState(e.getPlayer());
    }

    private void inject(Player player) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            Channel channel = sp.connection.connection.channel;
            if (channel == null || !channel.isActive()) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) return;

            OutboundFilter filter = new OutboundFilter(plugin, player, xray, containers, entities);
            channel.eventLoop().submit(() -> {
                if (channel.pipeline().get(HANDLER_NAME) == null && channel.pipeline().get("packet_handler") != null) {
                    channel.pipeline().addBefore("packet_handler", HANDLER_NAME, filter);
                }
            });
            channels.put(player.getUniqueId(), channel);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to inject packet filter for " + player.getName() + ": " + t.getMessage());
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

    private void clearPlayerState(Player player) {
        // Visibility watches are keyed only by player + packed block position. If we
        // keep them across a dimension switch, old-world chunk coords collide with the
        // new world and the xray reconciler wastes time on stale watches forever.
        UUID id = player.getUniqueId();
        if (plugin.entities() != null) plugin.entities().clearViewer(id);
        if (plugin.visibility() != null) plugin.visibility().clear(id);
        if (plugin.xray() != null) plugin.xray().clearPlayer(id);
    }

    /** Send a packet to the given player, bypassing this filter (used for re-sending corrected views). */
    public void sendBypass(Player player, Packet<?> packet) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            sp.connection.send(packet);
        } catch (Throwable t) {
            plugin.getLogger().fine("sendBypass failed for " + player.getName() + ": " + t.getMessage());
        }
    }
}
