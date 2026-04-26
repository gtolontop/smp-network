package fr.smp.core.skins;

import com.destroystokyo.paper.profile.ProfileProperty;
import fr.smp.core.SMPCore;
import fr.smp.core.auth.AuthAccount;
import fr.smp.core.auth.AuthSession;
import fr.smp.core.npc.SkinFetcher;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SkinManager implements Listener {

    public enum Mode {
        DEFAULT,
        RANDOM,
        TAKEN;

        public String dbValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode fromDb(String raw) {
            if (raw == null || raw.isBlank()) {
                return DEFAULT;
            }
            try {
                return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DEFAULT;
            }
        }
    }

    public record SkinRecord(Mode mode, String ownerName, String value, String signature, long updatedAt) {
        public boolean isUsable() {
            return ownerName != null && !ownerName.isBlank()
                    && value != null && !value.isBlank();
        }
    }

    public record OperationResult(boolean success, String targetName, String message, SkinRecord skin, boolean appliedNow) {}

    public record InfoResult(String targetName, boolean premiumOnly, SkinRecord currentSkin, String defaultOwnerPreview) {}

    private static final List<String> DEFAULT_SKIN_POOL = List.of(
            "jeb_",
            "Dinnerbone",
            "Notch",
            "deadmau5",
            "CaptainSparklez",
            "DanTDM",
            "Technoblade",
            "Dream",
            "Sapnap",
            "GeorgeNotFound",
            "TommyInnit",
            "Skeppy",
            "BadBoyHalo",
            "Ranboo",
            "Purpled",
            "fruitberries",
            "Grian",
            "MumboJumbo",
            "GoodTimesWithScar",
            "Smallishbeans",
            "Xisuma",
            "Keralis",
            "BdoubleO100",
            "Etho",
            "AntVenom",
            "Quig",
            "Illumina",
            "PeteZahHutt",
            "TapL",
            "Vikkstar123",
            "WadZee",
            "SB737",
            "Shadoune666",
            "Fundy",
            "Tubbo",
            "Philza",
            "Wallibear",
            "FireBreathMan",
            "Calvin",
            "MHF_Alex",
            "MHF_Steve"
    );

    private final SMPCore plugin;
    private final Database db;
    private final Random random = new Random();
    private final Map<String, Object> nameLocks = new ConcurrentHashMap<>();
    private BukkitTask startupApplyTask;

    public SkinManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        startupApplyTask = Bukkit.getScheduler().runTaskLater(plugin, this::reapplyEligibleOnlinePlayers, 40L);
    }

    public void stop() {
        if (startupApplyTask != null) {
            startupApplyTask.cancel();
            startupApplyTask = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || isPremiumSession(player)) {
                return;
            }
            applyStoredOrDefault(player);
        }, 2L);
    }

    public void applyStoredOrDefault(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        loadOrCreateAsync(player.getName(), skin -> {
            if (skin == null || !skin.isUsable() || !player.isOnline()) {
                return;
            }
            applySkin(player, skin);
        });
    }

    public void lookupInfoAsync(String targetName, Consumer<InfoResult> callback) {
        String normalized = normalizePlayerName(targetName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String nameLower = normalized.toLowerCase(Locale.ROOT);
            AuthAccount account = plugin.auth() != null ? plugin.auth().loadBlocking(nameLower) : null;
            SkinRecord current = loadBlocking(nameLower);
            InfoResult result = new InfoResult(
                    normalized,
                    account != null && account.isPremiumOnly(),
                    current,
                    defaultOwnerFor(nameLower));
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    public void setRandomAsync(String targetName, Consumer<OperationResult> callback) {
        String normalized = normalizePlayerName(targetName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OperationResult result = modifySkin(normalized, Mode.RANDOM, null);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    public void resetAsync(String targetName, Consumer<OperationResult> callback) {
        String normalized = normalizePlayerName(targetName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OperationResult result = modifySkin(normalized, Mode.DEFAULT, null);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    public void takeAsync(String targetName, String premiumName, Consumer<OperationResult> callback) {
        String normalizedTarget = normalizePlayerName(targetName);
        String normalizedOwner = normalizePlayerName(premiumName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OperationResult result = modifySkin(normalizedTarget, Mode.TAKEN, normalizedOwner);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    public Player findOnlinePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    public boolean isPremiumSession(Player player) {
        AuthSession session = currentSession(player);
        return session != null && session.premium();
    }

    public static boolean isValidMinecraftName(String name) {
        return name != null
                && !name.isBlank()
                && name.length() <= 16
                && name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
    }

    private void reapplyEligibleOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            AuthSession session = currentSession(player);
            if (session == null || session.premium()) {
                continue;
            }
            applyStoredOrDefault(player);
        }
    }

    private void loadOrCreateAsync(String targetName, Consumer<SkinRecord> callback) {
        String normalized = normalizePlayerName(targetName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SkinRecord record;
            try {
                record = withNameLock(normalized.toLowerCase(Locale.ROOT), () -> resolveStoredOrDefaultBlocking(normalized));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to resolve skin for " + normalized + ": " + ex.getMessage());
                record = null;
            }
            SkinRecord finalRecord = record;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalRecord));
        });
    }

    private OperationResult modifySkin(String targetName, Mode mode, String premiumName) {
        String nameLower = targetName.toLowerCase(Locale.ROOT);
        try {
            return withNameLock(nameLower, () -> {
                AuthAccount account = plugin.auth() != null ? plugin.auth().loadBlocking(nameLower) : null;
                if (account != null && account.isPremiumOnly()) {
                    return new OperationResult(false, targetName,
                            "This name is reserved by a premium account.", null, false);
                }

                SkinRecord existing = loadBlocking(nameLower);
                SkinFetcher.Skin fetched = switch (mode) {
                    case DEFAULT -> resolveDefaultSkin(nameLower);
                    case RANDOM -> resolveRandomSkin(existing != null ? existing.ownerName() : null);
                    case TAKEN -> resolveTakenSkin(premiumName);
                };

                if (fetched == null) {
                    String detail = mode == Mode.TAKEN
                            ? "Unable to fetch the premium skin for " + premiumName + "."
                            : "Unable to fetch a Minecraft skin right now.";
                    return new OperationResult(false, targetName, detail, existing, false);
                }

                SkinRecord stored = new SkinRecord(
                        mode,
                        fetched.ownerName(),
                        fetched.value(),
                        fetched.signature(),
                        Instant.now().getEpochSecond());
                saveBlocking(nameLower, stored);
                return new OperationResult(true, targetName, successMessage(mode, stored.ownerName()), stored, false);
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to update skin for " + targetName + ": " + ex.getMessage());
            return new OperationResult(false, targetName, "Internal error while updating the skin.", null, false);
        }
    }

    private String successMessage(Mode mode, String ownerName) {
        return switch (mode) {
            case DEFAULT -> "Restored default cracked skin from " + ownerName + ".";
            case RANDOM -> "Applied a random skin from " + ownerName + ".";
            case TAKEN -> "Applied the skin of " + ownerName + ".";
        };
    }

    private SkinRecord resolveStoredOrDefaultBlocking(String targetName) {
        String nameLower = targetName.toLowerCase(Locale.ROOT);
        SkinRecord existing = loadBlocking(nameLower);
        if (existing != null && existing.isUsable()) {
            return existing;
        }
        SkinFetcher.Skin fetched = resolveDefaultSkin(nameLower);
        if (fetched == null) {
            return null;
        }
        SkinRecord created = new SkinRecord(
                Mode.DEFAULT,
                fetched.ownerName(),
                fetched.value(),
                fetched.signature(),
                Instant.now().getEpochSecond());
        saveBlocking(nameLower, created);
        return created;
    }

    private SkinFetcher.Skin resolveDefaultSkin(String nameLower) {
        int start = Math.floorMod(nameLower.hashCode(), DEFAULT_SKIN_POOL.size());
        List<String> candidates = new ArrayList<>(DEFAULT_SKIN_POOL.size());
        for (int offset = 0; offset < DEFAULT_SKIN_POOL.size(); offset++) {
            candidates.add(DEFAULT_SKIN_POOL.get((start + offset) % DEFAULT_SKIN_POOL.size()));
        }
        return fetchFirstAvailable(candidates);
    }

    private SkinFetcher.Skin resolveRandomSkin(String currentOwner) {
        List<String> candidates = new ArrayList<>(DEFAULT_SKIN_POOL);
        if (currentOwner != null && !currentOwner.isBlank()) {
            candidates.removeIf(name -> name.equalsIgnoreCase(currentOwner));
        }
        if (candidates.isEmpty()) {
            candidates.addAll(DEFAULT_SKIN_POOL);
        }
        Collections.shuffle(candidates, random);
        return fetchFirstAvailable(candidates);
    }

    private SkinFetcher.Skin resolveTakenSkin(String premiumName) {
        if (!isValidMinecraftName(premiumName)) {
            return null;
        }
        return fetchFirstAvailable(List.of(premiumName));
    }

    private SkinFetcher.Skin fetchFirstAvailable(List<String> candidates) {
        for (String candidate : candidates) {
            try {
                SkinFetcher.Skin fetched = SkinFetcher.fetch(candidate).join();
                if (fetched != null && fetched.value() != null && !fetched.value().isBlank()) {
                    return fetched;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String defaultOwnerFor(String nameLower) {
        return DEFAULT_SKIN_POOL.get(Math.floorMod(nameLower.hashCode(), DEFAULT_SKIN_POOL.size()));
    }

    private SkinRecord loadBlocking(String nameLower) {
        try (Connection connection = db.get();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT mode, skin_owner, skin_value, skin_signature, updated_at "
                             + "FROM player_skins WHERE name_lower = ?")) {
            statement.setString(1, nameLower);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                SkinRecord record = new SkinRecord(
                        Mode.fromDb(rs.getString("mode")),
                        rs.getString("skin_owner"),
                        rs.getString("skin_value"),
                        rs.getString("skin_signature"),
                        rs.getLong("updated_at"));
                return record.isUsable() ? record : null;
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to load skin for " + nameLower + ": " + ex.getMessage());
            return null;
        }
    }

    private void saveBlocking(String nameLower, SkinRecord skin) {
        try (Connection connection = db.get();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO player_skins(name_lower, mode, skin_owner, skin_value, skin_signature, updated_at) "
                             + "VALUES(?,?,?,?,?,?) "
                             + "ON CONFLICT(name_lower) DO UPDATE SET "
                             + "mode=excluded.mode, "
                             + "skin_owner=excluded.skin_owner, "
                             + "skin_value=excluded.skin_value, "
                             + "skin_signature=excluded.skin_signature, "
                             + "updated_at=excluded.updated_at")) {
            statement.setString(1, nameLower);
            statement.setString(2, skin.mode().dbValue());
            statement.setString(3, skin.ownerName());
            statement.setString(4, skin.value());
            if (skin.signature() == null || skin.signature().isBlank()) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, skin.signature());
            }
            statement.setLong(6, skin.updatedAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to save skin for " + nameLower + ": " + ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private boolean applySkin(Player player, SkinRecord skin) {
        try {
            var profile = player.getPlayerProfile().clone();
            ProfileProperty current = profile.getProperties().stream()
                    .filter(property -> "textures".equals(property.getName()))
                    .findFirst()
                    .orElse(null);
            if (current != null
                    && Objects.equals(current.getValue(), skin.value())
                    && Objects.equals(current.getSignature(), skin.signature())) {
                return false;
            }
            profile.removeProperty("textures");
            if (skin.signature() != null && !skin.signature().isBlank()) {
                profile.setProperty(new ProfileProperty("textures", skin.value(), skin.signature()));
            } else {
                profile.setProperty(new ProfileProperty("textures", skin.value()));
            }
            player.setPlayerProfile(profile);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Failed to apply skin to " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    public OperationResult applyNowIfPossible(OperationResult result) {
        if (result == null || !result.success() || result.skin() == null) {
            return result;
        }
        Player player = findOnlinePlayer(result.targetName());
        AuthSession session = currentSession(player);
        if (player == null || session == null || session.premium()) {
            return result;
        }
        boolean appliedNow = applySkin(player, result.skin());
        return new OperationResult(result.success(), result.targetName(), result.message(), result.skin(), appliedNow);
    }

    private String normalizePlayerName(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private AuthSession currentSession(Player player) {
        if (player == null || plugin.auth() == null) {
            return null;
        }
        return plugin.auth().session(player.getUniqueId());
    }

    private Object lockFor(String nameLower) {
        return nameLocks.computeIfAbsent(nameLower, ignored -> new Object());
    }

    private <T> T withNameLock(String nameLower, LockedSupplier<T> supplier) throws Exception {
        synchronized (lockFor(nameLower)) {
            return supplier.get();
        }
    }

    @FunctionalInterface
    private interface LockedSupplier<T> {
        T get() throws Exception;
    }
}
