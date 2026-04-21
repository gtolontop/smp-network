package fr.smp.core.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Un NPC instancié : wrap l'entité NMS "fake-player" et mémorise à quels
 * joueurs il a été montré pour gérer correctement despawn/teleport.
 */
public final class Npc {

    private final long id;
    private String displayName;
    private final UUID profileUuid;
    private final Location spawnLocation;
    private Location currentLocation;
    private String skinOwner;
    private String skinValue;
    private String skinSignature;
    private boolean wander;
    private double wanderRadius;

    private Object nmsPlayer;
    private int entityId = -1;
    private final Set<UUID> shownTo = new HashSet<>();

    public Npc(long id, String displayName, UUID profileUuid, Location loc,
               String skinOwner, String skinValue, String skinSignature,
               boolean wander, double wanderRadius) {
        this.id = id;
        this.displayName = displayName;
        this.profileUuid = profileUuid;
        this.spawnLocation = loc.clone();
        this.currentLocation = loc.clone();
        this.skinOwner = skinOwner;
        this.skinValue = skinValue;
        this.skinSignature = skinSignature;
        this.wander = wander;
        this.wanderRadius = wanderRadius;
    }

    public long id() { return id; }
    public String displayName() { return displayName; }
    public UUID profileUuid() { return profileUuid; }
    public Location spawnLocation() { return spawnLocation.clone(); }
    public Location currentLocation() { return currentLocation.clone(); }
    public World world() { return spawnLocation.getWorld(); }
    public String skinOwner() { return skinOwner; }
    public String skinValue() { return skinValue; }
    public String skinSignature() { return skinSignature; }
    public boolean wander() { return wander; }
    public double wanderRadius() { return wanderRadius; }
    public int entityId() { return entityId; }
    public Object nmsPlayer() { return nmsPlayer; }
    public Set<UUID> shownTo() { return shownTo; }

    public void setDisplayName(String s) { this.displayName = s; }
    public void setSkin(String owner, String value, String signature) {
        this.skinOwner = owner; this.skinValue = value; this.skinSignature = signature;
    }
    public void setWander(boolean w) { this.wander = w; }
    public void setWanderRadius(double r) { this.wanderRadius = r; }
    public void setCurrentLocation(Location l) { this.currentLocation = l.clone(); }
    public void setNmsPlayer(Object p, int id) { this.nmsPlayer = p; this.entityId = id; }

    /**
     * Envoie les packets de spawn au joueur. Ordre obligatoire :
     *  1. PlayerInfoUpdate  (ADD_PLAYER + UPDATE_LISTED)  — publie skin + profile
     *  2. AddEntity                                      — apparition côté client
     *  3. SetEntityData                                  — couches de skin visibles
     *  4. RotateHead                                     — yaw tête cohérente
     *  5. PlayerInfoRemove (+30t)                        — sort du tab list
     */
    public void spawnTo(Player viewer, org.bukkit.plugin.Plugin plugin) {
        if (nmsPlayer == null) return;
        if (!viewer.getWorld().equals(world())) return;
        try {
            Object addInfo = NpcNms.buildAddPlayerInfoPacket(nmsPlayer);
            Object addEnt = NpcNms.buildAddEntityPacket(nmsPlayer);
            Object setData = NpcNms.buildSetDataPacket(nmsPlayer);
            Object rotHead = NpcNms.buildRotateHeadPacket(nmsPlayer, currentLocation.getYaw());

            NpcNms.sendAll(viewer, addInfo, addEnt, setData, rotHead);
            shownTo.add(viewer.getUniqueId());

            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (!viewer.isOnline()) return;
                    Object removeInfo = NpcNms.buildRemovePlayerInfoPacket(profileUuid);
                    NpcNms.send(viewer, removeInfo);
                } catch (Throwable ignored) {}
            }, 40L);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void despawnFor(Player viewer) {
        if (entityId < 0) return;
        try {
            Object rm = NpcNms.buildRemoveEntityPacket(entityId);
            NpcNms.send(viewer, rm);
            Object removeInfo = NpcNms.buildRemovePlayerInfoPacket(profileUuid);
            NpcNms.send(viewer, removeInfo);
        } catch (Throwable ignored) {}
        shownTo.remove(viewer.getUniqueId());
    }
}
