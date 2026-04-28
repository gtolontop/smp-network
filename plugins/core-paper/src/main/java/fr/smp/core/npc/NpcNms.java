package fr.smp.core.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Accès NMS par réflexion pour spawner des NPCs "fake-player".
 * Paper 1.20.5+ utilise les mappings Mojang au runtime (plus de obf).
 *
 * Toute la glue est isolée ici : si une future version casse un de ces
 * accesseurs on patche ce fichier sans toucher au reste du code.
 */
public final class NpcNms {

    private NpcNms() {}

    private static final Class<?> C_CRAFTSERVER;
    private static final Class<?> C_CRAFTWORLD;
    private static final Class<?> C_CRAFTPLAYER;
    private static final Class<?> C_SERVERPLAYER;
    private static final Class<?> C_SERVERLEVEL;
    private static final Class<?> C_MINECRAFT_SERVER;
    private static final Class<?> C_GAMEPROFILE;
    private static final Class<?> C_PROPERTY;
    private static final Class<?> C_PROPERTYMAP;
    private static final Class<?> C_GAMETYPE;
    private static final Class<?> C_CLIENT_INFO;
    private static final Class<?> C_ENTITY;
    private static final Class<?> C_ENTITY_TYPE;
    private static final Class<?> C_VEC3;
    private static final Class<?> C_PLAYER_NMS;
    private static final Class<?> C_SYNCHED_DATA;
    private static final Class<?> C_DATA_ACCESSOR;
    private static final Class<?> C_SERVER_GAME_LISTENER;
    private static final Class<?> C_PACKET;
    private static final Class<?> C_CONNECTION;

    private static final Class<?> P_INFO_UPDATE;
    private static final Class<?> P_INFO_UPDATE_ACTION;
    private static final Class<?> P_INFO_REMOVE;
    private static final Class<?> P_ADD_ENTITY;
    private static final Class<?> P_SET_DATA;
    private static final Class<?> P_ROTATE_HEAD;
    private static final Class<?> P_REMOVE_ENTITIES;
    private static final Class<?> P_TELEPORT_ENTITY;

    private static final Method M_CRAFTSERVER_GETHANDLE;
    private static final Method M_CRAFTWORLD_GETHANDLE;
    private static final Method M_CRAFTPLAYER_GETHANDLE;
    private static final Method M_CLIENT_INFO_DEFAULT;
    private static final Method M_ENTITY_GET_ID;
    private static final Method M_ENTITY_GET_DATA;
    private static final Method M_ENTITY_GET_UUID;
    private static final Method M_ENTITY_GET_TYPE;
    private static final Method M_ENTITY_GET_X;
    private static final Method M_ENTITY_GET_Y;
    private static final Method M_ENTITY_GET_Z;
    private static final Method M_ENTITY_GET_X_ROT;
    private static final Method M_ENTITY_GET_Y_ROT;
    private static final Method M_ENTITY_GET_Y_HEAD_ROT;
    private static final Method M_POSITION_SYNC_OF;
    private static final Method M_SYNCHED_GET_NON_DEFAULT;
    private static final Method M_SYNCHED_SET;
    private static final Method M_GAMEPROFILE_GET_PROPS;
    private static final Method M_PROPERTYMAP_PUT;
    private static final Method M_SERVERPLAYER_SET_POS;
    private static final Method M_ENTITY_SET_ROT;
    private static final Method M_PACKET_SEND;

    private static final Constructor<?> CT_PROPERTY;
    private static final Constructor<?> CT_GAMEPROFILE;
    private static final Constructor<?> CT_SERVERPLAYER;
    private static final Constructor<?> CT_INFO_UPDATE;
    private static final Constructor<?> CT_INFO_REMOVE;
    private static final Constructor<?> CT_ADD_ENTITY;
    private static final Constructor<?> CT_SET_DATA;
    private static final Constructor<?> CT_ROTATE_HEAD;
    private static final Constructor<?> CT_REMOVE_ENTITIES;
    private static final Constructor<?> CT_TELEPORT_ENTITY;

    private static final Field F_DATA_PLAYER_CUSTOMISATION;
    private static final Field F_CONNECTION_OF_LISTENER;
    private static final Object VEC3_ZERO;

