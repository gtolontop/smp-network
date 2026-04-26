package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.LeaderboardGUI;
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

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public LeaderboardCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.err("Cette commande ouvre une GUI en jeu."));
            return true;
        }

        LeaderboardManager.Category category = LeaderboardManager.Category.MONEY;
        LeaderboardManager.Scope scope = LeaderboardManager.Scope.SOLO;

        for (String arg : args) {
            LeaderboardManager.Category parsedCategory = LeaderboardManager.Category.parse(arg);
            if (parsedCategory != null) {
                category = parsedCategory;
                continue;
            }
            LeaderboardManager.Scope parsedScope = LeaderboardManager.Scope.parse(arg);
            if (parsedScope != null) {
                scope = parsedScope;
                continue;
            }
            player.sendMessage(Msg.err("Usage: <white>/leaderboard [money|playtime|kills|deaths|distance] [solo|team]</white>"));
            return true;
        }

        new LeaderboardGUI(plugin).open(player, category, scope, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 2) return List.of();

        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>(List.of(
                "money", "playtime", "kills", "deaths", "distance", "solo", "team"
        ));

        for (int i = 0; i < args.length - 1; i++) {
            suggestions.remove(args[i].toLowerCase(Locale.ROOT));
        }

        List<String> out = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (current.isBlank() || suggestion.startsWith(current)) {
                out.add(suggestion);
            }
        }
        return out;
    }
}
