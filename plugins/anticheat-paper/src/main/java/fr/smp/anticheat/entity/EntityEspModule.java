package fr.smp.anticheat.entity;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.config.AntiCheatConfig;
import fr.smp.anticheat.visibility.VisibilityEngine;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-viewer entity ESP protection.
 *
 * On AddEntity packet: if the entity is far + no LoS, the packet is dropped.
 * The entity then exists server-side but the client never tracks it.
 *
 * A periodic task re-evaluates: if a previously hidden entity becomes visible
 * (player turns to face the building, walls broken, etc.), we re-send the
 * AddEntity packet. If a previously visible entity becomes hidden, we send
 * RemoveEntities so the client stops rendering / tracking it.
 *
 * This defeats radar/ESP cheats that read entity packets directly.
 */
public final class EntityEspModule implements Listener {

    // Direct field access to avoid ServerLevel.getEntity() lookups on the Netty thread
    // (which is async-unsafe and trips Paper's AsyncCatcher).
    private static final VarHandle MOVE_ENTITY_ID;
    private static final VarHandle ROTATE_ENTITY_ID;
    private static final VarHandle EVENT_ENTITY_ID;

    static {
        try {
            MOVE_ENTITY_ID = MethodHandles
                    .privateLookupIn(ClientboundMoveEntityPacket.class, MethodHandles.lookup())
                    .findVarHandle(ClientboundMoveEntityPacket.class, "entityId", int.class);
            ROTATE_ENTITY_ID = MethodHandles
                    .privateLookupIn(ClientboundRotateHeadPacket.class, MethodHandles.lookup())
                    .findVarHandle(ClientboundRotateHeadPacket.class, "entityId", int.class);
            EVENT_ENTITY_ID = MethodHandles
                    .privateLookupIn(ClientboundEntityEventPacket.class, MethodHandles.lookup())
                    .findVarHandle(ClientboundEntityEventPacket.class, "entityId", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final AntiCheatPlugin plugin;
    private final AntiCheatConfig cfg;
    private final VisibilityEngine visibility;

    /** viewer UUID → entity IDs currently hidden from this viewer client-side. */
    private final ConcurrentMap<UUID, Set<Integer>> hidden = new ConcurrentHashMap<>();

    private BukkitTask task;

    public EntityEspModule(AntiCheatPlugin plugin, AntiCheatConfig cfg, VisibilityEngine visibility) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.visibility = visibility;
    }

    public boolean enabled() {
        return cfg.entityEspEnabled();
    }

    public void start() {
        int interval = Math.max(1, cfg.entityCheckIntervalTicks());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        hidden.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        clearViewer(e.getPlayer().getUniqueId());
    }

    public boolean isHidden(Player viewer, int entityId) {
        Set<Integer> hiddenIds = hidden.get(viewer.getUniqueId());
        return hiddenIds != null && hiddenIds.contains(entityId);
    }

    /**
     * Drops all hidden-entity state for a viewer. Useful on quit / world changes so a
     * fresh pairing stream is never filtered by stale IDs from the previous context.
     */
    public void clearViewer(UUID viewerId) {
        if (viewerId == null) return;
        hidden.remove(viewerId);
    }

    /**
     * Called from OutboundFilter on Netty thread. Drops packets that refer to entities
     * the viewer client does not currently track because we hid them.
     */
    public boolean shouldDropPacket(Player viewer, Packet<?> packet) {
        if (packet instanceof ClientboundAddEntityPacket add) {
            return shouldDropAdd(viewer, add);
        }
        try {
            if (packet instanceof ClientboundSetEntityDataPacket data) {
                return isHidden(viewer, data.id());
            }
            if (packet instanceof ClientboundSetEquipmentPacket equipment) {
                return isHidden(viewer, equipment.getEntity());
            }
            if (packet instanceof ClientboundUpdateAttributesPacket attrs) {
                return isHidden(viewer, attrs.getEntityId());
            }
            if (packet instanceof ClientboundSetEntityMotionPacket motion) {
                return isHidden(viewer, motion.id());
            }
            if (packet instanceof ClientboundTeleportEntityPacket teleport) {
                return isHidden(viewer, teleport.id());
            }
            if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
                return isHidden(viewer, sync.id());
            }
            if (packet instanceof ClientboundAnimatePacket animate) {
                return isHidden(viewer, animate.getId());
            }
            if (packet instanceof ClientboundSetPassengersPacket passengers) {
                if (isHidden(viewer, passengers.getVehicle())) return true;
                for (int passengerId : passengers.getPassengers()) {
                    if (isHidden(viewer, passengerId)) return true;
                }
                return false;
            }
            if (packet instanceof ClientboundSetEntityLinkPacket link) {
                return isHidden(viewer, link.getSourceId())
                        || (link.getDestId() != 0 && isHidden(viewer, link.getDestId()));
            }
            // The three packets below only expose entityId via getEntity(Level), which
            // performs an async-unsafe ServerLevel lookup. Read the field directly.
            if (packet instanceof ClientboundMoveEntityPacket move) {
                return isHidden(viewer, (int) MOVE_ENTITY_ID.get(move));
            }
            if (packet instanceof ClientboundRotateHeadPacket rotate) {
                return isHidden(viewer, (int) ROTATE_ENTITY_ID.get(rotate));
            }
            if (packet instanceof ClientboundEntityEventPacket event) {
                return isHidden(viewer, (int) EVENT_ENTITY_ID.get(event));
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Called from OutboundFilter on Netty thread. Returns true if packet should be dropped. */
    public boolean shouldDropAdd(Player viewer, ClientboundAddEntityPacket pkt) {
        try {
            EntityType<?> type = pkt.getType();
            String name = type.toString().toLowerCase();
            if (cfg.alwaysVisibleEntities().contains(name)) return false;
            if (pkt.getUUID().equals(viewer.getUniqueId())) return false;

            // We must NOT resolve the entity server-side here — ServerLevel.getEntity()
            // and raytraced LoS read chunk state and trip Paper's AsyncCatcher on the
            // Netty thread. Use packet payload + viewer position fields only (plain
            // double reads, no async trap). The periodic tick() is the authoritative
            // pass: it raytraces on the main thread and emits RemoveEntities for any
            // newly-occluded entity — that's what actually enforces the ESP block.
            //
            // Trade-off: a newly-spawned occluded entity is briefly visible until the
            // next tick() cycle (`entity.check-interval-ticks` — default 10t = ~500ms).
            ServerPlayer sp = ((CraftPlayer) viewer).getHandle();
            double dx = pkt.getX() - sp.getX();
            double dy = pkt.getY() - sp.getY();
            double dz = pkt.getZ() - sp.getZ();
            double dist2 = dx * dx + dy * dy + dz * dz;
            double hideD = cfg.entityHideDistance();
            if (dist2 < hideD * hideD) return false; // near radius, always visible
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private void tick() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (plugin.bypass().isBypassed(viewer)) continue;
            UUID id = viewer.getUniqueId();
            Set<Integer> hiddenIds = hidden.computeIfAbsent(id, ignored -> ConcurrentHashMap.newKeySet());
            ServerPlayer sp = ((CraftPlayer) viewer).getHandle();
            ServerLevel level = (ServerLevel) sp.level();

            // Re-check hidden entities for restoration
            Set<Integer> toRestore = ConcurrentHashMap.newKeySet();
            for (int eid : hiddenIds) {
                Entity entity = level.getEntity(eid);
                if (entity == null) {
                    toRestore.add(eid);
                    continue;
                }
                double dx = entity.getX() - sp.getX();
                double dy = entity.getY() - sp.getY();
                double dz = entity.getZ() - sp.getZ();
                double dist2 = dx * dx + dy * dy + dz * dz;
                double hideD = cfg.entityHideDistance();
                boolean inRadius = dist2 < hideD * hideD;
                if (inRadius) {
                    hiddenIds.remove(eid);
                    sendPairing(sp, entity);
                    continue;
                }
                int bx = (int) Math.floor(entity.getX());
                int by = (int) Math.floor(entity.getY() + entity.getBbHeight() * 0.5);
                int bz = (int) Math.floor(entity.getZ());
                if (visibility.hasLineOfSight(viewer, bx, by, bz)) {
                    hiddenIds.remove(eid);
                    sendPairing(sp, entity);
                }
            }
            for (int eid : toRestore) hiddenIds.remove(eid);

            // Scope the scan to a bounding box around the viewer — previously iterated
            // every entity in the level on every tick. Uses the Bukkit API directly
            // (getNearbyEntities maps to the same chunk-backed spatial iterator).
            double maxScan = cfg.entityHideDistance() * 2;
            double maxScan2 = maxScan * maxScan;
            double hideD = cfg.entityHideDistance();
            double hideD2 = hideD * hideD;
            for (var bukkitEntity : viewer.getNearbyEntities(maxScan, maxScan, maxScan)) {
                Entity entity = ((org.bukkit.craftbukkit.entity.CraftEntity) bukkitEntity).getHandle();
                if (entity == sp) continue;
                int eid = entity.getId();
                if (hiddenIds.contains(eid)) continue;
                String typeName = entity.getType().toString().toLowerCase();
                if (cfg.alwaysVisibleEntities().contains(typeName)) continue;
                double dx = entity.getX() - sp.getX();
                double dy = entity.getY() - sp.getY();
                double dz = entity.getZ() - sp.getZ();
                double dist2 = dx * dx + dy * dy + dz * dz;
                if (dist2 > maxScan2) continue;
                if (dist2 < hideD2) continue;
                int bx = (int) Math.floor(entity.getX());
                int by = (int) Math.floor(entity.getY() + entity.getBbHeight() * 0.5);
                int bz = (int) Math.floor(entity.getZ());
                if (!visibility.hasLineOfSight(viewer, bx, by, bz)) {
                    sendRemove(sp, entity);
                    hiddenIds.add(eid);
                }
            }
        }
    }

    private void sendPairing(ServerPlayer sp, Entity entity) {
        try {
            ChunkMap.TrackedEntity tracked = entity.moonrise$getTrackedEntity();
            if (tracked == null) return;
            tracked.serverEntity.addPairing(sp);
        } catch (Throwable ignored) {
        }
    }

    private void sendRemove(ServerPlayer sp, Entity entity) {
        try {
            ChunkMap.TrackedEntity tracked = entity.moonrise$getTrackedEntity();
            if (tracked != null) {
                tracked.serverEntity.removePairing(sp);
                return;
            }
            sp.connection.send(new ClientboundRemoveEntitiesPacket(entity.getId()));
        } catch (Throwable ignored) {
        }
    }
}
