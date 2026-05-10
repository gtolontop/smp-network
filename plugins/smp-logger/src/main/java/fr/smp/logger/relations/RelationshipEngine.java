package fr.smp.logger.relations;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.dict.PlayerDict;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RelationshipEngine {

    private final SMPLogger plugin;
    private final Map<String, Long> signalCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> pvpPairs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> victimAttackers = new ConcurrentHashMap<>();
    private final Map<Long, ArrayDeque<ContainerUse>> containerUses = new HashMap<>();
    private final TeamResolver teams;
    private volatile long pausedUntilMs = 0L;
    private volatile String pauseReason = "";
    private int proximityTask = -1;

    public RelationshipEngine(SMPLogger plugin) {
        this.plugin = plugin;
        this.teams = new TeamResolver(plugin);
    }

    public void start() {
        if (!configuredEnabled()) return;
        if (lobbyBlocked()) {
            plugin.getLogger().info("Relationship evidence checker disabled on lobby server '" + serverIdentity() + "'.");
            return;
        }
        long period = Math.max(5L, plugin.getConfig().getLong("relationships.proximity.sample-seconds", 15L)) * 20L;
        proximityTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::sampleProximity, period, period);
    }

    public void stop() {
        if (proximityTask != -1) Bukkit.getScheduler().cancelTask(proximityTask);
        proximityTask = -1;
        signalCooldowns.clear();
        pvpPairs.clear();
        victimAttackers.clear();
        containerUses.clear();
    }

    public void pauseFor(long durationMs, String reason) {
        long now = System.currentTimeMillis();
        pausedUntilMs = durationMs <= 0L || durationMs > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + durationMs;
        pauseReason = reason == null || reason.isBlank() ? "manual" : truncate(reason, 80);
    }

    public void resume() {
        pausedUntilMs = 0L;
        pauseReason = "";
    }

    public boolean isRecordingEnabled() {
        return enabled();
    }

    public boolean isConfiguredEnabled() {
        return configuredEnabled();
    }

    public boolean isLobbyBlocked() {
        return lobbyBlocked();
    }

    public boolean isRuntimePaused() {
        return runtimePaused();
    }

    public long pausedUntilMs() {
        if (!runtimePaused()) return 0L;
        return pausedUntilMs;
    }

    public String pauseReason() {
        return pauseReason;
    }

    public boolean proximitySamplerRunning() {
        return proximityTask != -1;
    }

    public String serverIdentity() {
        String configured = plugin.getConfig().getString("relationships.server-id", "");
        if (configured != null && !configured.isBlank()) return configured.trim();
        File worldContainer = plugin.getServer().getWorldContainer();
        return worldContainer == null ? "unknown" : worldContainer.getName();
    }

    public void recordContainerUse(Player player, Location loc, Material material) {
        if (!enabled() || player == null || loc == null || material == null) return;
        if (!isTrackedContainer(material)) return;
        long now = System.currentTimeMillis();
        long key = locKey(loc);
        ArrayDeque<ContainerUse> uses = containerUses.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long windowMs = Math.max(60L, plugin.getConfig().getLong("relationships.shared-container.window-minutes", 360L)) * 60_000L;
        while (!uses.isEmpty() && now - uses.peekFirst().timeMs > windowMs) uses.removeFirst();

        for (ContainerUse use : uses) {
            if (use.player.equals(player.getUniqueId())) continue;
            RelationSignal signal = isFurnaceLike(material) ? RelationSignal.SHARED_FURNACE : RelationSignal.SHARED_CONTAINER;
            int points = plugin.getConfig().getInt("relationships.points." + signal.name().toLowerCase(Locale.ROOT), signal.defaultPoints());
            String detail = material.name() + " reused after " + ((now - use.timeMs) / 1000L) + "s";
            recordSignal(use.player, player.getUniqueId(), signal, points, loc, detail,
                    "container:" + signal.name() + ":" + key, containerCooldownMs());
        }
        uses.addLast(new ContainerUse(player.getUniqueId(), now));
    }

    public void recordTrade(UUID from, UUID to, RelationSignal signal, Location loc, String detail) {
        if (!enabled() || from == null || to == null) return;
        int points = plugin.getConfig().getInt("relationships.points." + signal.name().toLowerCase(Locale.ROOT), signal.defaultPoints());
        recordSignal(from, to, signal, points, loc, detail, "trade:" + signal.name(), tradeCooldownMs(signal));
    }

    public void recordPvpContact(Player attacker, Player victim) {
        if (!enabled() || attacker == null || victim == null || attacker.equals(victim)) return;
        String pair = uuidPair(attacker.getUniqueId(), victim.getUniqueId());
        pvpPairs.put(pair, System.currentTimeMillis());
        int points = plugin.getConfig().getInt("relationships.points.pvp_contact", RelationSignal.PVP_CONTACT.defaultPoints());
        recordSignal(attacker.getUniqueId(), victim.getUniqueId(), RelationSignal.PVP_CONTACT, points, victim.getLocation(),
                "direct PvP contact", "pvp", 60_000L);
    }

    public void recordCombatAssist(Player attacker, Player victim, Location loc) {
        if (!enabled() || attacker == null || victim == null || attacker.equals(victim)) return;
        long now = System.currentTimeMillis();
        Map<UUID, Long> attackers = victimAttackers.computeIfAbsent(victim.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        attackers.entrySet().removeIf(entry -> now - entry.getValue() > combatAssistWindowMs());
        for (UUID other : attackers.keySet()) {
            if (other.equals(attacker.getUniqueId())) continue;
            if (recentPvp(attacker.getUniqueId(), other)) continue;
            int points = plugin.getConfig().getInt("relationships.points.combat_assist", RelationSignal.COMBAT_ASSIST.defaultPoints());
            String detail = "both damaged " + victim.getName() + " within " + (combatAssistWindowMs() / 1000L) + "s";
            recordSignal(other, attacker.getUniqueId(), RelationSignal.COMBAT_ASSIST, points, loc, detail,
                    "combat:" + victim.getUniqueId(), 30_000L);
        }
        attackers.put(attacker.getUniqueId(), now);
    }

    public int scanHomes(double maxDistance, org.bukkit.command.CommandSender sender) {
        if (!enabled()) {
            if (sender != null) {
                sender.sendMessage(net.kyori.adventure.text.Component.text(
                        "Relationship evidence checker is disabled; home scan skipped.",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            }
            return -1;
        }
        List<HomePoint> homes = loadHomes();
        int matches = 0;
        double maxSq = maxDistance * maxDistance;
        for (int i = 0; i < homes.size(); i++) {
            HomePoint a = homes.get(i);
            for (int j = i + 1; j < homes.size(); j++) {
                HomePoint b = homes.get(j);
                if (a.uuid.equals(b.uuid)) continue;
                if (!a.world.equalsIgnoreCase(b.world)) continue;
                if (!a.server.equalsIgnoreCase(b.server)) continue;
                if (!scanWorldAllowed(a.world)) continue;
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                double dz = a.z - b.z;
                if ((dx * dx) + (dy * dy) + (dz * dz) > maxSq) continue;
                Location loc = toLoadedLocation(a);
                String detail = "homes within " + Math.round(Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)))
                        + " blocks on " + a.server + "/" + a.world;
                int points = plugin.getConfig().getInt("relationships.points.home_near", RelationSignal.HOME_NEAR.defaultPoints());
                plugin.players().idOf(a.uuid, a.name);
                plugin.players().idOf(b.uuid, b.name);
                recordSignal(a.uuid, b.uuid, RelationSignal.HOME_NEAR, points, loc, detail,
                        "home:" + a.slot + ":" + b.slot + ":" + a.server + ":" + a.world, 12L * 60L * 60L * 1000L);
                matches++;
            }
        }
        return matches;
    }

    public void resetPair(PlayerDict.Entry a, PlayerDict.Entry b) throws SQLException {
        String pair = pairKey(a.id(), b.id());
        try (Connection c = plugin.db().writer()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM relation_evidence WHERE pair_key=?")) {
                ps.setString(1, pair);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM relation_pairs WHERE pair_key=?")) {
                ps.setString(1, pair);
                ps.executeUpdate();
            }
        }
    }

    public void setNote(PlayerDict.Entry a, PlayerDict.Entry b, String note) throws SQLException {
        String pair = pairKey(a.id(), b.id());
        ensurePair(a.id(), b.id(), System.currentTimeMillis());
        try (Connection c = plugin.db().writer();
             PreparedStatement ps = c.prepareStatement("UPDATE relation_pairs SET note=? WHERE pair_key=?")) {
            ps.setString(1, note);
            ps.setString(2, pair);
            ps.executeUpdate();
        }
    }

    public boolean sameTeam(UUID a, UUID b) {
        if (a == null || b == null) return false;
        String ta = teams.teamOf(a);
        String tb = teams.teamOf(b);
        return ta != null && !ta.isBlank() && ta.equals(tb);
    }

    private void recordSignal(UUID a, UUID b, RelationSignal signal, int points, Location loc,
                              String detail, String cooldownScope, long cooldownMs) {
        if (!enabled()) return;
        if (a == null || b == null || a.equals(b)) return;
        if (sameTeam(a, b)) return;
        if (loc != null && ignoredLocation(loc)) return;
        int lowCmp = a.compareTo(b);
        UUID left = lowCmp <= 0 ? a : b;
        UUID right = lowCmp <= 0 ? b : a;
        String pairUuid = uuidPair(left, right);
        long now = System.currentTimeMillis();
        String cooldownKey = pairUuid + ":" + signal.name() + ":" + cooldownScope;
        Long last = signalCooldowns.get(cooldownKey);
        if (last != null && now - last < cooldownMs) return;
        signalCooldowns.put(cooldownKey, now);

        int leftId = plugin.players().idOf(left, knownName(left));
        int rightId = plugin.players().idOf(right, knownName(right));
        if (leftId == 0 || rightId == 0) return;
        int lowId = Math.min(leftId, rightId);
        int highId = Math.max(leftId, rightId);
        String pairKey = pairKey(lowId, highId);
        int worldId = loc == null || loc.getWorld() == null ? 0 : plugin.worlds().idOf(loc.getWorld());
        int x = loc == null ? 0 : loc.getBlockX();
        int y = loc == null ? 0 : loc.getBlockY();
        int z = loc == null ? 0 : loc.getBlockZ();
        String safeDetail = detail == null ? "" : detail;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> persistSignal(pairKey, lowId, highId, signal,
                points, now, worldId, x, y, z, safeDetail));
    }

    private void persistSignal(String pairKey, int lowId, int highId, RelationSignal signal, int points, long now,
                               int worldId, int x, int y, int z, String detail) {
        try (Connection c = plugin.db().writer()) {
            c.setAutoCommit(false);
            try {
                ensurePair(c, pairKey, lowId, highId, now);
                String column = signal.counterColumn();
                String sql = "UPDATE relation_pairs SET score = MAX(0, score + ?), "
                        + "level = CASE "
                        + "WHEN MAX(0, score + ?) >= ? THEN 'CONFIRMED' "
                        + "WHEN MAX(0, score + ?) >= ? THEN 'PROBABLE' "
                        + "WHEN MAX(0, score + ?) >= ? THEN 'SUSPECT' "
                        + "WHEN MAX(0, score + ?) >= ? THEN 'WATCH' "
                        + "ELSE 'CLEAR' END, "
                        + "evidence_count = evidence_count + 1, "
                        + column + " = " + column + " + ?, "
                        + "last_seen=?, last_signal=?, last_world_id=?, last_x=?, last_y=?, last_z=?, last_detail=? "
                        + "WHERE pair_key=?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    int confirmed = levelThreshold("confirmed", 80);
                    int probable = levelThreshold("probable", 55);
                    int suspect = levelThreshold("suspect", 35);
                    int watch = levelThreshold("watch", 18);
                    int i = 1;
                    ps.setInt(i++, points);
                    ps.setInt(i++, points); ps.setInt(i++, confirmed);
                    ps.setInt(i++, points); ps.setInt(i++, probable);
                    ps.setInt(i++, points); ps.setInt(i++, suspect);
                    ps.setInt(i++, points); ps.setInt(i++, watch);
                    ps.setInt(i++, signal == RelationSignal.PROXIMITY ? proximitySeconds() : 1);
                    ps.setLong(i++, now);
                    ps.setString(i++, signal.name());
                    if (worldId == 0) ps.setNull(i++, java.sql.Types.INTEGER); else ps.setInt(i++, worldId);
                    ps.setInt(i++, x);
                    ps.setInt(i++, y);
                    ps.setInt(i++, z);
                    ps.setString(i++, truncate(detail, 180));
                    ps.setString(i, pairKey);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO relation_evidence(pair_key, t, signal, points, world_id, x, y, z, detail) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, pairKey);
                    ps.setLong(2, now);
                    ps.setString(3, signal.name());
                    ps.setInt(4, points);
                    if (worldId == 0) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, worldId);
                    ps.setInt(6, x);
                    ps.setInt(7, y);
                    ps.setInt(8, z);
                    ps.setString(9, truncate(detail, 240));
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Relationship signal failed: " + e.getMessage());
        }
    }

    private void ensurePair(int aId, int bId, long now) throws SQLException {
        try (Connection c = plugin.db().writer()) {
            ensurePair(c, pairKey(Math.min(aId, bId), Math.max(aId, bId)), Math.min(aId, bId), Math.max(aId, bId), now);
        }
    }

    private void ensurePair(Connection c, String pairKey, int lowId, int highId, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO relation_pairs(pair_key, player_low, player_high, first_seen, last_seen) VALUES (?,?,?,?,?)")) {
            ps.setString(1, pairKey);
            ps.setInt(2, lowId);
            ps.setInt(3, highId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    private void sampleProximity() {
        if (!enabled()) return;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        double radius = Math.max(4.0, plugin.getConfig().getDouble("relationships.proximity.radius-blocks", 18.0));
        double radiusSq = radius * radius;
        for (int i = 0; i < players.size(); i++) {
            Player a = players.get(i);
            if (!a.isOnline() || a.hasPermission("smplogger.bypass")) continue;
            for (int j = i + 1; j < players.size(); j++) {
                Player b = players.get(j);
                if (!b.isOnline() || b.hasPermission("smplogger.bypass")) continue;
                if (a.getWorld() != b.getWorld()) continue;
                Location loc = a.getLocation();
                if (ignoredLocation(loc)) continue;
                if (loc.distanceSquared(b.getLocation()) > radiusSq) continue;
                if (recentPvp(a.getUniqueId(), b.getUniqueId())) continue;
                int points = plugin.getConfig().getInt("relationships.points.proximity", RelationSignal.PROXIMITY.defaultPoints());
                recordSignal(a.getUniqueId(), b.getUniqueId(), RelationSignal.PROXIMITY, points, loc,
                        "near for sampled interval within " + Math.round(radius) + " blocks",
                        "proximity", proximitySeconds() * 1000L);
            }
        }
    }

    private boolean enabled() {
        return configuredEnabled() && !lobbyBlocked() && !runtimePaused();
    }

    private boolean configuredEnabled() {
        return plugin.getConfig().getBoolean("relationships.enabled", true);
    }

    private boolean runtimePaused() {
        long until = pausedUntilMs;
        if (until == 0L) return false;
        if (until != Long.MAX_VALUE && System.currentTimeMillis() >= until) {
            resume();
            return false;
        }
        return true;
    }

    private boolean lobbyBlocked() {
        if (!plugin.getConfig().getBoolean("relationships.disable-on-lobby", true)) return false;
        String identity = serverIdentity().toLowerCase(Locale.ROOT);
        List<String> lobbyNames = plugin.getConfig().getStringList("relationships.lobby-server-names");
        if (lobbyNames.isEmpty()) lobbyNames = List.of("lobby", "hub");
        for (String name : lobbyNames) {
            if (!name.isBlank() && identity.equals(name.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean recentPvp(UUID a, UUID b) {
        Long last = pvpPairs.get(uuidPair(a, b));
        return last != null && System.currentTimeMillis() - last < Math.max(30_000L,
                plugin.getConfig().getLong("relationships.pvp-grace-seconds", 120L) * 1000L);
    }

    private boolean ignoredLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String world = loc.getWorld().getName().toLowerCase(Locale.ROOT);
        for (String ignored : plugin.getConfig().getStringList("relationships.ignored-world-names")) {
            if (!ignored.isBlank() && world.equals(ignored.toLowerCase(Locale.ROOT))) return true;
        }
        for (String suffix : plugin.getConfig().getStringList("relationships.ignored-world-suffixes")) {
            if (!suffix.isBlank() && world.endsWith(suffix.toLowerCase(Locale.ROOT))) return true;
        }
        double spawnRadius = plugin.getConfig().getDouble("relationships.spawn-safe-radius", 96.0);
        if (spawnRadius <= 0) return false;
        Location spawn = loc.getWorld().getSpawnLocation();
        return spawn.getWorld() == loc.getWorld() && spawn.distanceSquared(loc) <= spawnRadius * spawnRadius;
    }

    private boolean scanWorldAllowed(String world) {
        String lower = world.toLowerCase(Locale.ROOT);
        for (String ignored : plugin.getConfig().getStringList("relationships.home-scan.ignored-world-names")) {
            if (!ignored.isBlank() && lower.equals(ignored.toLowerCase(Locale.ROOT))) return false;
        }
        for (String suffix : plugin.getConfig().getStringList("relationships.home-scan.ignored-world-suffixes")) {
            if (!suffix.isBlank() && lower.endsWith(suffix.toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    private boolean isTrackedContainer(Material material) {
        String name = material.name();
        return name.endsWith("CHEST") || name.endsWith("SHULKER_BOX") || name.equals("BARREL")
                || name.equals("FURNACE") || name.equals("BLAST_FURNACE") || name.equals("SMOKER")
                || name.equals("BREWING_STAND") || name.equals("HOPPER") || name.equals("DISPENSER")
                || name.equals("DROPPER");
    }

    private boolean isFurnaceLike(Material material) {
        String name = material.name();
        return name.equals("FURNACE") || name.equals("BLAST_FURNACE") || name.equals("SMOKER")
                || name.equals("BREWING_STAND");
    }

    private List<HomePoint> loadHomes() {
        List<HomePoint> homes = new ArrayList<>();
        File dbFile = coreDbFile();
        if (!dbFile.exists()) {
            plugin.getLogger().warning("Core DB not found for relationship home scan: " + dbFile.getAbsolutePath());
            return homes;
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT h.uuid, h.slot, COALESCE(h.server, 'survival'), h.world, h.x, h.y, h.z, COALESCE(p.name, h.uuid) "
                     + "FROM homes h LEFT JOIN players p ON p.uuid = h.uuid")) {
            while (rs.next()) {
                homes.add(new HomePoint(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getString(3),
                        rs.getString(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getString(8)));
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().warning("Relationship home scan failed: " + e.getMessage());
        }
        return homes;
    }

    private Location toLoadedLocation(HomePoint home) {
        World world = Bukkit.getWorld(home.world);
        if (world == null) return null;
        return new Location(world, home.x, home.y, home.z);
    }

    private File coreDbFile() {
        String configured = plugin.getConfig().getString("relationships.core-db-file", "../shared-data/smp.db");
        File file = new File(configured);
        if (file.isAbsolute()) return file;
        return new File(plugin.getServer().getWorldContainer(), configured);
    }

    private int levelThreshold(String key, int fallback) {
        return plugin.getConfig().getInt("relationships.levels." + key, fallback);
    }

    private long containerCooldownMs() {
        return Math.max(30L, plugin.getConfig().getLong("relationships.shared-container.cooldown-seconds", 600L)) * 1000L;
    }

    private long tradeCooldownMs(RelationSignal signal) {
        if (signal == RelationSignal.CHEST_HANDOFF) return 5_000L;
        return Math.max(3L, plugin.getConfig().getLong("relationships.trade.cooldown-seconds", 15L)) * 1000L;
    }

    private long combatAssistWindowMs() {
        return Math.max(5L, plugin.getConfig().getLong("relationships.combat-assist.window-seconds", 20L)) * 1000L;
    }

    private int proximitySeconds() {
        return Math.max(5, plugin.getConfig().getInt("relationships.proximity.sample-seconds", 15));
    }

    private static String pairKey(int a, int b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    private static String uuidPair(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    private static long locKey(Location l) {
        long w = l.getWorld() == null ? 0 : l.getWorld().getName().hashCode();
        long x = l.getBlockX() & 0x3FFFFFFL;
        long y = l.getBlockY() & 0xFFFL;
        long z = l.getBlockZ() & 0x3FFFFFFL;
        return (w << 48) ^ (x << 22) ^ (z << 4) ^ y;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private String knownName(UUID uuid) {
        PlayerDict.Entry entry = plugin.players().byUuid(uuid);
        return entry == null ? uuid.toString() : entry.name();
    }

    private record ContainerUse(UUID player, long timeMs) {}
    private record HomePoint(UUID uuid, int slot, String server, String world, double x, double y, double z, String name) {}

    private final class TeamResolver {
        private final SMPLogger plugin;
        private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

        private TeamResolver(SMPLogger plugin) {
            this.plugin = plugin;
        }

        private String teamOf(UUID uuid) {
            long now = System.currentTimeMillis();
            CacheEntry cached = cache.get(uuid);
            if (cached != null && now - cached.loadedAt < 30_000L) return cached.teamId;
            File dbFile = coreDbFile();
            if (!dbFile.exists()) return null;
            String team = null;
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                 PreparedStatement ps = c.prepareStatement("SELECT team_id FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) team = rs.getString(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Relationship team lookup failed: " + e.getMessage());
            }
            cache.put(uuid, new CacheEntry(team, now));
            return team;
        }

        private record CacheEntry(String teamId, long loadedAt) {}
    }
}
