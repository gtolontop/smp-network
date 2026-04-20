package fr.smp.anticheat.net;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.containers.ContainerEspModule;
import fr.smp.anticheat.entity.EntityEspModule;
import fr.smp.anticheat.xray.XrayModule;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.UUID;

/**
 * Netty handler installed per-player to inspect and rewrite outbound game packets,
 * AND to inspect inbound interaction packets for ghost-block prevention.
 *
 * Outbound: chunk/BE/block-update/add-entity packets get filtered through the modules.
 * Inbound: when the player tries to break or right-click a position that is currently
 *          masked for them, we send a real-block + BE update FIRST so the client and
 *          server agree on the block before the action is processed.
 *
 * The Player reference is captured at construction time (the handler lives on the
 * player's own channel) so we avoid a per-packet Bukkit.getPlayer(UUID) lookup — that
 * call scans the online-player list and used to dominate this handler's CPU cost under
 * load. {@link Player#isOnline()} is O(1) and sufficient to guard against quit races.
 */
public final class OutboundFilter extends ChannelDuplexHandler {

    private final AntiCheatPlugin plugin;
    private final UUID playerId;
    private final Player player;
    private final XrayModule xray;
    private final ContainerEspModule containers;
    private final EntityEspModule entities;

    public OutboundFilter(AntiCheatPlugin plugin,
                          Player player,
                          XrayModule xray,
                          ContainerEspModule containers,
                          EntityEspModule entities) {
        this.plugin = plugin;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.xray = xray;
        this.containers = containers;
        this.entities = entities;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!player.isOnline() || hasBypass(player) || !(msg instanceof Packet<?> raw)) {
            super.write(ctx, msg, promise);
            return;
        }

        Packet<?> out = raw;
        Player pl = this.player;
        try {
            if (out instanceof ClientboundLevelChunkWithLightPacket chunk) {
                if (xray.enabled()) out = xray.rewriteChunk(pl, chunk);
                if (out instanceof ClientboundLevelChunkWithLightPacket c2 && containers.enabled()) {
                    out = containers.rewriteChunk(pl, c2);
                }
            } else if (out instanceof ClientboundBlockEntityDataPacket bed) {
                if (containers.enabled()) {
                    out = containers.rewriteBlockEntity(pl, bed);
                }
            } else if (out instanceof ClientboundBlockUpdatePacket bu) {
                if (xray.enabled()) {
                    out = xray.rewriteBlockUpdate(pl, bu);
                }
            } else if (out instanceof ClientboundSectionBlocksUpdatePacket sbu) {
                if (xray.enabled()) {
                    out = xray.rewriteSectionUpdate(pl, sbu);
                }
            } else if (out instanceof ClientboundAddEntityPacket add) {
                if (entities.enabled() && entities.shouldDropAdd(pl, add)) {
                    return;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Filter error: " + t.getMessage());
            out = raw;
        }

        if (out == null) return;
        super.write(ctx, out, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Defensive: anti-ghost-block. Whenever the player's client targets a block
        // (mining or right-click), if that position is currently masked for them,
        // un-mask immediately so the action lands on the same block client-side and
        // server-side. Schedule the real-block send to the main thread (we are on
        // Netty thread here, and the send below ultimately enqueues a packet which
        // is fine, but we also touch BE state which must be main-thread).
        try {
            if (xray.enabled()) {
                if (msg instanceof ServerboundPlayerActionPacket act) {
                    BlockPos p = act.getPos();
                    int x = p.getX(), y = p.getY(), z = p.getZ();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) xray.revealOnInteract(player, x, y, z);
                    });
                } else if (msg instanceof ServerboundUseItemOnPacket use) {
                    BlockPos p = use.getHitResult().getBlockPos();
                    int x = p.getX(), y = p.getY(), z = p.getZ();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) xray.revealOnInteract(player, x, y, z);
                    });
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("inbound filter error: " + t.getMessage());
        }
        super.channelRead(ctx, msg);
    }

    private boolean hasBypass(Permissible p) {
        return plugin.bypass().isBypassed(p);
    }
}
