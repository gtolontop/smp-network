package fr.smp.core.npc;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.LeaderboardManager;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PodiumCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public PodiumCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> cmdSet(sender, args);
            case "remove", "del", "delete" -> cmdRemove(sender, args);
            case "refresh", "reload" -> cmdRefresh(sender);
            case "list", "info" -> cmdList(sender);
            default -> { sendHelp(sender, label); yield true; }
        };
    }

    private boolean cmdSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.err("Cette commande doit être exécutée en jeu."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /podium set <1|2|3> [categorie] [solo|team]"));
            return true;
        }

        int rank = parseRank(args[1]);
        if (rank < 0) {
            sender.sendMessage(Msg.err("Rang invalide — utilise 1, 2 ou 3."));
            return true;
        }

        LeaderboardManager.Category cat = LeaderboardManager.Category.MONEY;
        LeaderboardManager.Scope scope = LeaderboardManager.Scope.SOLO;
        for (int i = 2; i < args.length; i++) {
            LeaderboardManager.Category c = LeaderboardManager.Category.parse(args[i]);
            LeaderboardManager.Scope s = LeaderboardManager.Scope.parse(args[i]);
            if (c != null) cat = c;
            else if (s != null) scope = s;
        }

        boolean ok = plugin.podium().setSlot(rank, player.getLocation(), cat, scope);
        if (ok) {
            sender.sendMessage(Msg.ok("Podium #" + rank + " placé ici — catégorie: <yellow>"
                    + cat.display() + "</yellow>, scope: <yellow>" + scope.display() + "</yellow>."));
        } else {
            sender.sendMessage(Msg.err("Impossible d'enregistrer le slot podium."));
        }
        return true;
    }

    private boolean cmdRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /podium remove <1|2|3>"));
            return true;
        }
        int rank = parseRank(args[1]);
        if (rank < 0) {
            sender.sendMessage(Msg.err("Rang invalide — utilise 1, 2 ou 3."));
            return true;
        }
        boolean ok = plugin.podium().removeSlot(rank);
        if (ok) sender.sendMessage(Msg.ok("Podium #" + rank + " supprimé."));
        else sender.sendMessage(Msg.err("Aucun podium #" + rank + " configuré."));
        return true;
    }

    private boolean cmdRefresh(CommandSender sender) {
        plugin.podium().refresh();
        sender.sendMessage(Msg.ok("Podium actualisé."));
        return true;
    }

    private boolean cmdList(CommandSender sender) {
        Map<Integer, PodiumManager.SlotConfig> slots = plugin.podium().slots();
        if (slots.isEmpty()) {
            sender.sendMessage(Msg.err("Aucun slot podium configuré. Utilise /podium set <1|2|3>."));
            return true;
        }
        sender.sendMessage(Msg.ok("<white>Slots podium :</white>"));
        slots.forEach((rank, cfg) -> {
            var loc = cfg.location();
            sender.sendMessage(Msg.ok("  <white>#" + rank + "</white> — "
                    + cfg.category().display() + " " + cfg.scope().display()
                    + " @ " + loc.getWorld().getName()
                    + " " + (int) loc.getX() + " " + (int) loc.getY() + " " + (int) loc.getZ()));
        });
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Msg.ok("<white>Podium — commandes :</white>"));
        sender.sendMessage(Msg.ok("  /" + label + " set <1|2|3> [categorie] [solo|team]"));
        sender.sendMessage(Msg.ok("  /" + label + " remove <1|2|3>"));
        sender.sendMessage(Msg.ok("  /" + label + " refresh"));
        sender.sendMessage(Msg.ok("  /" + label + " list"));
    }

    private int parseRank(String s) {
        return switch (s) {
            case "1" -> 1;
            case "2" -> 2;
            case "3" -> 3;
            default -> -1;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) return filter(List.of("set", "remove", "refresh", "list"), current);
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("remove") || sub.equals("del"))
                return filter(List.of("1", "2", "3"), current);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("set")) {
            List<String> opts = new ArrayList<>(List.of(
                    "money", "playtime", "kills", "deaths", "distance", "elo", "solo", "team"));
            return filter(opts, current);
        }
        return List.of();
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : list) if (prefix.isBlank() || s.startsWith(prefix)) out.add(s);
        return out;
    }
}
