package fr.smp.core.npc;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class NpcManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final Database db;
    private final Map<Long, Npc> npcs = new HashMap<>();
    private final Random rng = new Random();
    private BukkitTask wanderTask;
    private BukkitTask nameTask;
    /** Cached NMS Entity#setPos(double,double,double) — looked up lazily once
     *  per JVM since the wander tick re-resolves it 5 NPCs/sec otherwise. */
    private static volatile Method setPosMethod;

    public NpcManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        if (!NpcNms.ok()) {
            plugin.getLogger().warning("NPCs désactivés : init NMS en échec — "
                    + (NpcNms.initError() != null ? NpcNms.initError().getMessage() : "raison inconnue"));
            return;
        }
        loadAll();
        Bukkit.getScheduler().runTask(plugin, this::spawnAllToEveryone);

        wanderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWander, 40L, 20L);
        nameTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickNameVisibility, 40L, 20L);
    }

    public void stop() {
        if (wanderTask != null) wanderTask.cancel();
        if (nameTask != null) nameTask.cancel();
        for (Npc npc : npcs.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                npc.despawnFor(p);
            }
        }
        npcs.clear();
    }

    public Map<Long, Npc> all() { return npcs; }
    public Npc byName(String name) {
        for (Npc n : npcs.values()) {
            if (n.displayName().equalsIgnoreCase(name)) return n;
        }
        return null;
    }
    public Npc byId(long id) { return npcs.get(id); }

    /* ================================  Persistence  ============================== */

    private void loadAll() {
        npcs.clear();
        try (Connection c = db.get();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id, display_name, world, x, y, z, yaw, pitch, "
                             + "skin_owner, skin_value, skin_signature, wander, wander_radius "
                             + "FROM npcs WHERE server = '" + serverTag() + "'")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String dn = rs.getString("display_name");
                World w = Bukkit.getWorld(rs.getString("world"));
                if (w == null) continue;
                Location loc = new Location(w,
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
                String owner = rs.getString("skin_owner");
                String value = rs.getString("skin_value");
                String sig = rs.getString("skin_signature");
                boolean wander = rs.getInt("wander") == 1;
                double radius = rs.getDouble("wander_radius");
                UUID pUuid = UUID.nameUUIDFromBytes(("NPC:" + id).getBytes());
                Npc npc = new Npc(id, dn, pUuid, loc, owner, value, sig, wander, radius);
                spawnEntity(npc);
                npcs.put(id, npc);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Load NPCs: " + e.getMessage());
        }
    }

    private void spawnAllToEveryone() {
        for (Npc npc : npcs.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(npc.world())) npc.spawnTo(p, plugin);
            }
        }
    }

    public Npc create(Location loc, String displayName, SkinFetcher.Skin skin, boolean wander) {
        if (!NpcNms.ok()) {
            // Sans accès NMS, on ne peut pas spawner d'entité — on refuse la création
            // pour ne pas écrire des lignes fantômes en DB qui réapparaîtraient invisibles
            // au prochain démarrage.
            plugin.getLogger().warning("Refus de création NPC : init NMS en échec — "
                    + (NpcNms.initError() != null ? NpcNms.initError().getMessage() : "raison inconnue"));
            return null;
        }
        long id;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO npcs (server, display_name, world, x, y, z, yaw, pitch, "
                             + "skin_owner, skin_value, skin_signature, wander, wander_radius) "
                             + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, serverTag());
            ps.setString(2, displayName);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.setString(9, skin != null ? skin.ownerName() : null);
            ps.setString(10, skin != null ? skin.value() : null);
            ps.setString(11, skin != null ? skin.signature() : null);
            ps.setInt(12, wander ? 1 : 0);
            ps.setDouble(13, 5.0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return null;
                id = keys.getLong(1);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Create NPC: " + e.getMessage());
            return null;
        }
        UUID pUuid = UUID.nameUUIDFromBytes(("NPC:" + id).getBytes());
        Npc npc = new Npc(id, displayName, pUuid, loc,
                skin != null ? skin.ownerName() : null,
                skin != null ? skin.value() : null,
                skin != null ? skin.signature() : null,
                wander, 5.0);
        spawnEntity(npc);
        npcs.put(id, npc);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(npc.world())) npc.spawnTo(p, plugin);
        }
        return npc;
    }

    public boolean remove(Npc npc) {
        if (npc == null) return false;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM npcs WHERE id = ?")) {
            ps.setLong(1, npc.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Remove NPC: " + e.getMessage());
            return false;
        }
        for (Player p : Bukkit.getOnlinePlayers()) npc.despawnFor(p);
        npcs.remove(npc.id());
        return true;
    }

    public void reskin(Npc npc, SkinFetcher.Skin skin) {
        npc.setSkin(skin.ownerName(), skin.value(), skin.signature());
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE npcs SET skin_owner=?, skin_value=?, skin_signature=? WHERE id=?")) {
            ps.setString(1, skin.ownerName());
            ps.setString(2, skin.value());
            ps.setString(3, skin.signature());
            ps.setLong(4, npc.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Reskin NPC: " + e.getMessage());
        }
        // On respawn complètement pour que le nouveau skin soit pris en compte.
        respawn(npc);
    }

    public void toggleWander(Npc npc) {
        npc.setWander(!npc.wander());
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE npcs SET wander=? WHERE id=?")) {
            ps.setInt(1, npc.wander() ? 1 : 0);
            ps.setLong(2, npc.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Toggle wander: " + e.getMessage());
        }
    }

    public void move(Npc npc, Location loc) {
        npc.setCurrentLocation(loc);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE npcs SET world=?, x=?, y=?, z=?, yaw=?, pitch=? WHERE id=?")) {
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.setLong(7, npc.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Move NPC: " + e.getMessage());
        }
        respawn(npc);
    }

    public void rename(Npc npc, String newName) {
        npc.setDisplayName(newName);
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE npcs SET display_name=? WHERE id=?")) {
            ps.setString(1, newName);
            ps.setLong(2, npc.id());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Rename NPC: " + e.getMessage());
        }
        // Le pseudo est publié via le GameProfile : un respawn est nécessaire.
        respawn(npc);
    }

    private void respawn(Npc npc) {
        for (Player p : Bukkit.getOnlinePlayers()) npc.despawnFor(p);
        npc.setNmsPlayer(null, -1);
        spawnEntity(npc);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(npc.world())) npc.spawnTo(p, plugin);
        }
    }

    private void spawnEntity(Npc npc) {
        try {
            Object profile = NpcNms.buildGameProfile(
                    npc.profileUuid(), truncatedName(npc.displayName(), npc.id()),
                    npc.skinValue(), npc.skinSignature());
            Object nmsPlayer = NpcNms.createFakePlayer(npc.world(), npc.currentLocation(), profile);
            npc.setNmsPlayer(nmsPlayer, NpcNms.getEntityId(nmsPlayer));
        } catch (Throwable t) {
            plugin.getLogger().warning("Spawn NPC entity: " + t.getMessage());
        }
    }

    /**
     * Le pseudo affiché au-dessus de la tête est celui du GameProfile — limité
     * à 16 caractères. On tronque + on suffixe avec l'id pour garantir l'unicité
     * (plusieurs NPCs avec le même "nom court" resteraient distincts côté client).
     */
    private String truncatedName(String name, long id) {
        String stripped = stripMm(name);
        if (stripped.length() > 16) stripped = stripped.substring(0, 14) + "_" + (id % 10);
        return stripped;
    }

    private String stripMm(String mm) {
        try {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(MM.deserialize(mm));
        } catch (Throwable t) {
            return mm.replaceAll("<[^>]*>", "");
        }
    }

    /* ================================  Listeners  ================================ */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (Npc npc : npcs.values()) {
                if (p.getWorld().equals(npc.world())) npc.spawnTo(p, plugin);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        for (Npc npc : npcs.values()) npc.shownTo().remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        for (Npc npc : npcs.values()) {
            if (p.getWorld().equals(npc.world())) {
                npc.spawnTo(p, plugin);
            } else {
                npc.despawnFor(p);
            }
        }
    }

    /* ================================  Wander tick  ============================== */

    private void tickWander() {
        for (Npc npc : npcs.values()) {
            if (!npc.wander()) continue;
            if (rng.nextInt(4) != 0) continue; // ~25% par tick pour rester tranquille
            tryWanderStep(npc);
        }
    }

    private void tryWanderStep(Npc npc) {
        Location base = npc.spawnLocation();
        Location cur = npc.currentLocation();
        double dx = (rng.nextDouble() - 0.5) * 2.0;
        double dz = (rng.nextDouble() - 0.5) * 2.0;
        double nx = cur.getX() + dx;
        double nz = cur.getZ() + dz;
        // Reste dans le rayon de balade autour du spawn initial — squared compare
        // évite l'allocation de 2 Vector + un sqrt par tick × NPCs en wander.
        double rdx = nx - base.getX();
        double rdz = nz - base.getZ();
        double radius = npc.wanderRadius();
        if (rdx * rdx + rdz * rdz > radius * radius) {
            dx = base.getX() - cur.getX();
            dz = base.getZ() - cur.getZ();
            nx = cur.getX() + Math.signum(dx) * 0.5;
            nz = cur.getZ() + Math.signum(dz) * 0.5;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(nx - cur.getX()), nz - cur.getZ()));
        Location next = new Location(cur.getWorld(), nx, cur.getY(), nz, yaw, 0f);
        // Vérifie qu'on n'est pas dans un bloc solide : sinon on abandonne ce tick.
        if (next.getBlock().getType().isSolid()) return;
        npc.setCurrentLocation(next);
        try {
            Object data = npc.nmsPlayer();
            if (data != null) {
                // Cache the reflective lookup — same NMS class for every NPC.
                Method setPos = setPosMethod;
                if (setPos == null) {
                    setPos = data.getClass().getMethod("setPos", double.class, double.class, double.class);
                    setPos.setAccessible(true);
                    setPosMethod = setPos;
                }
                setPos.invoke(data, nx, cur.getY(), nz);
                // Envoie téléport + rotation de tête
                Object tp = NpcNms.buildTeleportPacket(data);
                Object rot = NpcNms.buildRotateHeadPacket(data, yaw);
                World npcWorld = npc.world();
                java.util.Set<UUID> shown = npc.shownTo();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld() != npcWorld) continue;
                    if (!shown.contains(p.getUniqueId())) continue;
                    NpcNms.sendAll(p, tp, rot);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Les fake-players n'ont pas de nameplate par défaut côté client. On
     * suffit à afficher le pseudo du GameProfile (publié via PlayerInfoUpdate)
     * qui apparaît automatiquement au-dessus de la tête en MC standard.
     * Ce tick-là sert juste à re-spawner les joueurs qui ont changé de chunk
     * hors rayon de vision (le client drop l'entité si trop loin).
     */
    private void tickNameVisibility() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty() || npcs.isEmpty()) return;
        // Iterate per-player (typically much fewer than NPCs) and bail out on world
        // mismatch before paying the distance maths. Inlines the squared distance to
        // avoid Location.distanceSquared() (allocates + revalidates worlds) — saves
        // ~10× CPU on the hot path with N NPCs × M players.
        final double SHOW_SQ = 64.0 * 64.0;
        final double HIDE_SQ = 96.0 * 96.0;
        for (Player p : online) {
            World pw = p.getWorld();
            Location ploc = p.getLocation();
            double px = ploc.getX(), py = ploc.getY(), pz = ploc.getZ();
            UUID pid = p.getUniqueId();
            for (Npc npc : npcs.values()) {
                if (npc.world() != pw) continue;
                Location nloc = npc.currentLocation();
                double dx = nloc.getX() - px;
                double dy = nloc.getY() - py;
                double dz = nloc.getZ() - pz;
                double d2 = dx * dx + dy * dy + dz * dz;
                boolean shown = npc.shownTo().contains(pid);
                if (d2 <= SHOW_SQ && !shown) {
                    npc.spawnTo(p, plugin);
                } else if (d2 > HIDE_SQ && shown) {
                    npc.despawnFor(p);
                }
            }
        }
    }

    private String serverTag() {
        return plugin.getServerType();
    }
}
