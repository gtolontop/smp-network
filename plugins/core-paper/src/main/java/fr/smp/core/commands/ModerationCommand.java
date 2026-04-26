package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.ModerationManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Moderation command dispatcher. One class handles /kick, /ban, /unban,
 * /mute, /unmute — each entry point decides its own argument layout but
 * they all share player-resolving and duration parsing.
 *
 * Duration strings accepted: "1d2h30m15s", "perm", bare integer (seconds).
 */
public class ModerationCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final String mode; // kick | ban | unban | mute | unmute

    public ModerationCommand(SMPCore plugin, String mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String required = switch (mode) {
            case "kick"   -> "smp.moderation.kick";
            case "mute"   -> "smp.moderation.mute";
            case "unmute" -> "smp.moderation.mute";
            default       -> "smp.moderation";
        };
        if (!sender.hasPermission("smp.moderation") && !sender.hasPermission(required)) {
            sender.sendMessage(Msg.err("Permission refusée.")); return true;
        }
        switch (mode) {
            case "kick" -> handleKick(sender, args);
            case "ban" -> handleBan(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "mute" -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            default -> sender.sendMessage(Msg.err("Mode inconnu."));
        }
        return true;
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(Msg.err("/kick <joueur> [raison]")); return; }
        String reason = args.length > 1 ? joinFrom(args, 1) : "Aucune raison";
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t != null) {
            plugin.moderation().recordKick(t.getUniqueId(), t.getName(), issuerOf(sender), reason);
            t.kick(MM.deserialize("<red><bold>Kick</bold></red>\n<gray>" + reason + "</gray>"));
            sender.sendMessage(Msg.ok("<red>Kick " + t.getName() + "</red>: <gray>" + reason + "</gray>"));
            return;
        }
        UUID uuid = plugin.players().resolveUuid(args[0]);
        if (uuid == null) { sender.sendMessage(Msg.err("Joueur hors-ligne.")); return; }
        String targetName = nameOf(uuid, args[0]);
        plugin.moderation().recordKick(uuid, targetName, issuerOf(sender), reason);
        if (plugin.getMessageChannel() != null) {
            plugin.getMessageChannel().sendModKick(args[0], reason);
        }
        sender.sendMessage(Msg.ok("<red>Kick " + targetName + "</red>: <gray>" + reason + "</gray>"));
    }

    private void handleBan(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(Msg.err("/ban <joueur> [durée] [raison]")); return; }
        UUID target = plugin.players().resolveUuid(args[0]);
        if (target == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return; }
        String targetName = nameOf(target, args[0]);
        long durationSec = 0; int reasonStart = 1;
        String durationStr = "permanent";
        if (args.length >= 2) {
            long parsed = ModerationManager.parseDuration(args[1]);
            if (parsed >= 0) { durationSec = parsed; reasonStart = 2; durationStr = args[1]; }
        }
        String reason = args.length > reasonStart ? joinFrom(args, reasonStart) : null;
        String ip = null;
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            ip = online.getAddress().getAddress().getHostAddress();
        } else {
            ip = plugin.moderation().resolveLastIp(targetName);
        }
        plugin.moderation().ban(target, targetName, issuerOf(sender), reason, durationSec, ip);
        if (online != null) {
            online.kick(MM.deserialize("<red><bold>Banni</bold></red>\n<gray>" +
                    (reason != null ? reason : "non spécifié") + "</gray>\n" +
                    (durationSec <= 0 ? "<dark_red>Permanent</dark_red>"
                                      : "<gray>Durée: </gray><white>" + durationStr + "</white>")));
        } else if (plugin.getMessageChannel() != null) {
            plugin.getMessageChannel().sendModBanKick(targetName,
                    reason != null ? reason : "",
                    durationSec <= 0 ? "<permanent>" : durationStr);
        }
        sender.sendMessage(Msg.ok("<red>Ban</red> <white>" + targetName + "</white> " +
                (durationSec <= 0 ? "<dark_red>permanent</dark_red>" : "<gray>pour " + durationStr + "</gray>") +
                (reason != null ? " <gray>(" + reason + ")</gray>" : "") +
                (ip != null ? " <dark_gray>(IP: " + ip + ")</dark_gray>" : "")));
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(Msg.err("/unban <joueur>")); return; }
        UUID target = plugin.players().resolveUuid(args[0]);
        if (target == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return; }
        plugin.moderation().unban(target, issuerOf(sender));
        sender.sendMessage(Msg.ok("<green>Unban " + args[0] + ".</green>"));
    }

    private void handleMute(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(Msg.err("/mute <joueur> [durée] [raison]")); return; }
        UUID target = plugin.players().resolveUuid(args[0]);
        if (target == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return; }
        String targetName = nameOf(target, args[0]);
        long durationSec = 0; int reasonStart = 1;
        String durationStr = "permanent";
        if (args.length >= 2) {
            long parsed = ModerationManager.parseDuration(args[1]);
            if (parsed >= 0) { durationSec = parsed; reasonStart = 2; durationStr = args[1]; }
        }
        String reason = args.length > reasonStart ? joinFrom(args, reasonStart) : null;
        plugin.moderation().mute(target, targetName, issuerOf(sender), reason, durationSec);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            Component notice = MM.deserialize("<red><bold>Mute</bold></red> <gray>" +
                    (reason != null ? reason : "") + "</gray>");
            online.sendMessage(notice);
        } else if (plugin.getMessageChannel() != null) {
            plugin.getMessageChannel().sendModMuteNotify(targetName,
                    reason != null ? reason : "", durationStr);
        }
        sender.sendMessage(Msg.ok("<red>Mute</red> <white>" + targetName + "</white>"));
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) { sender.sendMessage(Msg.err("/unmute <joueur>")); return; }
        UUID target = plugin.players().resolveUuid(args[0]);
        if (target == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return; }
        plugin.moderation().unmute(target, issuerOf(sender));
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.sendMessage(MM.deserialize("<green><bold>Unmute</bold></green> <gray>Tu n'es plus muet.</gray>"));
        } else if (plugin.getMessageChannel() != null) {
            plugin.getMessageChannel().sendModUnmuteNotify(args[0]);
        }
        sender.sendMessage(Msg.ok("<green>Unmute " + args[0] + ".</green>"));
    }

    private String issuerOf(CommandSender sender) {
        return sender instanceof Player p ? p.getName() : "CONSOLE";
    }

    private String nameOf(UUID uuid, String fallback) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        var offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : fallback;
    }

    private String joinFrom(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