    private static final boolean OK;
    private static final Throwable INIT_ERROR;

    static {
        Class<?> craftServer = null, craftWorld = null, craftPlayer = null;
        Class<?> serverPlayer = null, serverLevel = null, minecraftServer = null;
        Class<?> gameProfile = null, property = null, propertyMap = null, gameType = null;
        Class<?> clientInfo = null, entity = null, playerNms = null;
        Class<?> entityType = null, vec3 = null;
        Class<?> synched = null, dataAccessor = null;
        Class<?> serverGameListener = null, packet = null, connection = null;

        Class<?> pInfoUpdate = null, pInfoUpdateAction = null, pInfoRemove = null;
        Class<?> pAddEntity = null, pSetData = null, pRotateHead = null;
        Class<?> pRemoveEntities = null, pTeleportEntity = null;
        Class<?> pPositionSync = null;

        Method m_csGetHandle = null, m_cwGetHandle = null, m_cpGetHandle = null;
        Method m_clientDefault = null, m_entGetId = null, m_entGetData = null;
        Method m_entGetUuid = null, m_entGetType = null;
        Method m_entGetX = null, m_entGetY = null, m_entGetZ = null;
        Method m_entGetXRot = null, m_entGetYRot = null, m_entGetYHeadRot = null;
        Method m_posSyncOf = null;
        Method m_synGetNonDefault = null, m_synSet = null;
        Method m_gpGetProps = null, m_pmPut = null;
        Method m_spSetPos = null, m_entSetRot = null, m_packetSend = null;

        Constructor<?> ctProperty = null, ctGameProfile = null, ctServerPlayer = null;
        Constructor<?> ctInfoUpdate = null, ctInfoRemove = null, ctAddEntity = null;
        Constructor<?> ctSetData = null, ctRotateHead = null, ctRemoveEntities = null;
        Constructor<?> ctTeleport = null;

        Field fDataCust = null, fConnOfListener = null;
        Object vec3Zero = null;

        boolean ok = false;
        Throwable err = null;

        try {
            craftServer = Class.forName("org.bukkit.craftbukkit.CraftServer");
            craftWorld = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");

            serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
            minecraftServer = Class.forName("net.minecraft.server.MinecraftServer");
            gameType = Class.forName("net.minecraft.world.level.GameType");
            clientInfo = Class.forName("net.minecraft.server.level.ClientInformation");
            entity = Class.forName("net.minecraft.world.entity.Entity");
            entityType = Class.forName("net.minecraft.world.entity.EntityType");
            vec3 = Class.forName("net.minecraft.world.phys.Vec3");
            playerNms = Class.forName("net.minecraft.world.entity.player.Player");
            synched = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            dataAccessor = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            serverGameListener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            packet = Class.forName("net.minecraft.network.protocol.Packet");
            connection = Class.forName("net.minecraft.network.Connection");

            gameProfile = Class.forName("com.mojang.authlib.GameProfile");
            property = Class.forName("com.mojang.authlib.properties.Property");
            propertyMap = Class.forName("com.mojang.authlib.properties.PropertyMap");

            pInfoUpdate = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            pInfoUpdateAction = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            pInfoRemove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            pAddEntity = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            pSetData = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            pRotateHead = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
            pRemoveEntities = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            try {
                pTeleportEntity = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
            } catch (ClassNotFoundException ignored) {
                pTeleportEntity = null;
            }
            try {
                pPositionSync = Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket");
            } catch (ClassNotFoundException ignored) {
                pPositionSync = null;
            }

            m_csGetHandle = craftServer.getMethod("getServer");
            m_cwGetHandle = craftWorld.getMethod("getHandle");
            m_cpGetHandle = craftPlayer.getMethod("getHandle");

            m_clientDefault = clientInfo.getMethod("createDefault");

            m_entGetId = entity.getMethod("getId");
            m_entGetData = entity.getMethod("getEntityData");
            m_entGetUuid = entity.getMethod("getUUID");
            m_entGetType = entity.getMethod("getType");
            // getX/Y/Z avec 0 paramètre — il existe aussi getX(double progress).
            for (Method m : entity.getMethods()) {
                if (m.getParameterCount() != 0) continue;
                switch (m.getName()) {
                    case "getX" -> { if (m_entGetX == null) m_entGetX = m; }
                    case "getY" -> { if (m_entGetY == null) m_entGetY = m; }
                    case "getZ" -> { if (m_entGetZ == null) m_entGetZ = m; }
                    case "getXRot" -> { if (m_entGetXRot == null) m_entGetXRot = m; }
                    case "getYRot" -> { if (m_entGetYRot == null) m_entGetYRot = m; }
                    case "getYHeadRot" -> { if (m_entGetYHeadRot == null) m_entGetYHeadRot = m; }
                    default -> {}
                }
            }
            m_synGetNonDefault = synched.getMethod("getNonDefaultValues");
            m_synSet = synched.getMethod("set", dataAccessor, Object.class);
            // Non-fatal: authlib 26.x+ removed mutable getProperties() from GameProfile.
            // NPCs spawnent sans skin custom si l'API n'est pas dispo.
            try {
                m_gpGetProps = gameProfile.getMethod("getProperties");
                try {
                    m_pmPut = propertyMap.getMethod("put", Object.class, Object.class);
                } catch (NoSuchMethodException ignored2) {
                    if (property != null)
                        m_pmPut = propertyMap.getMethod("put", String.class, property);
                }
            } catch (NoSuchMethodException ignored) {}

            // Vec3.ZERO pour la composante "movement" du AddEntity packet.
            try {
                vec3Zero = vec3.getField("ZERO").get(null);
            } catch (Throwable ignoredVec3) {
                vec3Zero = null;
            }

            // ServerPlayer#setPos(double, double, double)
            for (Method m : serverPlayer.getMethods()) {
                if (m.getName().equals("setPos") && m.getParameterCount() == 3
                        && m.getParameterTypes()[0] == double.class) {
                    m_spSetPos = m; break;
                }
            }
            // Entity#setYRot / Entity#setXRot
            for (Method m : entity.getMethods()) {
                if (m.getName().equals("absMoveTo") && m.getParameterCount() == 5) {
                    m_entSetRot = m; break; // void absMoveTo(double x,double y,double z,float yaw,float pitch)
                }
            }
            if (m_entSetRot == null) {
                for (Method m : entity.getMethods()) {
                    if (m.getName().equals("moveTo") && m.getParameterCount() == 5) {
                        m_entSetRot = m; break;
                    }
                }
            }

            // ServerGamePacketListenerImpl#send(Packet<?>)
            for (Method m : serverGameListener.getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1
                        && packet.isAssignableFrom(m.getParameterTypes()[0])) {
                    m_packetSend = m; break;
                }
            }

            // Property(String name, String value) — or Property(String,String,String)
            try {
                ctProperty = property.getConstructor(String.class, String.class, String.class);
            } catch (NoSuchMethodException e) {
                ctProperty = property.getConstructor(String.class, String.class);
            }
            // GameProfile(UUID, String)
            ctGameProfile = gameProfile.getConstructor(UUID.class, String.class);

            // ServerPlayer(MinecraftServer, ServerLevel, GameProfile, ClientInformation)
            ctServerPlayer = serverPlayer.getConstructor(
                    minecraftServer, serverLevel, gameProfile, clientInfo);

            // ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, Collection<ServerPlayer>)
            ctInfoUpdate = pInfoUpdate.getConstructor(EnumSet.class, Collection.class);
            ctInfoRemove = pInfoRemove.getConstructor(List.class);
            // ClientboundAddEntityPacket : la signature (Entity) a été supprimée en MC 1.21.5+
            // au profit de (Entity, ServerEntity). On essaie d'abord l'ancienne pour rester
            // compatible, sinon on bascule sur le constructeur tous-arguments stable
            // (id, uuid, x, y, z, xRot, yRot, type, data, movement, yHeadRot).
            try {
                ctAddEntity = pAddEntity.getConstructor(entity);
            } catch (NoSuchMethodException old) {
                ctAddEntity = pAddEntity.getConstructor(
                        int.class, UUID.class,
                        double.class, double.class, double.class,
                        float.class, float.class,
                        entityType, int.class, vec3, double.class);
            }
            ctSetData = pSetData.getConstructor(int.class, List.class);
            ctRotateHead = pRotateHead.getConstructor(entity, byte.class);
            ctRemoveEntities = pRemoveEntities.getConstructor(int[].class);

            // ClientboundTeleportEntityPacket(Entity) — n'existe plus en 1.21.5+.
            // On préfère donc ClientboundEntityPositionSyncPacket.of(Entity) si dispo,
            // et on garde ce slot uniquement pour les anciennes versions Paper.
            if (pTeleportEntity != null) {
                try {
                    ctTeleport = pTeleportEntity.getConstructor(entity);
                } catch (NoSuchMethodException e) {
                    ctTeleport = null;
                }
            }
            if (pPositionSync != null) {
                try {
                    m_posSyncOf = pPositionSync.getMethod("of", entity);
                } catch (NoSuchMethodException ignored) {
                    m_posSyncOf = null;
                }
            }

            // DATA_PLAYER_MODE_CUSTOMISATION : historiquement sur Player, déplacé sur
            // ServerPlayer en MC 26.x, supprimé en MC 26.1.2+. Non-fatal.
            try {
                fDataCust = serverPlayer.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
            } catch (NoSuchFieldException onSp) {
                try {
                    fDataCust = playerNms.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
                } catch (NoSuchFieldException ignored) {}
            }
            if (fDataCust != null) fDataCust.setAccessible(true);

            // ServerGamePacketListenerImpl#connection
            try {
                fConnOfListener = serverGameListener.getDeclaredField("connection");
            } catch (NoSuchFieldException nf) {
                // Paper/fork rename: try common alternatives
                for (Field f : serverGameListener.getDeclaredFields()) {
                    if (f.getType() == connection) { fConnOfListener = f; break; }
                }
            }
            if (fConnOfListener != null) fConnOfListener.setAccessible(true);

            ok = true;
        } catch (Throwable t) {
            err = t;
        }

