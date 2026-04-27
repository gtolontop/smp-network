package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.VanillaPlayerBackupManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class VanillaBackupCommand implements CommandExecutor, TabCompleter {

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final SMPCore plugin;

    public VanillaBackupCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        VanillaPlayerBackupManager backups = plugin.vanillaBackups();
        if (backups == null || !backups.isEnabled()) {
            sender.sendMessage(Msg.err("Backups vanilla indisponibles sur ce serveur."));
            return true;
        }

        if (args.length == 0) {
            usage(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender, backups, args);
            case "snap" -> snap(sender, backups, args);
            case "restore" -> restore(sender, backups, args);
            default -> {
                usage(sender);
                yield true;
            }
        };
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Msg.info("<gray>/vanillabackup list <joueur> [limit]</gray>"));
        sender.sendMessage(Msg.info("<gray>/vanillabackup snap <joueur></gray>"));
        sender.sendMessage(Msg.info("<gray>/vanillabackup restore <joueur> <index> [vanilla|stats|advancements|data|all]</gray>"));
    }

    private boolean list(CommandSender sender, VanillaPlayerBackupManager backups, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /vanillabackup list <joueur> [limit]"));
            return true;
        }
        UUID uuid = plugin.players().resolveUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage(Msg.err("Joueur inconnu."));
            return true;
        }
        int limit = 20;
        if (args.length >= 3) {
            try {
                limit = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Msg.err("Limit invalide."));
                return true;
            }
        }

        List<VanillaPlayerBackupManager.Snapshot> snapshots = backups.list(uuid, limit);
        if (snapshots.isEmpty()) {
            sender.sendMessage(Msg.info("<gray>Aucun backup vanilla pour ce joueur.</gray>"));
            return true;
        }

        sender.sendMessage(Msg.info("<gray>Backups vanilla de <white>" + args[1]
                + "</white> (plus récent en tête) :</gray>"));
        for (int i = 0; i < snapshots.size(); i++) {
            var snapshot = snapshots.get(i);
            sender.sendMessage(Msg.info(String.format(
                    "<gray>#%-2d  <white>%s</white>  <dark_gray>[%s]</dark_gray></gray>",
                    i, FMT.format(new Date(snapshot.createdAt())), snapshot.source())));
        }
        return true;
    }

    private boolean snap(CommandSender sender, VanillaPlayerBackupManager backups, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /vanillabackup snap <joueur>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Msg.err("Le joueur doit être en ligne pour un snapshot manuel."));
            return true;
        }
        boolean ok = backups.backup(target, "manual");
        sender.sendMessage(ok
                ? Msg.ok("<green>Backup vanilla pris pour <yellow>" + target.getName() + "</yellow>.</green>")
                : Msg.err("Impossible de prendre le backup vanilla."));
        return true;
    }

    private boolean restore(CommandSender sender, VanillaPlayerBackupManager backups, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.err("Usage: /vanillabackup restore <joueur> <index> [vanilla|stats|advancements|data|all]"));
            return true;
        }
        UUID uuid = plugin.players().resolveUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage(Msg.err("Joueur inconnu."));
            return true;
        }
        if (Bukkit.getPlayer(uuid) != null) {
            sender.sendMessage(Msg.err("Le joueur doit être hors ligne pour restaurer ses fichiers vanilla."));
            return true;
        }

        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Msg.err("Index invalide."));
            return true;
        }

        List<VanillaPlayerBackupManager.Snapshot> snapshots = backups.list(uuid, Math.max(index + 1, 100));
        if (index < 0 || index >= snapshots.size()) {
            sender.sendMessage(Msg.err("Index hors plage."));
            return true;
        }

        Set<VanillaPlayerBackupManager.Part> parts = backups.parseParts(args.length >= 4 ? args[3] : "vanilla");
        if (parts.isEmpty()) {
            sender.sendMessage(Msg.err("Partie invalide. Utilise vanilla, stats, advancements, data ou all."));
            return true;
        }

        boolean ok = backups.restore(uuid, snapshots.get(index), parts);
        sender.sendMessage(ok
                ? Msg.ok("<green>Backup vanilla restauré pour <yellow>" + args[1] + "</yellow>.</green>")
                : Msg.err("Impossible de restaurer le backup vanilla."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) {
            return filter(List.of("list", "snap", "restore"), args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[1]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("restore")) {
            return filter(List.of("vanilla", "stats", "advancements", "data", "all"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> suggestions, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (lower.isBlank() || suggestion.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(suggestion);
            }
        }
        return out;
    }
}
