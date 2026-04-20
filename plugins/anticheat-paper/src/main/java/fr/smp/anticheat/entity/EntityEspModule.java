package fr.smp.anticheat.entity;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.config.AntiCheatConfig;
import fr.smp.anticheat.visibility.VisibilityEngine;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
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

    private final AntiCheatPlugin plugin;
    private final AntiCheatConfig cfg;
    private final VisibilityEngine visibility;

    /** viewer UUID → entity IDs currently hidden from this viewer client-side. */
    private final ConcurrentMap<UUID, IntSet> hidden = new ConcurrentHashMap<>();

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
        hidden.remove(e.getPlayer().getUniqueId());
    }

    /** Called from OutboundFilter on Netty thread. Returns true if packet should be dropped. */
    public boolean shouldDropAdd(Player viewer, ClientboundAddEntityPacket pkt) {
        try {
            EntityType<?> type = pkt.getType();
            String name = type.toString().toLowerCase();
            if (cfg.alwaysVisibleEntities().contains(name)) return false;

            int eid = pkt.getId();
            ServerPlayer sp = ((CraftPlayer) viewer).getHandle();
            ServerLevel level = (ServerLevel) sp.level();
            Entity entity = level.getEntity(eid);
            // If entity unknown server-side or it's the viewer themselves, allow
            if (entity == null || entity == sp) return false;

            double dx = entity.getX() - sp.getX();
            double dy = entity.getY() - sp.getY();
            double dz = entity.getZ() - sp.getZ();
            double dist2 = dx * dx + dy * dy + dz * dz;
            double hideD = cfg.entityHideDistance();
            if (dist2 < hideD * hideD) return false; // within near radius, always visible

            // Beyond near radius: check LoS at the entity's chest level
            int bx = (int) Math.floor(entity.getX());
            int by = (int) Math.floor(entity.getY() + entity.getBbHeight() * 0.5);
            int bz = (int) Math.floor(entity.getZ());
            boolean los = visibility.hasLineOfSight(viewer, bx, by, bz);
            if (los) return false;

            hidden.computeIfAbsent(viewer.getUniqueId(), k -> new IntOpenHashSet()).add(eid);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void tick() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (plugin.bypass().isBypassed(viewer)) continue;
            UUID id = viewer.getUniqueId();
            IntSet hiddenIds = hidden.get(id);
            if (hiddenIds == null) continue;
            ServerPlayer sp = ((CraftPlayer) viewer).getHandle();
            ServerLevel level = (ServerLevel) sp.level();

            // Re-check hidden entities for restoration
            IntSet toRestore = new IntOpenHashSet();
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
                    sendAdd(sp, entity);
                    toRestore.add(eid);
                    continue;
                }
                int bx = (int) Math.floor(entity.getX());
                int by = (int) Math.floor(entity.getY() + entity.getBbHeight() * 0.5);
                int bz = (int) Math.floor(entity.getZ());
                if (visibility.hasLineOfSight(viewer, bx, by, bz)) {
                    sendAdd(sp, entity);
                    toRestore.add(eid);
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
                    sp.connection.send(new ClientboundRemoveEntitiesPacket(eid));
                    hiddenIds.add(eid);
                }
            }
        }
    }

    private void sendAdd(ServerPlayer sp, Entity entity) {
        try {
            sp.connection.send(new ClientboundAddEntityPacket(
                    entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
                    entity.getXRot(), entity.getYRot(), entity.getType(), 0,
                    entity.getDeltaMovement(), entity.getYHeadRot()));
        } catch (Throwable ignored) {
        }
    }
}