        C_CRAFTSERVER = craftServer; C_CRAFTWORLD = craftWorld; C_CRAFTPLAYER = craftPlayer;
        C_SERVERPLAYER = serverPlayer; C_SERVERLEVEL = serverLevel; C_MINECRAFT_SERVER = minecraftServer;
        C_GAMEPROFILE = gameProfile; C_PROPERTY = property; C_PROPERTYMAP = propertyMap;
        C_GAMETYPE = gameType; C_CLIENT_INFO = clientInfo; C_ENTITY = entity;
        C_ENTITY_TYPE = entityType; C_VEC3 = vec3;
        C_PLAYER_NMS = playerNms; C_SYNCHED_DATA = synched; C_DATA_ACCESSOR = dataAccessor;
        C_SERVER_GAME_LISTENER = serverGameListener; C_PACKET = packet; C_CONNECTION = connection;

        P_INFO_UPDATE = pInfoUpdate; P_INFO_UPDATE_ACTION = pInfoUpdateAction;
        P_INFO_REMOVE = pInfoRemove; P_ADD_ENTITY = pAddEntity; P_SET_DATA = pSetData;
        P_ROTATE_HEAD = pRotateHead; P_REMOVE_ENTITIES = pRemoveEntities; P_TELEPORT_ENTITY = pTeleportEntity;

