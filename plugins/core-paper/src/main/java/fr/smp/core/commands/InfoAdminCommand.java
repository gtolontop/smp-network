package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.ModerationManager;
import fr.smp.core.storage.Database;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class InfoAdminCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final int MAX_HISTORY = 10;

    private final SMPCore plugin;

    public InfoAdminCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Msg.err("/infoadmin <joueur>"));
            return true;
        }

        UUID targetUuid = plugin.players().resolveUuid(args[0]);
        if (targetUuid == null) {
            sender.sendMessage(Msg.err("Joueur inconnu."));
            return true;
        }

        Player online = Bukkit.getPlayer(targetUuid);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetUuid);
        String name = online != null ? online.getName() : (offline.getName() != null ? offline.getName() : args[0]);
        boolean isOnline = online != null && online.isOnline();

        List<Component> lines = new ArrayList<>();

        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        lines.add(MM.deserialize("<gradient:#a8edea:#fed6e3>  Infos Admin</gradient> <dark_gray>»</dark_gray> <white>" + name + "</white>"));
        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        // Identité
        lines.add(MM.deserialize("<yellow>📋 Identité</yellow>"));
        lines.add(MM.deserialize("  <gray>Nom:</gray> <white>" + name + "</white>"));
        lines.add(MM.deserialize("  <gray>UUID:</gray> <white>" + targetUuid + "</white>"));
        lines.add(MM.deserialize("  <gray>En ligne:</gray> " + (isOnline ? "<green>Oui</green>" : "<red>Non</red>")));

        // IP
        String ip = null;
        if (isOnline) {
            ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : null;
        }
        if (ip == null) {
            ip = fetchAuthIp(name);
        }
        if (ip != null) {
            lines.add(MM.deserialize("  <gray>IP:</gray> <white>" + ip + "</white>"));
        }

        // Permission group
        String group = plugin.permissions().primaryGroup(targetUuid);
        lines.add(MM.deserialize("  <gray>Groupe:</gray> <white>" + group + "</white>"));

        // Auth info
        lines.add(MM.deserialize(""));
        lines.add(MM.deserialize("<yellow>🔐 Auth</yellow>"));
        AuthInfo authInfo = fetchAuthInfo(name);
        if (authInfo != null) {
            if (authInfo.registeredAt > 0)
                lines.add(MM.deserialize("  <gray>Inscrit le:</gray> <white>" + DATE_FMT.format(new Date(authInfo.registeredAt * 1000L)) + "</white>"));
            if (authInfo.lastLogin > 0)
                lines.add(MM.deserialize("  <gray>Dernier login:</gray> <white>" + DATE_FMT.format(new Date(authInfo.lastLogin * 1000L)) + "</white>"));
            if (authInfo.lastIp != null)
                lines.add(MM.deserialize("  <gray>Dernière IP:</gray> <white>" + authInfo.lastIp + "</white>"));
            lines.add(MM.deserialize("  <gray>Tentatives échouées:</gray> <white>" + authInfo.failedAttempts + "</white>"));
            if (authInfo.lockedUntil > 0) {
                long now = System.currentTimeMillis() / 1000L;
                if (authInfo.lockedUntil > now) {
                    lines.add(MM.deserialize("  <gray>Verrouillé jusqu'à:</gray> <red>" + DATE_FMT.format(new Date(authInfo.lockedUntil * 1000L)) + "</red>"));
                }
            }
            lines.add(MM.deserialize("  <gray>Premium UUID:</gray> <white>" + (authInfo.premiumUuid != null ? authInfo.premiumUuid : "non") + "</white>"));
            lines.add(MM.deserialize("  <gray>Cracked UUID:</gray> <white>" + (authInfo.crackedUuid != null ? authInfo.crackedUuid : "non") + "</white>"));
        } else {
            lines.add(MM.deserialize("  <gray>Aucun compte auth trouvé.</gray>"));
        }

        // Stats joueur
        PlayerData data = plugin.players().loadOffline(targetUuid);
        if (data != null) {
            lines.add(MM.deserialize(""));
            lines.add(MM.deserialize("<yellow>📊 Stats</yellow>"));
            lines.add(MM.deserialize("  <gray>Argent:</gray> <green>" + String.format("%.2f", data.money()) + "</green>"));
            lines.add(MM.deserialize("  <gray>Saphirs:</gray> <aqua>" + data.shards() + "</aqua>"));
            lines.add(MM.deserialize("  <gray>Kills:</gray> <white>" + data.kills() + "</white>  <gray>Morts:</gray> <white>" + data.deaths() + "</white>  <gray>KDR:</gray> <white>" + String.format("%.2f", data.deaths() > 0 ? (double) data.kills() / data.deaths() : data.kills()) + "</white>"));
            lines.add(MM.deserialize("  <gray>Playtime:</gray> <white>" + Msg.duration(data.playtimeSec()) + "</white>"));
            lines.add(MM.deserialize("  <gray>Première connexion:</gray> <white>" + DATE_FMT.format(new Date(data.firstJoin() * 1000L)) + "</white>"));
            lines.add(MM.deserialize("  <gray>Dernière activité:</gray> <white>" + DATE_FMT.format(new Date(data.lastSeen() * 1000L)) + "</white>"));
            if (data.teamId() != null) {
                lines.add(MM.deserialize("  <gray>Team:</gray> <white>" + data.teamId() + "</white>"));
            }
            if (data.hasLastLocation()) {
                lines.add(MM.deserialize("  <gray>Dernière position:</gray> <white>" + data.lastWorld() + " " +
                        String.format("%.0f %.0f %.0f", data.lastX(), data.lastY(), data.lastZ()) + "</white>"));
            }
        }

        // Info en ligne
        if (isOnline) {
            lines.add(MM.deserialize(""));
            lines.add(MM.deserialize("<yellow>🌐 En ligne</yellow>"));
            lines.add(MM.deserialize("  <gray>Ping:</gray> <white>" + online.getPing() + "ms</white>"));
            lines.add(MM.deserialize("  <gray>GameMode:</gray> <white>" + online.getGameMode().name() + "</white>"));
            lines.add(MM.deserialize("  <gray>Monde:</gray> <white>" + online.getWorld().getName() + "</white>"));
            Location loc = online.getLocation();
            lines.add(MM.deserialize("  <gray>Position:</gray> <white>" + String.format("%.0f %.0f %.0f", loc.getX(), loc.getY(), loc.getZ()) + "</white>"));
            lines.add(MM.deserialize("  <gray>Health:</gray> <red>" + String.format("%.1f", online.getHealth()) + "/" + String.format("%.1f", online.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) + "</red>"));
            lines.add(MM.deserialize("  <gray>Food:</gray> <gold>" + online.getFoodLevel() + "/20</gold>"));
            lines.add(MM.deserialize("  <gray>XP:</gray> <white>" + String.format("%.0f", online.getExp() * 100) + "% (Lvl " + online.getLevel() + ")</white>"));
            lines.add(MM.deserialize("  <gray>OP:</gray> " + (online.isOp() ? "<red>Oui</red>" : "<green>Non</green>")));
            lines.add(MM.deserialize("  <gray>AFK:</gray> " + (plugin.afk().isAfk(online) ? "<yellow>Oui</yellow>" : "<green>Non</green>")));
            lines.add(MM.deserialize("  <gray>Vanish:</gray> " + (plugin.vanish().isVanished(online) ? "<dark_purple>Oui</dark_purple>" : "<green>Non</green>")));
            lines.add(MM.deserialize("  <gray>Admin Mode:</gray> " + (plugin.adminMode().isAdmin(online) ? "<red>Oui</red>" : "<green>Non</green>")));
            if (plugin.combat().isTagged(online)) {
                lines.add(MM.deserialize("  <gray>Combat Tag:</gray> <red>Oui</red>"));
            }
            lines.add(MM.deserialize("  <gray>Fly:</gray> " + (online.isFlying() ? "<aqua>Oui</aqua>" : "<gray>Non</gray>")));
            lines.add(MM.deserialize("  <gray>Client:</gray> <white>" + online.getClientBrandName() + "</white>"));
            lines.add(MM.deserialize("  <gray>Locale:</gray> <white>" + online.locale() + "</white>"));
        }

        // Ban
        ModerationManager.Ban ban = plugin.moderation().activeBan(targetUuid);
        lines.add(MM.deserialize(""));
        lines.add(MM.deserialize("<yellow>🔨 Modération</yellow>"));
        if (ban != null) {
            lines.add(MM.deserialize("  <gray>Ban:</gray> <red>OUI</red>"));
            lines.add(MM.deserialize("  <gray>  Raison:</gray> <white>" + (ban.reason() != null ? ban.reason() : "non spécifiée") + "</white>"));
            lines.add(MM.deserialize("  <gray>  Par:</gray> <white>" + ban.issuer() + "</white>"));
            lines.add(MM.deserialize("  <gray>  Le:</gray> <white>" + DATE_FMT.format(new Date(ban.issuedAt() * 1000L)) + "</white>"));
            if (ban.permanent()) {
                lines.add(MM.deserialize("  <gray>  Durée:</gray> <dark_red>Permanent</dark_red>"));
            } else {
                long remaining = ban.expiresAt() - System.currentTimeMillis() / 1000L;
                lines.add(MM.deserialize("  <gray>  Expire:</gray> <white>" + DATE_FMT.format(new Date(ban.expiresAt() * 1000L)) + "</white> <gray>(reste " + Msg.duration(Math.max(0, remaining)) + ")</gray>"));
            }
        } else {
            lines.add(MM.deserialize("  <gray>Ban:</gray> <green>Non</green>"));
        }

        // Mute
        ModerationManager.Mute mute = plugin.moderation().activeMute(targetUuid);
        if (mute != null) {
            lines.add(MM.deserialize("  <gray>Mute:</gray> <red>OUI</red>"));
            lines.add(MM.deserialize("  <gray>  Raison:</gray> <white>" + (mute.reason() != null ? mute.reason() : "non spécifiée") + "</white>"));
            lines.add(MM.deserialize("  <gray>  Par:</gray> <white>" + mute.issuer() + "</white>"));
            lines.add(MM.deserialize("  <gray>  Le:</gray> <white>" + DATE_FMT.format(new Date(mute.issuedAt() * 1000L)) + "</white>"));
            long remaining = mute.expiresAt() - System.currentTimeMillis() / 1000L;
            lines.add(MM.deserialize("  <gray>  Expire:</gray> <white>" + DATE_FMT.format(new Date(mute.expiresAt() * 1000L)) + "</white> <gray>(reste " + Msg.duration(Math.max(0, remaining)) + ")</gray>"));
        } else {
            lines.add(MM.deserialize("  <gray>Mute:</gray> <green>Non</green>"));
        }

        // Historique modération
        List<HistoryEntry> history = fetchHistory(targetUuid);
        if (!history.isEmpty()) {
            lines.add(MM.deserialize(""));
            lines.add(MM.deserialize("<yellow>📜 Historique modération</yellow> <gray>(derniers " + history.size() + ")</gray>"));
            for (HistoryEntry e : history) {
                String actionColor = switch (e.action) {
                    case "ban" -> "<red>ban</red>";
                    case "unban" -> "<green>unban</green>";
                    case "kick" -> "<gold>kick</gold>";
                    case "mute" -> "<dark_red>mute</dark_red>";
                    case "unmute" -> "<green>unmute</green>";
                    default -> "<white>" + e.action + "</white>";
                };
                String line = "  <dark_gray>•</dark_gray> <white>" + DATE_FMT.format(new Date(e.createdAt * 1000L)) + "</white> " +
                        actionColor + " <gray>par</gray> <white>" + e.issuer + "</white>";
                if (e.reason != null && !e.reason.isEmpty()) line += " <gray>|</gray> <white>" + e.reason + "</white>";
                if (e.duration != null && !e.duration.isEmpty()) line += " <gray>(" + e.duration + ")</gray>";
                lines.add(MM.deserialize(line));
            }
        }

        // Homes
        int homeCount = countHomes(targetUuid);
        if (homeCount > 0) {
            lines.add(MM.deserialize(""));
            lines.add(MM.deserialize("<yellow>🏠 Homes</yellow>"));
            lines.add(MM.deserialize("  <gray>Nombre de homes:</gray> <white>" + homeCount + "</white>"));
        }

        // Bounty
        var bounty = plugin.bounties();
        if (bounty != null) {
            var b = bounty.get(targetUuid);
            lines.add(MM.deserialize(""));
            lines.add(MM.deserialize("<yellow>💰 Prime</yellow>"));
            lines.add(MM.deserialize("  <gray>Prime sur sa tête:</gray> " + (b != null ? "<gold>" + String.format("%.2f", b.amount()) + "</gold> <gray>(par " + b.lastIssuerName() + ")</gray>" : "<gray>Aucune</gray>")));
        }

        lines.add(MM.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));

        for (Component line : lines) {
            sender.sendMessage(line);
        }

        return true;
    }

    private String fetchAuthIp(String name) {
        try (Connection c = plugin.database().get();
             PreparedStatement ps = c.prepareStatement("SELECT last_ip FROM auth_accounts WHERE name_lower=?")) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private AuthInfo fetchAuthInfo(String name) {
        try (Connection c = plugin.database().get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT registered_at, last_login, last_ip, failed_attempts, locked_until, premium_uuid, cracked_uuid FROM auth_accounts WHERE name_lower=?")) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                AuthInfo info = new AuthInfo();
                info.registeredAt = rs.getLong(1);
                info.lastLogin = rs.getLong(2);
                info.lastIp = rs.getString(3);
                info.failedAttempts = rs.getInt(4);
                info.lockedUntil = rs.getLong(5);
                info.premiumUuid = rs.getString(6);
                info.crackedUuid = rs.getString(7);
                return info;
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private List<HistoryEntry> fetchHistory(UUID uuid) {
        List<HistoryEntry> list = new ArrayList<>();
        try (Connection c = plugin.database().get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT action, issuer, reason, duration, created_at FROM mod_history WHERE uuid=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, MAX_HISTORY);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HistoryEntry e = new HistoryEntry();
                    e.action = rs.getString(1);
                    e.issuer = rs.getString(2);
                    e.reason = rs.getString(3);
                    e.duration = rs.getString(4);
                    e.createdAt = rs.getLong(5);
                    list.add(e);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private int countHomes(UUID uuid) {
        try (Connection c = plugin.database().get();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM homes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    private static class AuthInfo {
        long registeredAt;
        long lastLogin;
        String lastIp;
        int failedAttempts;
        long lockedUntil;
        String premiumUuid;
        String crackedUuid;
    }

    private static class HistoryEntry {
        String action;
        String issuer;
        String reason;
        String duration;
        long createdAt;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("smp.admin")) {
            return new fr.smp.core.utils.NetworkTabCompleter(plugin, 0, false)
                    .networkPlayerNames(sender, args[0].toLowerCase());
        }
        return java.util.List.of();
    }
}
