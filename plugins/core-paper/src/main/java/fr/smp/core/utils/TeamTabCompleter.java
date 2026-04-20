package fr.smp.core.utils;

import fr.smp.core.SMPCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeamTabCompleter implements TabCompleter {

    private static final List<String> SUBS = List.of(
            "create", "list", "invite", "join", "leave",
            "kick", "disband", "sethome", "home", "info", "color");

    private final SMPCore plugin;
    private final NetworkTabCompleter network;

    public TeamTabCompleter(SMPCore plugin) {
        this.plugin = plugin;
        this.network = new NetworkTabCompleter(plugin, -1, false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return SUBS;
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) return filter(SUBS, prefix);

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("invite") || sub.equals("kick"))) {
            return network.networkPlayerNames(sender, prefix);
        }
        if (args.length == 2 && (sub.equals("join") || sub.equals("info"))) {
            List<String> tags = new ArrayList<>();
            if (plugin.teams() != null) {
                plugin.teams().list().forEach(t -> tags.add(t.tag()));
            }
            return filter(tags, prefix);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (prefix.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(s);
        }
        return out;
    }
}