        M_CRAFTSERVER_GETHANDLE = m_csGetHandle; M_CRAFTWORLD_GETHANDLE = m_cwGetHandle;
        M_CRAFTPLAYER_GETHANDLE = m_cpGetHandle; M_CLIENT_INFO_DEFAULT = m_clientDefault;
        M_ENTITY_GET_ID = m_entGetId; M_ENTITY_GET_DATA = m_entGetData;
        M_ENTITY_GET_UUID = m_entGetUuid; M_ENTITY_GET_TYPE = m_entGetType;
        M_ENTITY_GET_X = m_entGetX; M_ENTITY_GET_Y = m_entGetY; M_ENTITY_GET_Z = m_entGetZ;
        M_ENTITY_GET_X_ROT = m_entGetXRot; M_ENTITY_GET_Y_ROT = m_entGetYRot;
        M_ENTITY_GET_Y_HEAD_ROT = m_entGetYHeadRot;
        M_POSITION_SYNC_OF = m_posSyncOf;
        M_SYNCHED_GET_NON_DEFAULT = m_synGetNonDefault; M_SYNCHED_SET = m_synSet;
        M_GAMEPROFILE_GET_PROPS = m_gpGetProps; M_PROPERTYMAP_PUT = m_pmPut;
        M_SERVERPLAYER_SET_POS = m_spSetPos; M_ENTITY_SET_ROT = m_entSetRot;
        M_PACKET_SEND = m_packetSend;

