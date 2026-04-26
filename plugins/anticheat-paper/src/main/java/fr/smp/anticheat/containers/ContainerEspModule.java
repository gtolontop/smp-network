package fr.smp.anticheat.containers;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.config.AntiCheatConfig;
import fr.smp.anticheat.visibility.VisibilityEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hides container block entities (chests, barrels, hoppers, etc.) from clients
 * who have no line of sight to the container's position.
 *
 * Two layers of protection:
 *   1. Strip BlockEntityInfo entries from the chunk packet's BE list via reflection
 *      → client gets no NBT (no items, no custom name, etc.) for hidden containers.
 *   2. Drop ClientboundBlockEntityDataPacket updates for the same containers.
 * When LoS is later established, we re-emit a BlockEntityData packet with the real
 * NBT + a BlockUpdate to ensure the block is correctly displayed.
 */
public final class ContainerEspModule {

    private final AntiCheatPlugin plugin;
    private final AntiCheatConfig cfg;
    private final VisibilityEngine visibility;

    private static final Field BLOCK_ENTITIES_FIELD;
    private static final Method BEI_TYPE_METHOD;
    private static final Method BEI_PACKED_XZ_METHOD;
    private static final Method BEI_Y_METHOD;
    private static final Field BEI_TYPE_FIELD;
    private static final Field BEI_PACKED_XZ_FIELD;
    private static final Field BEI_Y_FIELD;

    static {
        Field beField = null;
        for (Field f : ClientboundLevelChunkPacketData.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                beField = f;
                break;
            }
        }
        BLOCK_ENTITIES_FIELD = beField;

        Method typeMethod = null, packedMethod = null, yMethod = null;
        Field typeField = null, packedField = null, yField = null;
        for (Class<?> inner : ClientboundLevelChunkPacketData.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("BlockEntityInfo")) {
                try { typeMethod = inner.getDeclaredMethod("type"); typeMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { packedMethod = inner.getDeclaredMethod("packedXZ"); packedMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { yMethod = inner.getDeclaredMethod("y"); yMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { typeField = inner.getDeclaredField("type"); typeField.setAccessible(true); } catch (Throwable ignored) {}
                try { packedField = inner.getDeclaredField("packedXZ"); packedField.setAccessible(true); } catch (Throwable ignored) {}
                try { yField = inner.getDeclaredField("y"); yField.setAccessible(true); } catch (Throwable ignored) {}
                break;
            }
        }
        BEI_TYPE_METHOD = typeMethod;
        BEI_PACKED_XZ_METHOD = packedMethod;
        BEI_Y_METHOD = yMethod;
        BEI_TYPE_FIELD = typeField;
        BEI_PACKED_XZ_FIELD = packedField;
        BEI_Y_FIELD = yField;
    }

    public ContainerEspModule(AntiCheatPlugin plugin, AntiCheatConfig cfg, VisibilityEngine visibility) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.visibility = visibility;
    }

    public boolean enabled() {
        return cfg.containersEnabled();
    }

    /**
     * Strip hidden block-entity entries from the chunk packet's data list.
     * Returns the same packet, mutated. If no hidden entries are present, no modification occurs.
     */
    public ClientboundLevelChunkWithLightPacket rewriteChunk(Player player, ClientboundLevelChunkWithLightPacket pkt) {
        if (BLOCK_ENTITIES_FIELD == null || (BEI_TYPE_METHOD == null && BEI_TYPE_FIELD == null)) return pkt;
        try {
            ClientboundLevelChunkPacketData data = pkt.getChunkData();
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) BLOCK_ENTITIES_FIELD.get(data);
            if (list == null || list.isEmpty()) return pkt;

            Set<NamespacedKey> hidden = cfg.hiddenBlockEntityTypes();

            int chunkX = pkt.getX();
            int chunkZ = pkt.getZ();

            List<Object> kept = new ArrayList<>(list.size());
            List<int[]> toWatch = new ArrayList<>();

            for (Object info : list) {
                BlockEntityType<?> beType = readType(info);
                Identifier rl = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(beType);
                if (rl == null) {
                    kept.add(info);
                    continue;
                }
                NamespacedKey key = new NamespacedKey(rl.getNamespace(), rl.getPath());
                if (!hidden.contains(key)) {
                    kept.add(info);
                    continue;
                }
                int packedXZ = readPackedXZ(info);
                int y = readY(info);
                int worldX = (chunkX << 4) | (packedXZ >> 4);
                int worldZ = (chunkZ << 4) | (packedXZ & 0xF);
                toWatch.add(new int[]{worldX, y, worldZ});
                // intentionally not added to kept → stripped
            }

            if (toWatch.isEmpty()) return pkt;

            BLOCK_ENTITIES_FIELD.set(data, kept);

            // NOTE: we no longer register visibility watches here. XrayModule already
            // watches the same positions (when the container materials are in
            // xray.hidden-blocks) and its callback handles both block + BE reveal.
            // Registering here would overwrite the xray watch (one watch per key),
            // breaking the block-level mask for that position.
        } catch (Throwable t) {
            plugin.getLogger().warning("ContainerEspModule chunk rewrite failed: " + t.getMessage());
        }
        return pkt;
    }

    /**
     * Drop block-entity data updates for hidden containers while the block is still masked
     * for this player. We consult XrayModule.isMasked as the single source of truth instead
     * of the LoS cache — the cache flips periodically (raytrace at boundary) and we don't
     * want to drop a legitimate BE update just because the raytrace wobbled between the
     * reveal and the packet being processed by Netty.
     */
    public ClientboundBlockEntityDataPacket rewriteBlockEntity(Player player, ClientboundBlockEntityDataPacket pkt) {
        try {
            Identifier rl = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(pkt.getType());
            if (rl == null) return pkt;
            NamespacedKey key = new NamespacedKey(rl.getNamespace(), rl.getPath());
            if (!cfg.hiddenBlockEntityTypes().contains(key)) return pkt;
            BlockPos pos = pkt.getPos();
            var xray = plugin.xray();
            if (xray == null) return pkt;
            // Not masked ⇒ player is allowed to see this BE's NBT.
            if (!xray.isMasked(player, pos.getX(), pos.getY(), pos.getZ())) return pkt;
            return null; // masked ⇒ drop NBT update
        } catch (Throwable t) {
            return pkt;
        }
    }

    private void onVisibilityChange(Player player, int wx, int wy, int wz, boolean visible) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            ServerLevel level = (ServerLevel) sp.level();
            BlockPos pos = new BlockPos(wx, wy, wz);
            if (visible) {
                sp.connection.send(new ClientboundBlockUpdatePacket(level, pos));
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    var update = be.getUpdatePacket();
                    if (update != null) sp.connection.send(update);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("container reveal failed: " + t.getMessage());
        }
    }

    private static BlockEntityType<?> readType(Object info) throws ReflectiveOperationException {
        if (BEI_TYPE_METHOD != null) {
            return (BlockEntityType<?>) BEI_TYPE_METHOD.invoke(info);
        }
        return (BlockEntityType<?>) BEI_TYPE_FIELD.get(info);
    }

    private static int readPackedXZ(Object info) throws ReflectiveOperationException {
        if (BEI_PACKED_XZ_METHOD != null) {
            return (int) BEI_PACKED_XZ_METHOD.invoke(info);
        }
        return (int) BEI_PACKED_XZ_FIELD.get(info);
    }

    private static int readY(Object info) throws ReflectiveOperationException {
        if (BEI_Y_METHOD != null) {
            return (int) BEI_Y_METHOD.invoke(info);
        }
        return (int) BEI_Y_FIELD.get(info);
    }
}
