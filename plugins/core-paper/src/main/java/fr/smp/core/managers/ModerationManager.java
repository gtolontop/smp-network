package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bans + mutes + kick log, stored in shared SQLite so the rulings apply
 * across every backend. Duration strings like {@code 1d2h30m} are parsed
 * by {@link #parseDuration(String)}.
 *
 * A ban is enforced at AsyncPlayerPreLoginEvent (kicks the player before
 * the world even loads); mutes are enforced by cancelling
 * AsyncChatEvent on any server for as long as the record is active.
 */
public class ModerationManager implements Listener {

    public record Ban(UUID uuid, String name, String issuer, String reason,
                     long issuedAt, long expiresAt) {
        public boolean permanent() { return expiresAt <= 0; }
        public boolean active(long nowSec) { return permanent() || expiresAt > nowSec; }
    }

    public record Mute(UUID uuid, String name, String issuer, String reason,
                      long issuedAt, long expiresAt) {
        public boolean active(long nowSec) { return expiresAt > nowSec; }
    }

    private final SMPCore plugin;
    private final Database db;

    public ModerationManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Parse "1d2h30m15s" or a bare int as seconds. Negative / bad input returns -1. */
    public static long parseDuration(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty() || s.equals("perm") || s.equals("permanent") || s.equals("forever")) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        long total = 0;
        long current = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                current = current * 10 + (c - '0');
                continue;
            }
            long mult = switch (c) {
                case 's' -> 1;
                case 'm' -> 60;
                case 'h' -> 3600;
                case 'd' -> 86400;
                case 'w' -> 604800;
                case 'y' -> 31_536_000L;
                default -> -1;
            };
            if (mult < 0) return -1;
            total += current * mult;
            current = 0;
        }
        if (current != 0) total += current; // trailing digits = seconds
        return total;
    }

    public void ban(UUID uuid, String name, String issuer, String reason, long durationSec) {
        ban(uuid, name, issuer, reason, durationSec, null);
    }

    public void ban(UUID uuid, String name, String issuer, String reason, long durationSec, String ip) {
        long now = System.currentTimeMillis() / 1000L;
        long expires = durationSec <= 0 ? 0 : now + durationSec;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO mod_bans(uuid, name, issuer, reason, issued_at, expires_at) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, issuer);
            ps.setString(4, reason);
            ps.setLong(5, now);
            ps.setLong(6, expires);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.ban: " + e.getMessage());
        }
        if (ip != null && !ip.isEmpty()) {
            banIp(ip, uuid, name, issuer, reason, expires);
        }
        history(uuid, name, "ban", issuer, reason, durationLabel(durationSec));
    }

    private void banIp(String ip, UUID uuid, String name, String issuer, String reason, long expiresAt) {
        long now = System.currentTimeMillis() / 1000L;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO mod_ip_bans(ip, uuid, name, issuer, reason, issued_at, expires_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, ip);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setString(4, issuer);
            ps.setString(5, reason);
            ps.setLong(6, now);
            ps.setLong(7, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.banIp: " + e.getMessage());
        }
    }

    public void unban(UUID uuid, String issuer) {
        String name = "?";
        try (Connection c = db.get()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mod_bans WHERE uuid=? RETURNING name")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) name = rs.getString(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mod_ip_bans WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.unban: " + e.getMessage());
        }
        history(uuid, name, "unban", issuer, null, null);
    }

    public List<Ban> listBans() {
        List<Ban> bans = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000L;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, issuer, reason, issued_at, expires_at FROM mod_bans ORDER BY issued_at DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ban b = new Ban(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getLong(5), rs.getLong(6));
                    if (b.active(now)) bans.add(b);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.listBans: " + e.getMessage());
        }
        return bans;
    }

    public Ban activeBan(UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, issuer, reason, issued_at, expires_at FROM mod_bans WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Ban b = new Ban(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getLong(5), rs.getLong(6));
                    if (b.active(System.currentTimeMillis() / 1000L)) return b;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.activeBan: " + e.getMessage());
        }
        return null;
    }

    public Ban activeIpBan(String ip) {
        long now = System.currentTimeMillis() / 1000L;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, issuer, reason, issued_at, expires_at FROM mod_ip_bans WHERE ip=?")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long expiresAt = rs.getLong(6);
                    boolean permanent = expiresAt <= 0;
                    if (permanent || expiresAt > now) {
                        return new Ban(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getLong(5), expiresAt);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.activeIpBan: " + e.getMessage());
        }
        return null;
    }

    public String resolveLastIp(String playerName) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("SELECT last_ip FROM auth_accounts WHERE name_lower=?")) {
            ps.setString(1, playerName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.resolveLastIp: " + e.getMessage());
        }
        return null;
    }

    public void mute(UUID uuid, String name, String issuer, String reason, long durationSec) {
        if (durationSec <= 0) durationSec = 31_536_000L * 10; // "permanent" mute = 10y
        long now = System.currentTimeMillis() / 1000L;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO mod_mutes(uuid, name, issuer, reason, issued_at, expires_at) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, issuer);
            ps.setString(4, reason);
            ps.setLong(5, now);
            ps.setLong(6, now + durationSec);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.mute: " + e.getMessage());
        }
        history(uuid, name, "mute", issuer, reason, durationLabel(durationSec));
    }

    public void unmute(UUID uuid, String issuer) {
        String name = "?";
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM mod_mutes WHERE uuid=? RETURNING name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) name = rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.unmute: " + e.getMessage());
        }
        history(uuid, name, "unmute", issuer, null, null);
    }

    public Mute activeMute(UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, name, issuer, reason, issued_at, expires_at FROM mod_mutes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Mute m = new Mute(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getLong(5), rs.getLong(6));
                    if (m.active(System.currentTimeMillis() / 1000L)) return m;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.activeMute: " + e.getMessage());
        }
        return null;
    }

    public void recordKick(UUID uuid, String name, String issuer, String reason) {
        history(uuid, name, "kick", issuer, reason, null);
    }

    private void history(UUID uuid, String name, String action, String issuer, String reason, String duration) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mod_history(uuid, name, action, issuer, reason, duration, created_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, action);
            ps.setString(4, issuer);
            ps.setString(5, reason);
            ps.setString(6, duration);
            ps.setLong(7, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("mod.history: " + e.getMessage());
        }
    }

    private String durationLabel(long seconds) {
        if (seconds <= 0) return "permanent";
        long d = seconds / 86400; seconds %= 86400;
        long h = seconds / 3600; seconds %= 3600;
        long m = seconds / 60; long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append('d');
        if (h > 0) sb.append(h).append('h');
        if (m > 0) sb.append(m).append('m');
        if (s > 0 && sb.length() == 0) sb.append(s).append('s');
        return sb.length() == 0 ? "0s" : sb.toString();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Ban b = activeBan(event.getUniqueId());
        if (b == null) {
            String ip = event.getAddress().getHostAddress();
            b = activeIpBan(ip);
        }
        if (b == null) return;
        String when = b.permanent() ? "permanent" :
                "expire le " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                        .format(new java.util.Date(b.expiresAt() * 1000L));
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                net.kyori.adventure.text.Component.text("§cTu es banni du serveur.\n§7Motif: §f" +
                        (b.reason() != null ? b.reason() : "non spécifié") +
                        "\n§7Durée: §f" + when));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        Mute m = activeMute(p.getUniqueId());
        if (m == null) return;
        event.setCancelled(true);
        long remaining = m.expiresAt() - System.currentTimeMillis() / 1000L;
        p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<red>Tu es mute.</red> <gray>" +
                (m.reason() != null ? "Motif: <white>" + m.reason() + "</white>. " : "") +
                "Restant: <white>" + durationLabel(Math.max(0, remaining)) + "</white></gray>"));
    }
}
