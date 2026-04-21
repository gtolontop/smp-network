package fr.smp.core.auth;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central state for the authentication subsystem.
 *
 * Holds in-memory {@link AuthSession}s for online players and is the only
 * surface that touches the {@code auth_accounts} table. All DB operations are
 * dispatched off the main thread; callbacks are routed back through the Bukkit
 * scheduler so listeners and commands stay single-threaded.
 *
 * Premium detection is by inspection of the player's GameProfile:
 * Velocity-modern forwarding only sets the {@code textures} property after a
 * successful Mojang session handshake, which is impossible for a cracked
 * client when {@link fr.smp.auth} (the Velocity bridge) has called
 * forceOnlineMode() for that name.
 */
public final class AuthManager {

    /** Hard timeout — if the player hasn't authenticated by then, kick. */
    public static final long LOGIN_TIMEOUT_MS = 60_000L;
    /** After this many failed attempts (across reconnects), lock for {@link #LOCKOUT_MS}. */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_MS = 5 * 60_000L;
    /** Per-session immediate kick threshold — prevents one connection from spamming guesses. */
    public static final int SESSION_FAIL_KICK = 3;
    public static final int MIN_PASSWORD_LEN = 4;
    public static final int MAX_PASSWORD_LEN = 64;

    private final SMPCore plugin;
    private final Database db;
    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<>();
    private BukkitTask reminderTask;
    private BukkitTask timeoutTask;