        CT_PROPERTY = ctProperty; CT_GAMEPROFILE = ctGameProfile;
        CT_SERVERPLAYER = ctServerPlayer; CT_INFO_UPDATE = ctInfoUpdate;
        CT_INFO_REMOVE = ctInfoRemove; CT_ADD_ENTITY = ctAddEntity;
        CT_SET_DATA = ctSetData; CT_ROTATE_HEAD = ctRotateHead;
        CT_REMOVE_ENTITIES = ctRemoveEntities; CT_TELEPORT_ENTITY = ctTeleport;

        F_DATA_PLAYER_CUSTOMISATION = fDataCust;
        F_CONNECTION_OF_LISTENER = fConnOfListener;
        VEC3_ZERO = vec3Zero;

        OK = ok;
        INIT_ERROR = err;
    }

    public static boolean ok() { return OK; }
    public static Throwable initError() { return INIT_ERROR; }

    public static Object serverHandle() throws Exception {
        return M_CRAFTSERVER_GETHANDLE.invoke(org.bukkit.Bukkit.getServer());
    }

    public static Object levelHandle(World w) throws Exception {
        return M_CRAFTWORLD_GETHANDLE.invoke(w);
    }

    public static Object buildGameProfile(UUID id, String name, String value, String signature) throws Exception {
        Object profile = CT_GAMEPROFILE.newInstance(id, name);
        if (value != null && !value.isBlank()
                && M_GAMEPROFILE_GET_PROPS != null && M_PROPERTYMAP_PUT != null) {
            Object prop;
            if (CT_PROPERTY.getParameterCount() == 3) {
                prop = CT_PROPERTY.newInstance("textures", value, signature);
            } else {
                prop = CT_PROPERTY.newInstance("textures", value);
            }
            Object map = M_GAMEPROFILE_GET_PROPS.invoke(profile);
            M_PROPERTYMAP_PUT.invoke(map, "textures", prop);
        }
        return profile;
    }

    public static Object createFakePlayer(World world, Location loc, Object gameProfile) throws Exception {
        Object server = serverHandle();
        Object level = levelHandle(world);
        Object info = M_CLIENT_INFO_DEFAULT.invoke(null);
        Object nmsPlayer = CT_SERVERPLAYER.newInstance(server, level, gameProfile, info);
        M_SERVERPLAYER_SET_POS.invoke(nmsPlayer, loc.getX(), loc.getY(), loc.getZ());
        if (M_ENTITY_SET_ROT != null) {
            M_ENTITY_SET_ROT.invoke(nmsPlayer,
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch());
        }
        if (F_DATA_PLAYER_CUSTOMISATION != null) {
            Object accessor = F_DATA_PLAYER_CUSTOMISATION.get(null);
            Object entityData = M_ENTITY_GET_DATA.invoke(nmsPlayer);
            M_SYNCHED_SET.invoke(entityData, accessor, (byte) 0x7F);
        }
        return nmsPlayer;
    }

    public static int getEntityId(Object nmsPlayer) throws Exception {
        return (int) M_ENTITY_GET_ID.invoke(nmsPlayer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object buildAddPlayerInfoPacket(Object nmsPlayer) throws Exception {
        EnumSet actions = EnumSet.noneOf((Class) P_INFO_UPDATE_ACTION);
        for (Object c : P_INFO_UPDATE_ACTION.getEnumConstants()) {
            String n = ((Enum<?>) c).name();
            // On veut publier le profil + skin sans apparaître dans la tab-list
            if (n.equals("ADD_PLAYER") || n.equals("UPDATE_LISTED")) actions.add(c);
        }
        return CT_INFO_UPDATE.newInstance(actions, List.of(nmsPlayer));
    }

    public static Object buildRemovePlayerInfoPacket(UUID uuid) throws Exception {
        return CT_INFO_REMOVE.newInstance(List.of(uuid));
    }

    public static Object buildAddEntityPacket(Object nmsPlayer) throws Exception {
        // Le constructeur (Entity) a été remplacé en MC 1.21.5+ par (Entity, ServerEntity).
        // Construire un ServerEntity proprement requiert l'arbre de tracking complet, donc
        // on alimente directement le constructeur 11-args (stable depuis 1.21.4).
        if (CT_ADD_ENTITY.getParameterCount() == 1) {
            return CT_ADD_ENTITY.newInstance(nmsPlayer);
        }
        int id = (int) M_ENTITY_GET_ID.invoke(nmsPlayer);
        UUID uuid = (UUID) M_ENTITY_GET_UUID.invoke(nmsPlayer);
        double x = (double) M_ENTITY_GET_X.invoke(nmsPlayer);
        double y = (double) M_ENTITY_GET_Y.invoke(nmsPlayer);
        double z = (double) M_ENTITY_GET_Z.invoke(nmsPlayer);
        float xRot = (float) M_ENTITY_GET_X_ROT.invoke(nmsPlayer);
        float yRot = (float) M_ENTITY_GET_Y_ROT.invoke(nmsPlayer);
        Object type = M_ENTITY_GET_TYPE.invoke(nmsPlayer);
        double yHeadRot = M_ENTITY_GET_Y_HEAD_ROT != null
                ? (double) (float) M_ENTITY_GET_Y_HEAD_ROT.invoke(nmsPlayer)
                : (double) yRot;
        return CT_ADD_ENTITY.newInstance(
                id, uuid, x, y, z, xRot, yRot, type, 0, VEC3_ZERO, yHeadRot);
    }

    public static Object buildSetDataPacket(Object nmsPlayer) throws Exception {
        int id = getEntityId(nmsPlayer);
        Object data = M_ENTITY_GET_DATA.invoke(nmsPlayer);
        Object list = M_SYNCHED_GET_NON_DEFAULT.invoke(data);
        if (list == null) return null;
        return CT_SET_DATA.newInstance(id, list);
    }

    public static Object buildRotateHeadPacket(Object nmsPlayer, float yaw) throws Exception {
        byte encoded = (byte) (int) (yaw * 256.0f / 360.0f);
        return CT_ROTATE_HEAD.newInstance(nmsPlayer, encoded);
    }

    public static Object buildRemoveEntityPacket(int entityId) throws Exception {
        return CT_REMOVE_ENTITIES.newInstance(new int[]{entityId});
    }

    public static Object buildTeleportPacket(Object nmsPlayer) throws Exception {
        // 1.21.4 et antérieur : ClientboundTeleportEntityPacket(Entity).
        // 1.21.5+ : utiliser ClientboundEntityPositionSyncPacket.of(Entity).
        if (CT_TELEPORT_ENTITY != null) {
            return CT_TELEPORT_ENTITY.newInstance(nmsPlayer);
        }
        if (M_POSITION_SYNC_OF != null) {
            return M_POSITION_SYNC_OF.invoke(null, nmsPlayer);
        }
        return null;
    }

    public static void send(Player player, Object packet) {
        if (packet == null) return;
        try {
            Object nmsPlayer = M_CRAFTPLAYER_GETHANDLE.invoke(player);
            Field connFld = C_SERVERPLAYER.getField("connection");
            Object listener = connFld.get(nmsPlayer);
            if (listener == null) return;
            M_PACKET_SEND.invoke(listener, packet);
        } catch (NoSuchFieldException nf) {
            try {
                Object nmsPlayer = M_CRAFTPLAYER_GETHANDLE.invoke(player);
                for (Field f : C_SERVERPLAYER.getFields()) {
                    if (C_SERVER_GAME_LISTENER.isAssignableFrom(f.getType())) {
                        Object listener = f.get(nmsPlayer);
                        if (listener != null) M_PACKET_SEND.invoke(listener, packet);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public static void sendAll(Player player, Object... packets) {
        for (Object p : packets) send(player, p);
    }
}
