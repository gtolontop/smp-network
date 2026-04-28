package fr.smp.core.npc;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.LeaderboardManager;
import fr.smp.core.storage.Database;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PodiumManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int REFRESH_INTERVAL_TICKS = 12_000; // 10 minutes

    record SlotConfig(int rank, LeaderboardManager.Category category,
                      LeaderboardManager.Scope scope, Location location) {}

    private final SMPCore plugin;
    private final Database db;
    private final Map<Integer, SlotConfig> slots = new LinkedHashMap<>();
    private final Map<Integer, Npc> activeNpcs = new HashMap<>();
    private final Map<Integer, TextDisplay> activeHolos = new HashMap<>();
    private BukkitTask refreshTask;

    public PodiumManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        loadSlots();
        if (!slots.isEmpty()) refresh();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::refresh, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    public void stop() {
        if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; }
        clearActive();
    }

    public Map<Integer, SlotConfig> slots() { return slots; }

    public boolean setSlot(int rank, Location loc,
                           LeaderboardManager.Category cat, LeaderboardManager.Scope scope) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO podium_slots(rank,category,scope,world,x,y,z,yaw,pitch) "
                             + "VALUES(?,?,?,?,?,?,?,?,?) "
                             + "ON CONFLICT(rank) DO UPDATE SET "
                             + "category=excluded.category, scope=excluded.scope, "
                             + "world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, "
                             + "yaw=excluded.yaw, pitch=excluded.pitch")) {
            ps.setInt(1, rank);
            ps.setString(2, cat.key());
            ps.setString(3, scope.key());
            ps.setString(4, loc.getWorld().getName());
            ps.setDouble(5, loc.getX());
            ps.setDouble(6, loc.getY());
            ps.setDouble(7, loc.getZ());
            ps.setFloat(8, loc.getYaw());
            ps.setFloat(9, loc.getPitch());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Podium setSlot: " + e.getMessage());
            return false;
        }
        loadSlots();
        refreshSlot(rank);
        return true;
    }

    public boolean removeSlot(int rank) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM podium_slots WHERE rank=?")) {
            ps.setInt(1, rank);
            if (ps.executeUpdate() == 0) return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Podium removeSlot: " + e.getMessage());
            return false;
        }
        despawnSlot(rank);
        loadSlots();
        return true;
    }

    public void refresh() {
        for (int rank : slots.keySet()) refreshSlot(rank);
    }

    private void refreshSlot(int rank) {
        SlotConfig cfg = slots.get(rank);
        if (cfg == null) { despawnSlot(rank); return; }

        LeaderboardManager.Result result = plugin.leaderboards().ranking(cfg.category(), cfg.scope(), null);
        List<LeaderboardManager.Entry> entries = result.entries();
        LeaderboardManager.Entry entry = entries.size() >= rank ? entries.get(rank - 1) : null;

        despawnSlot(rank);
        if (entry == null) return;

        String playerName = entry.sortName();
        SkinFetcher.fetch(playerName).thenAccept(skin ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        spawnSlot(rank, cfg, entry, skin)));
    }

    private void spawnSlot(int rank, SlotConfig cfg, LeaderboardManager.Entry entry,
                           SkinFetcher.Skin skin) {
        if (!NpcNms.ok()) return;
        Location loc = cfg.location();
        if (loc.getWorld() == null) return;

        UUID uuid = UUID.nameUUIDFromBytes(("Podium:" + rank).getBytes());
        String profileName = profileName(entry.sortName());

        Npc npc = new Npc(-rank, entry.displayName(), uuid, loc,
                skin != null ? skin.ownerName() : null,
                skin != null ? skin.value() : null,
                skin != null ? skin.signature() : null,
                false, 0.0);

        try {
            Object profile = NpcNms.buildGameProfile(uuid, profileName,
                    skin != null ? skin.value() : null,
                    skin != null ? skin.signature() : null);
            Object nmsPlayer = NpcNms.createFakePlayer(loc.getWorld(), loc, profile);
            npc.setNmsPlayer(nmsPlayer, NpcNms.getEntityId(nmsPlayer));
        } catch (Throwable t) {
            plugin.getLogger().warning("Podium NPC spawn #" + rank + ": " + t.getMessage());
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(loc.getWorld())) npc.spawnTo(p, plugin);
        }
        activeNpcs.put(rank, npc);

        Location holoLoc = loc.clone().add(0, 2.35, 0);
        TextDisplay td = holoLoc.getWorld().spawn(holoLoc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setDefaultBackground(false);
            d.setShadowed(false);
            d.text(holoText(rank, entry));
        });
        activeHolos.put(rank, td);
    }

    private void despawnSlot(int rank) {
        Npc npc = activeNpcs.remove(rank);
        if (npc != null) {
            for (Player p : Bukkit.getOnlinePlayers()) npc.despawnFor(p);
        }
        TextDisplay td = activeHolos.remove(rank);
        if (td != null && !td.isDead()) td.remove();
    }

    private void clearActive() {
        for (int rank : List.copyOf(activeNpcs.keySet())) despawnSlot(rank);
    }

    private void loadSlots() {
        slots.clear();
        try (Connection c = db.get();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT rank,category,scope,world,x,y,z,yaw,pitch FROM podium_slots ORDER BY rank")) {
            while (rs.next()) {
                int rank = rs.getInt("rank");
                LeaderboardManager.Category cat = LeaderboardManager.Category.parse(rs.getString("category"));
                LeaderboardManager.Scope scope = LeaderboardManager.Scope.parse(rs.getString("scope"));
                if (cat == null) cat = LeaderboardManager.Category.MONEY;
                if (scope == null) scope = LeaderboardManager.Scope.SOLO;
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) continue;
                Location loc = new Location(world,
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
                slots.put(rank, new SlotConfig(rank, cat, scope, loc));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Podium loadSlots: " + e.getMessage());
        }
    }

    private Component holoText(int rank, LeaderboardManager.Entry entry) {
        String rankTag = switch (rank) {
            case 1 -> "<gold><bold>#1</bold></gold>";
            case 2 -> "<white><bold>#2</bold></white>";
            case 3 -> "<#cd7f32><bold>#3</bold></#cd7f32>";
            default -> "<gray>#" + rank + "</gray>";
        };
        String name = MM.stripTags(entry.displayName());
        return MM.deserialize(rankTag + "\n<white>" + name + "</white>\n" + entry.valueDisplay());
    }

    private String profileName(String displayName) {
        String stripped = MM.stripTags(displayName);
        if (stripped.length() > 16) stripped = stripped.substring(0, 16);
        return stripped;
    }

    /* ===================== Listeners ===================== */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (Map.Entry<Integer, Npc> entry : activeNpcs.entrySet()) {
                Npc npc = entry.getValue();
                if (p.getWorld().equals(npc.world())) npc.spawnTo(p, plugin);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uid = e.getPlayer().getUniqueId();
        for (Npc npc : activeNpcs.values()) npc.shownTo().remove(uid);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        for (Npc npc : activeNpcs.values()) {
            if (p.getWorld().equals(npc.world())) npc.spawnTo(p, plugin);
            else npc.despawnFor(p);
        }
    }
}