    public AuthManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        // Reminder actionbar every 2s for unauthenticated players.
        reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickReminders, 40L, 40L);
        // Timeout sweep every 1s.
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimeouts, 20L, 20L);
    }

    public void stop() {
        if (reminderTask != null) reminderTask.cancel();
        if (timeoutTask != null) timeoutTask.cancel();
        reminderTask = null;
        timeoutTask = null;
        sessions.clear();
    }

    // ---------------------------------------------------------------- session lifecycle

    public AuthSession beginSession(Player player, boolean premium) {
        AuthSession s = new AuthSession(premium, System.currentTimeMillis(), player.getLocation().clone());
        sessions.put(player.getUniqueId(), s);
        return s;
    }

    public AuthSession session(UUID id) { return sessions.get(id); }

    public boolean isAuthenticated(Player player) {
        AuthSession s = sessions.get(player.getUniqueId());
        return s != null && s.isAuthenticated();
    }

    /** Returns true if no session exists yet (e.g. listener fires before join handler). Treat as unauthenticated. */
    public boolean isUnauthenticated(Player player) {
        AuthSession s = sessions.get(player.getUniqueId());
        return s == null || !s.isAuthenticated();
    }

    public void endSession(UUID id) {
        sessions.remove(id);
    }

    public void markAuthenticated(Player player, AuthAccount account) {
        AuthSession s = sessions.get(player.getUniqueId());
        if (s == null) return;
        s.setStage(AuthSession.Stage.AUTHENTICATED);
        s.resetFailed();
        AuthEffects.clear(player);
        plugin.logs().log(LogCategory.JOIN, player,
                "auth_ok premium=" + s.premium() + " account_premium=" + (account != null && account.premiumUuid() != null));

        // Run the post-join setup that JoinListener deferred while we waited
        // for auth (teleport / scoreboard / nametag / hunted refresh / etc.).
        if (plugin.joinListener() != null && plugin.players() != null) {
            var data = plugin.players().loadOrCreate(player.getUniqueId(), player.getName());
            try {
                plugin.joinListener().runJoinSetup(player, data);
            } catch (Throwable t) {
                plugin.getLogger().warning("Post-auth join setup failed for " + player.getName() + ": " + t.getMessage());
            }
        }
    }

    public void markAwaiting(Player player) {
        AuthSession s = sessions.get(player.getUniqueId());
        if (s == null) return;
        s.setStage(AuthSession.Stage.AWAITING);
        AuthEffects.apply(player);
    }

    // ---------------------------------------------------------------- DB I/O (async)

    /** Always called on a worker thread. Returns null if no row exists. */
    public AuthAccount loadBlocking(String nameLower) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name_lower, password_hash, premium_uuid, cracked_uuid, registered_at, " +
                     "       last_login, last_ip, failed_attempts, locked_until, must_rechange " +
                     "FROM auth_accounts WHERE name_lower = ?")) {
            ps.setString(1, nameLower);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new AuthAccount(
                        rs.getString("name_lower"),
                        rs.getString("password_hash"),
                        parseUuid(rs.getString("premium_uuid")),
                        parseUuid(rs.getString("cracked_uuid")),
                        rs.getLong("registered_at"),
                        rs.getLong("last_login"),
                        rs.getString("last_ip"),
                        rs.getInt("failed_attempts"),
                        rs.getLong("locked_until"),
                        rs.getInt("must_rechange") != 0);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("auth: load failed for " + nameLower + ": " + e.getMessage());
            return null;
        }
    }

    public void loadAsync(String nameLower, Consumer<AuthAccount> cb) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount a = loadBlocking(nameLower);
            Bukkit.getScheduler().runTask(plugin, () -> cb.accept(a));
        });
    }

    public CompletableFuture<Void> saveAsync(AuthAccount a) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                saveBlocking(a);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }

    private void saveBlocking(AuthAccount a) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO auth_accounts(name_lower, password_hash, premium_uuid, cracked_uuid, " +
                     "  registered_at, last_login, last_ip, failed_attempts, locked_until, must_rechange) " +
                     "VALUES(?,?,?,?,?,?,?,?,?,?) " +
                     "ON CONFLICT(name_lower) DO UPDATE SET " +
                     "  password_hash=excluded.password_hash, " +
                     "  premium_uuid=excluded.premium_uuid, " +
                     "  cracked_uuid=excluded.cracked_uuid, " +
                     "  last_login=excluded.last_login, " +
                     "  last_ip=excluded.last_ip, " +
                     "  failed_attempts=excluded.failed_attempts, " +
                     "  locked_until=excluded.locked_until, " +
                     "  must_rechange=excluded.must_rechange")) {
            ps.setString(1, a.nameLower());
            ps.setString(2, a.passwordHash());
            ps.setString(3, a.premiumUuid() == null ? null : a.premiumUuid().toString());
            ps.setString(4, a.crackedUuid() == null ? null : a.crackedUuid().toString());
            ps.setLong(5, a.registeredAt());
            ps.setLong(6, a.lastLogin());
            ps.setString(7, a.lastIp());
            ps.setInt(8, a.failedAttempts());
            ps.setLong(9, a.lockedUntil());
            ps.setInt(10, a.mustRechange() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("auth: save failed for " + a.nameLower() + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void deleteAsync(String nameLower, Runnable after) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.get();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM auth_accounts WHERE name_lower = ?")) {
                ps.setString(1, nameLower);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("auth: delete failed for " + nameLower + ": " + e.getMessage());
            }
            if (after != null) Bukkit.getScheduler().runTask(plugin, after);
        });
    }

    // ---------------------------------------------------------------- premium detection

    /**
     * Returns true if the player's profile carries a Mojang {@code textures}
     * property — only present when the connection passed online-mode handshake
     * (forced by the Velocity AuthBridge for premium names).
     *
     * Velocity's modern forwarding signs the forwarding payload with the
     * shared secret, so a cracked client cannot fabricate this property.
     */
    public static boolean detectPremium(Player player) {
        try {
            return player.getPlayerProfile().getProperties().stream()
                    .anyMatch(p -> "textures".equals(p.getName()));
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- reminders + timeouts

    private void tickReminders() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            AuthSession s = sessions.get(p.getUniqueId());
            if (s == null || s.isAuthenticated()) continue;
            if (s.stage() == AuthSession.Stage.PENDING) continue;
            if (now - s.lastReminderMs() < 1900) continue;
            s.setLastReminderMs(now);
            AuthEffects.reminder(p, hasPasswordHint(p));
        }
    }

    private boolean hasPasswordHint(Player p) {
        // Read the cached account on the session if we wired one; for now
        // we just always show the generic reminder — listener pushes the
        // specific /login vs /register message at join time.
        return true;
    }

    private void tickTimeouts() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            AuthSession s = sessions.get(p.getUniqueId());
            if (s == null || s.isAuthenticated()) continue;
            if (s.stage() == AuthSession.Stage.PENDING) continue;
            if (now - s.joinedAt() >= LOGIN_TIMEOUT_MS) {
                p.kick(AuthEffects.kickComponent("<red>Délai d'authentification dépassé.</red>\n<gray>Reconnecte-toi pour réessayer.</gray>"));
            }
        }
    }

    public void teleportToFreeze(Player p) {
        AuthSession s = sessions.get(p.getUniqueId());
        if (s == null || s.freezeLoc() == null) return;
        Location current = p.getLocation();
        Location freeze = s.freezeLoc();
        if (current.getWorld() == freeze.getWorld()
                && current.distanceSquared(freeze) < 0.04) return;
        Location to = freeze.clone();
        to.setYaw(current.getYaw());
        to.setPitch(current.getPitch());
        p.teleport(to);
    }

    // ---------------------------------------------------------------- helpers

    public Optional<AuthAccount> sessionAccount(Player p) {
        // Currently we don't cache the loaded account on the session — kept
        // as Optional so future caching is a drop-in.
        return Optional.empty();
    }

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String validatePassword(String pw) {
        if (pw == null || pw.length() < MIN_PASSWORD_LEN) {
            return "Le mot de passe doit faire au moins " + MIN_PASSWORD_LEN + " caractères.";
        }
        if (pw.length() > MAX_PASSWORD_LEN) {
            return "Le mot de passe ne peut pas dépasser " + MAX_PASSWORD_LEN + " caractères.";
        }
        for (char c : pw.toCharArray()) {
            if (c < 0x20 || c == 0x7F) return "Caractères invisibles interdits.";
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }
}
