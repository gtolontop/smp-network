package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.npc.Npc;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-player netty handler that snoops ServerboundInteractPacket so we can
 * detect right-clicks on a fake-player NPC. Bukkit's PlayerInteractAtEntity
 * doesn't fire for packet-only NPCs, so we have to read the wire ourselves.
 *
 * We match the click target to the registered NPC list by entity-id, and if
 * the NPC's display name carries the duel marker (case-insensitive contains
 * "duel"), we open the duel GUI on the next main-thread tick.
 */
public final class DuelNpcClickInjector implements Listener {

    public static final String HANDLER_NAME = "smp_duel_npc_click";

    private final SMPCore plugin;
    private final ConcurrentMap<UUID, Channel> channels = new ConcurrentHashMap<>();

    /** Cached reflective access to ServerboundInteractPacket's entityId. */
    private static volatile Field interactEntityIdField;

    public DuelNpcClickInjector(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player p : plugin.getServer().getOnlinePlayers()) inject(p);
    }

    public void stop() {
        for (Player p : plugin.getServer().getOnlinePlayers()) uninject(p);
        channels.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { inject(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { uninject(e.getPlayer()); }

    private void inject(Player player) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            Channel channel = sp.connection.connection.channel;
            if (channel == null || !channel.isActive()) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) return;
            ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    try {
                        handleIncoming(player, msg);
                    } catch (Throwable ignored) {
                        // Never break the netty pipeline because of duel logic.
                    }
                    super.channelRead(ctx, msg);
                }
            };
            channel.eventLoop().submit(() -> {
                if (channel.pipeline().get(HANDLER_NAME) == null
                        && channel.pipeline().get("packet_handler") != null) {
                    channel.pipeline().addBefore("packet_handler", HANDLER_NAME, handler);
                }
            });
            channels.put(player.getUniqueId(), channel);
        } catch (Throwable t) {
            plugin.getLogger().warning("duel-npc-click inject failed for " + player.getName() + ": " + t.getMessage());
        }
    }

    private void uninject(Player player) {
        Channel channel = channels.remove(player.getUniqueId());
        if (channel == null) return;
        try {
            channel.eventLoop().submit(() -> {
                if (channel.pipeline().get(HANDLER_NAME) != null) channel.pipeline().remove(HANDLER_NAME);
            });
        } catch (Throwable ignored) {}
    }

    private void handleIncoming(Player player, Object msg) {
        if (msg == null) return;
        String cn = msg.getClass().getSimpleName();
        if (!"ServerboundInteractPacket".equals(cn)) return;
        // Read the entity id reflectively. The field is the first int in the
        // packet; we cache it after the first lookup since the class is final.
        Field f = interactEntityIdField;
        if (f == null) {
            for (Field cand : msg.getClass().getDeclaredFields()) {
                if (cand.getType() == int.class) {
                    cand.setAccessible(true);
                    f = cand;
                    interactEntityIdField = cand;
                    break;
                }
            }
            if (f == null) return;
        }
        int eid;
        try { eid = f.getInt(msg); } catch (IllegalAccessException ex) { return; }

        if (plugin.npcs() == null) return;
        Npc target = null;
        for (Npc n : plugin.npcs().all().values()) {
            if (n.entityId() == eid) { target = n; break; }
        }
        if (target == null) return;
        if (!isDuelNpc(target)) return;

        // Schedule on main thread — Bukkit GUI APIs aren't thread-safe.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            DuelQueueGUI.open(plugin, player);
        });
    }

    /** A simple naming convention so admins control which NPC opens the GUI. */
    private boolean isDuelNpc(Npc n) {
        String dn = n.displayName();
        if (dn == null) return false;
        String stripped = dn.replaceAll("<[^>]*>", "").toLowerCase();
        return stripped.contains("duel");
    }
}
