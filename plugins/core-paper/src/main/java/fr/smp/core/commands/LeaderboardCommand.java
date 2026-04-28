package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.LeaderboardGUI;
import fr.smp.core.gui.LeaderboardHubGUI;
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
import java.util.UUID;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public LeaderboardCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isDistanceSet(args)) {
            return setDistance(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.err("Cette commande ouvre une GUI en jeu."));
            return true;
        }

        if (args.length == 0) {
            new LeaderboardHubGUI(plugin).open(player);
            return true;
        }

        LeaderboardManager.Category category = LeaderboardManager.Category.MONEY;
        LeaderboardManager.Scope scope = LeaderboardManager.Scope.SOLO;
        boolean explicitCategory = false;

        for (String arg : args) {
            LeaderboardManager.Category parsedCategory = LeaderboardManager.Category.parse(arg);
            if (parsedCategory != null) {
                category = parsedCategory;
                explicitCategory = true;
                continue;
            }
            LeaderboardManager.Scope parsedScope = LeaderboardManager.Scope.parse(arg);
            if (parsedScope != null) {
                scope = parsedScope;
                continue;
            }
            player.sendMessage(Msg.err("Usage: <white>/leaderboard [money|playtime|kills|deaths|distance|elo] [solo|team]</white>"));
            return true;
        }

        if (!explicitCategory) {
            new LeaderboardHubGUI(plugin).open(player);
            return true;
        }

        new LeaderboardGUI(plugin).open(player, category, scope, 0);
        return true;
    }

    private boolean isDistanceSet(String[] args) {
        return args.length >= 4
                && args[0].equalsIgnoreCase("distance")
                && args[1].equalsIgnoreCase("set");
    }

    private boolean setDistance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        UUID uuid = plugin.players().resolveUuid(args[2]);
        if (uuid == null) {
            sender.sendMessage(Msg.err("Joueur inconnu."));
            return true;
        }

        Long centimeters = parseDistanceToCentimeters(args[3]);
        if (centimeters == null) {
            sender.sendMessage(Msg.err("Distance invalide. Exemples: <white>250km</white>, <white>3850m</white>."));
            return true;
        }

        String actor = sender instanceof Player p ? p.getName() : "console";
        boolean ok = plugin.leaderboards().setDistance(uuid, args[2], centimeters, actor);
        if (!ok) {
            sender.sendMessage(Msg.err("Impossible de corriger la distance."));
            return true;
        }
        sender.sendMessage(Msg.ok("<green>Distance leaderboard de <yellow>" + args[2]
                + "</yellow> corrigée à <aqua>" + formatDistance(centimeters) + "</aqua>.</green>"));
        return true;
    }

    private Long parseDistanceToCentimeters(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        double multiplier = 100.0D;
        if (text.endsWith("km")) {
            multiplier = 100_000.0D;
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("cm")) {
            multiplier = 1.0D;
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("m")) {
            multiplier = 100.0D;
            text = text.substring(0, text.length() - 1);
        }
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || value < 0) return null;
            return Math.round(value * multiplier);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatDistance(long centimeters) {
        double meters = centimeters / 100.0D;
        if (meters >= 1000.0D) {
            return String.format(Locale.US, "%.2f km", meters / 1000.0D);
        }
        return String.format(Locale.US, "%.0f m", meters);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 2) return List.of();

        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(List.of(
                    "money", "playtime", "kills", "deaths", "distance", "elo", "solo", "team"
            ));
            return filter(suggestions, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("distance") && sender.hasPermission("smp.admin")) {
            return filter(List.of("set"), current);
        }

        if (args.length > 2) return List.of();
        List<String> suggestions = new ArrayList<>(List.of(
                "money", "playtime", "kills", "deaths", "distance", "elo", "solo", "team"
        ));

        for (int i = 0; i < args.length - 1; i++) {
            suggestions.remove(args[i].toLowerCase(Locale.ROOT));
        }

        return filter(suggestions, current);
    }

    private List<String> filter(List<String> suggestions, String current) {
        List<String> out = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (current.isBlank() || suggestion.startsWith(current)) {
                out.add(suggestion);
            }
        }
        return out;
    }
}
