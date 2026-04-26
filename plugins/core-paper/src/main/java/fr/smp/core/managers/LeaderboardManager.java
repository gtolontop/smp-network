package fr.smp.core.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerDataManager;
import fr.smp.core.storage.Database;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LeaderboardManager {

    public enum Category {
        MONEY("money", "Baltop", Material.GOLD_INGOT, Material.YELLOW_STAINED_GLASS_PANE, "#f6d365", "#fda085"),
        PLAYTIME("playtime", "Playtime", Material.CLOCK, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "#84fab0", "#8fd3f4"),
        KILLS("kills", "Kills", Material.DIAMOND_SWORD, Material.RED_STAINED_GLASS_PANE, "#f85032", "#e73827"),
        DEATHS("deaths", "Deaths", Material.SKELETON_SKULL, Material.GRAY_STAINED_GLASS_PANE, "#cfd9df", "#e2ebf0"),
        DISTANCE("distance", "Distance", Material.ELYTRA, Material.CYAN_STAINED_GLASS_PANE, "#43cea2", "#185a9d");

        private final String key;
        private final String display;
        private final Material icon;
        private final Material border;
        private final String gradientStart;
        private final String gradientEnd;

        Category(String key, String display, Material icon, Material border, String gradientStart, String gradientEnd) {
            this.key = key;
            this.display = display;
            this.icon = icon;
            this.border = border;
            this.gradientStart = gradientStart;
            this.gradientEnd = gradientEnd;
        }

        public String key() { return key; }
        public String display() { return display; }
        public Material icon() { return icon; }
        public Material border() { return border; }

        public String titleMiniMessage() {
            return "<gradient:" + gradientStart + ":" + gradientEnd + "><bold>" + display + "</bold></gradient>";
        }

        public static Category parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "money", "bal", "balance", "wealth", "rich", "baltop", "moneytop" -> MONEY;
                case "playtime", "pt", "time", "temps" -> PLAYTIME;
                case "kills", "kill", "frags" -> KILLS;
                case "deaths", "death", "morts", "mort" -> DEATHS;
                case "distance", "dist", "travel", "parcouru", "parcourue" -> DISTANCE;
                default -> null;
            };
        }
    }

    public enum Scope {
        SOLO("solo", "Solo", Material.PLAYER_HEAD),
        TEAM("team", "Team", Material.BLUE_BANNER);

        private final String key;
        private final String display;
        private final Material icon;

        Scope(String key, String display, Material icon) {
            this.key = key;
            this.display = display;
            this.icon = icon;
        }

        public String key() { return key; }
        public String display() { return display; }
        public Material icon() { return icon; }

        public static Scope parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "solo", "player", "players", "joueur", "joueurs" -> SOLO;
                case "team", "teams", "guild", "guilds", "equipe", "équipe", "equipes", "équipes" -> TEAM;
                default -> null;
            };
        }
    }

    public record Entry(
            String id,
            String sortName,
            String displayName,
            Material iconMaterial,
            UUID iconPlayer,
            double sortValue,
            String valueDisplay,
            List<String> detailLines
    ) {
        public Entry {
            detailLines = List.copyOf(detailLines);
        }
    }

    public record Highlight(int rank, String displayName, String valueDisplay) {}

    public record Result(List<Entry> entries, Highlight highlight) {
        public Result {
            entries = List.copyOf(entries);
        }
    }

    private record PlayerSnapshot(
            UUID uuid,
            String name,
            String teamId,
            double money,
            long playtimeSec,
            int kills,
            int deaths
    ) {}

    private record TeamSnapshot(
            String id,
            String tag,
            String name,
            String color,
            UUID owner,
            double balance
    ) {}

    private record MemberHighlight(String name, double value, String valueDisplay) {}

    private static final List<String> DISTANCE_KEYS = List.of(
            "minecraft:walk_one_cm",
            "minecraft:crouch_one_cm",
            "minecraft:sprint_one_cm",
            "minecraft:swim_one_cm",
            "minecraft:walk_under_water_one_cm",
            "minecraft:walk_on_water_one_cm",
            "minecraft:climb_one_cm",
            "minecraft:fly_one_cm",
            "minecraft:aviate_one_cm",
            "minecraft:fall_one_cm",
            "minecraft:boat_one_cm",
            "minecraft:minecart_one_cm",
            "minecraft:horse_one_cm",
            "minecraft:pig_one_cm",
            "minecraft:strider_one_cm",
            "minecraft:camel_one_cm"
    );

    private static final Map<String, Material> TEAM_BANNERS = new LinkedHashMap<>();
    static {
        TEAM_BANNERS.put("<white>", Material.WHITE_BANNER);
        TEAM_BANNERS.put("<gray>", Material.LIGHT_GRAY_BANNER);
        TEAM_BANNERS.put("<dark_gray>", Material.GRAY_BANNER);
        TEAM_BANNERS.put("<black>", Material.BLACK_BANNER);
        TEAM_BANNERS.put("<red>", Material.RED_BANNER);
        TEAM_BANNERS.put("<dark_red>", Material.BROWN_BANNER);
        TEAM_BANNERS.put("<gold>", Material.ORANGE_BANNER);
        TEAM_BANNERS.put("<yellow>", Material.YELLOW_BANNER);
        TEAM_BANNERS.put("<green>", Material.LIME_BANNER);
        TEAM_BANNERS.put("<dark_green>", Material.GREEN_BANNER);
        TEAM_BANNERS.put("<aqua>", Material.LIGHT_BLUE_BANNER);
        TEAM_BANNERS.put("<dark_aqua>", Material.CYAN_BANNER);
        TEAM_BANNERS.put("<blue>", Material.BLUE_BANNER);
        TEAM_BANNERS.put("<light_purple>", Material.MAGENTA_BANNER);
        TEAM_BANNERS.put("<dark_purple>", Material.PURPLE_BANNER);
        TEAM_BANNERS.put("<pink>", Material.PINK_BANNER);
    }

    private static final long DISTANCE_CACHE_TTL_MS = 15_000L;

    private final SMPCore plugin;
    private final Database db;
    private final PlayerDataManager players;
    private final Object distanceCacheLock = new Object();

    private volatile Map<UUID, Long> cachedDistances = Map.of();
    private volatile long distanceCacheUntilMs = 0L;

    public LeaderboardManager(SMPCore plugin, Database db, PlayerDataManager players) {
        this.plugin = plugin;
        this.db = db;
        this.players = players;
    }

    public Result ranking(Category category, Scope scope, UUID viewerUuid) {
        players.saveAll();
        return switch (scope) {
            case SOLO -> buildSoloRanking(category, viewerUuid);
            case TEAM -> buildTeamRanking(category, viewerUuid);
        };
    }

    private Result buildSoloRanking(Category category, UUID viewerUuid) {
        List<PlayerSnapshot> playerRows = loadPlayers();
        Map<String, TeamSnapshot> teamsById = loadTeams();
        Map<UUID, Long> distances = category == Category.DISTANCE ? loadDistances() : Map.of();

        List<Entry> entries = new ArrayList<>(playerRows.size());
        for (PlayerSnapshot player : playerRows) {
            double value = baseValue(player, category, distances);
            TeamSnapshot team = player.teamId() == null ? null : teamsById.get(player.teamId());
            entries.add(new Entry(
                    player.uuid().toString(),
                    safeName(player.name(), player.uuid()),
                    "<white>" + safeName(player.name(), player.uuid()) + "</white>",
                    Material.PLAYER_HEAD,
                    player.uuid(),
                    value,
                    formatValue(category, value),
                    soloDetailLines(category, player, team, distances)
            ));
        }

        entries.sort(entryComparator());
        Highlight highlight = highlight(entries, viewerUuid == null ? null : viewerUuid.toString());
        return new Result(entries, highlight);
    }

    private Result buildTeamRanking(Category category, UUID viewerUuid) {
        List<PlayerSnapshot> playerRows = loadPlayers();
        Map<UUID, PlayerSnapshot> playersByUuid = new HashMap<>();
        for (PlayerSnapshot player : playerRows) {
            playersByUuid.put(player.uuid(), player);
        }

        Map<String, TeamSnapshot> teamsById = loadTeams();
        Map<String, List<UUID>> membersByTeam = loadTeamMembers();
        Map<UUID, Long> distances = category == Category.DISTANCE ? loadDistances() : Map.of();

        List<Entry> entries = new ArrayList<>();
        for (TeamSnapshot team : teamsById.values()) {
            List<UUID> memberIds = membersByTeam.getOrDefault(team.id(), List.of());
            if (memberIds.isEmpty() && team.balance() <= 0D) {
                continue;
            }

            double memberTotal = 0D;
            double total = 0D;
            MemberHighlight spotlight = null;

            for (UUID memberId : memberIds) {
                PlayerSnapshot player = playersByUuid.get(memberId);
                if (player == null) {
                    player = new PlayerSnapshot(memberId, safeOfflineName(memberId), team.id(), 0D, 0L, 0, 0);
                }

                double score = baseValue(player, category, distances);
                memberTotal += switch (category) {
                    case MONEY -> player.money();
                    default -> score;
                };
                total += score;

                if (spotlight == null || score > spotlight.value()) {
                    spotlight = new MemberHighlight(
                            safeName(player.name(), player.uuid()),
                            score,
                            formatValue(category, score)
                    );
                }
            }

            if (category == Category.MONEY) {
                total = memberTotal + team.balance();
            }

            entries.add(new Entry(
                    team.id(),
                    team.tag() + " " + team.name(),
                    formatTeamDisplay(team),
                    bannerForTeam(team.color()),
                    null,
                    total,
                    formatValue(category, total),
                    teamDetailLines(category, team, memberIds.size(), memberTotal, spotlight)
            ));
        }

        entries.sort(entryComparator());

        String viewerTeamId = null;
        if (viewerUuid != null) {
            PlayerSnapshot viewer = playersByUuid.get(viewerUuid);
            if (viewer != null) viewerTeamId = viewer.teamId();
        }
        Highlight highlight = highlight(entries, viewerTeamId);
        return new Result(entries, highlight);
    }

    private List<PlayerSnapshot> loadPlayers() {
        List<PlayerSnapshot> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, team_id, money, playtime_sec, kills, deaths FROM players")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(rs.getString(1));
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    out.add(new PlayerSnapshot(
                            uuid,
                            rs.getString(2),
                            rs.getString(3),
                            rs.getDouble(4),
                            rs.getLong(5),
                            rs.getInt(6),
                            rs.getInt(7)
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("leaderboard.players: " + e.getMessage());
        }
        return out;
    }

    private Map<String, TeamSnapshot> loadTeams() {
        Map<String, TeamSnapshot> out = new LinkedHashMap<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, tag, name, color, owner, balance FROM teams")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID owner;
                    try {
                        owner = UUID.fromString(rs.getString(5));
                    } catch (IllegalArgumentException ignored) {
                        owner = null;
                    }
                    TeamSnapshot team = new TeamSnapshot(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4) == null ? "<white>" : rs.getString(4),
                            owner,
                            rs.getDouble(6)
                    );
                    out.put(team.id(), team);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("leaderboard.teams: " + e.getMessage());
        }
        return out;
    }

    private Map<String, List<UUID>> loadTeamMembers() {
        Map<String, List<UUID>> out = new HashMap<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT team_id, uuid FROM team_members ORDER BY joined_at, uuid")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(rs.getString(2));
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    out.computeIfAbsent(rs.getString(1), ignored -> new ArrayList<>()).add(uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("leaderboard.teamMembers: " + e.getMessage());
        }
        return out;
    }

    private Map<UUID, Long> loadDistances() {
        long now = System.currentTimeMillis();
        if (now < distanceCacheUntilMs) {
            return cachedDistances;
        }

        synchronized (distanceCacheLock) {
            now = System.currentTimeMillis();
            if (now < distanceCacheUntilMs) {
                return cachedDistances;
            }

            File statsDir = resolveStatsDirectory();
            if (statsDir == null) {
                cachedDistances = Map.of();
                distanceCacheUntilMs = now + DISTANCE_CACHE_TTL_MS;
                return cachedDistances;
            }

            Map<UUID, Long> out = new HashMap<>();
            File[] files = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    UUID uuid = uuidFromStatsFile(file.getName());
                    if (uuid == null) continue;
                    out.put(uuid, readDistance(file));
                }
            }

            cachedDistances = Map.copyOf(out);
            distanceCacheUntilMs = now + DISTANCE_CACHE_TTL_MS;
            return cachedDistances;
        }
    }

    private File resolveStatsDirectory() {
        List<File> candidates = new ArrayList<>();

        World mainWorld = plugin.resolveWorld(null, World.Environment.NORMAL);
        if (mainWorld != null) {
            addStatsCandidates(candidates, mainWorld.getWorldFolder());
        }
        for (World world : Bukkit.getWorlds()) {
            addStatsCandidates(candidates, world.getWorldFolder());
        }

        addStatsCandidates(candidates, new File("world"));
        addStatsCandidates(candidates, new File("../survival/world"));
        addStatsCandidates(candidates, new File("survival/world"));

        for (File candidate : candidates) {
            if (candidate.isDirectory()) {
                return candidate.getAbsoluteFile();
            }
        }
        return null;
    }

    private void addStatsCandidates(List<File> candidates, File worldFolder) {
        candidates.add(new File(worldFolder, "players/stats"));
        candidates.add(new File(worldFolder, "stats"));
    }

    private UUID uuidFromStatsFile(String fileName) {
        if (fileName == null || !fileName.endsWith(".json")) return null;
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - 5));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private long readDistance(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject stats = asObject(root.get("stats"));
            JsonObject custom = stats == null ? null : asObject(stats.get("minecraft:custom"));
            if (custom == null) return 0L;

            long total = 0L;
            for (String key : DISTANCE_KEYS) {
                JsonElement element = custom.get(key);
                if (element != null && element.isJsonPrimitive()) {
                    try {
                        total += element.getAsLong();
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed values and keep parsing the rest.
                    }
                }
            }
            return total;
        } catch (IOException | IllegalStateException e) {
            return 0L;
        }
    }

    private JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private double baseValue(PlayerSnapshot player, Category category, Map<UUID, Long> distances) {
        return switch (category) {
            case MONEY -> player.money();
            case PLAYTIME -> player.playtimeSec();
            case KILLS -> player.kills();
            case DEATHS -> player.deaths();
            case DISTANCE -> distances.getOrDefault(player.uuid(), 0L);
        };
    }

    private List<String> soloDetailLines(Category category, PlayerSnapshot player, TeamSnapshot team, Map<UUID, Long> distances) {
        List<String> lines = new ArrayList<>();
        switch (category) {
            case MONEY -> {
                lines.add(team == null
                        ? "<gray>Team: <white>Aucune</white></gray>"
                        : "<gray>Team: " + formatTeamDisplay(team) + "</gray>");
                lines.add("<gray>Playtime: <white>" + Msg.duration(player.playtimeSec()) + "</white></gray>");
            }
            case PLAYTIME -> {
                lines.add("<gray>Kills: <red>" + player.kills() + "</red> <dark_gray>•</dark_gray> Deaths: <white>" + player.deaths() + "</white></gray>");
                lines.add(team == null
                        ? "<gray>Team: <white>Aucune</white></gray>"
                        : "<gray>Team: " + formatTeamDisplay(team) + "</gray>");
            }
            case KILLS -> {
                lines.add("<gray>Deaths: <white>" + player.deaths() + "</white></gray>");
                lines.add("<gray>KD: <white>" + formatKd(player.kills(), player.deaths()) + "</white></gray>");
            }
            case DEATHS -> {
                lines.add("<gray>Kills: <white>" + player.kills() + "</white></gray>");
                lines.add("<gray>Playtime: <white>" + Msg.duration(player.playtimeSec()) + "</white></gray>");
            }
            case DISTANCE -> {
                lines.add("<gray>Playtime: <white>" + Msg.duration(player.playtimeSec()) + "</white></gray>");
                lines.add(team == null
                        ? "<gray>Team: <white>Aucune</white></gray>"
                        : "<gray>Team: " + formatTeamDisplay(team) + "</gray>");
                lines.add("<gray>Total lu: <white>" + formatDistance(distances.getOrDefault(player.uuid(), 0L)) + "</white></gray>");
            }
        }
        return lines;
    }

    private List<String> teamDetailLines(Category category, TeamSnapshot team, int members, double memberTotal, MemberHighlight spotlight) {
        List<String> lines = new ArrayList<>();
        switch (category) {
            case MONEY -> {
                lines.add("<gray>Fortune des joueurs: <gold>$" + Msg.money(memberTotal) + "</gold></gray>");
                lines.add("<gray>Banque de team: <gold>$" + Msg.money(team.balance()) + "</gold></gray>");
                lines.add(spotlight == null
                        ? "<gray>Membres: <white>" + members + "</white></gray>"
                        : "<gray>Le plus riche: <white>" + spotlight.name() + "</white> <dark_gray>•</dark_gray> " + spotlight.valueDisplay() + "</gray>");
            }
            case PLAYTIME -> {
                lines.add("<gray>Membres: <white>" + members + "</white></gray>");
                if (spotlight != null) {
                    lines.add("<gray>No-life MVP: <white>" + spotlight.name() + "</white></gray>");
                    lines.add("<gray>Temps: " + spotlight.valueDisplay() + "</gray>");
                }
            }
            case KILLS -> {
                lines.add("<gray>Membres: <white>" + members + "</white></gray>");
                if (spotlight != null) {
                    lines.add("<gray>Carry: <white>" + spotlight.name() + "</white></gray>");
                    lines.add("<gray>Score: " + spotlight.valueDisplay() + "</gray>");
                }
            }
            case DEATHS -> {
                lines.add("<gray>Membres: <white>" + members + "</white></gray>");
                if (spotlight != null) {
                    lines.add("<gray>Le plus maudit: <white>" + spotlight.name() + "</white></gray>");
                    lines.add("<gray>Score: " + spotlight.valueDisplay() + "</gray>");
                }
            }
            case DISTANCE -> {
                lines.add("<gray>Membres: <white>" + members + "</white></gray>");
                if (spotlight != null) {
                    lines.add("<gray>Explorateur: <white>" + spotlight.name() + "</white></gray>");
                    lines.add("<gray>Distance: " + spotlight.valueDisplay() + "</gray>");
                }
            }
        }
        return lines;
    }

    private Highlight highlight(List<Entry> entries, String id) {
        if (id == null) return null;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (id.equalsIgnoreCase(entry.id())) {
                return new Highlight(i + 1, entry.displayName(), entry.valueDisplay());
            }
        }
        return null;
    }

    private Comparator<Entry> entryComparator() {
        return Comparator
                .comparingDouble(Entry::sortValue).reversed()
                .thenComparing(entry -> entry.sortName().toLowerCase(Locale.ROOT));
    }

    private String formatValue(Category category, double value) {
        return switch (category) {
            case MONEY -> "<green>$" + Msg.money(value) + "</green>";
            case PLAYTIME -> "<aqua>" + Msg.duration(Math.round(value)) + "</aqua>";
            case KILLS -> "<red>" + Math.round(value) + " kills</red>";
            case DEATHS -> "<gray>" + Math.round(value) + " deaths</gray>";
            case DISTANCE -> "<blue>" + formatDistance(Math.round(value)) + "</blue>";
        };
    }

    private String formatDistance(long centimeters) {
        double meters = centimeters / 100.0D;
        if (meters >= 1000.0D) {
            return String.format(Locale.US, "%.2f km", meters / 1000.0D);
        }
        return String.format(Locale.US, "%.0f m", meters);
    }

    private String formatKd(int kills, int deaths) {
        if (kills <= 0) return "0.00";
        if (deaths <= 0) return "Perfect";
        return String.format(Locale.US, "%.2f", kills / (double) deaths);
    }

    private String formatTeamDisplay(TeamSnapshot team) {
        return team.color() + "[" + team.tag() + "] " + team.name() + "<reset>";
    }

    private Material bannerForTeam(String color) {
        return TEAM_BANNERS.getOrDefault(color, Material.WHITE_BANNER);
    }

    private String safeName(String name, UUID uuid) {
        if (name != null && !name.isBlank()) return name;
        return uuid.toString().substring(0, 8);
    }

    private String safeOfflineName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return safeName(name, uuid);
    }
}
