package fr.smp.core.utils;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.NetworkRoster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NetworkTabCompleter implements TabCompleter {

    private final SMPCore plugin;
    private final int playerArgIndex;
    private final boolean includeSelf;
    private final List<String> fixedSubcommands;

    public NetworkTabCompleter(SMPCore plugin, int playerArgIndex, boolean includeSelf) {
        this(plugin, playerArgIndex, includeSelf, null);
    }

    public NetworkTabCompleter(SMPCore plugin, int playerArgIndex, boolean includeSelf, List<String> fixedSubcommands) {
        this.plugin = plugin;
        this.playerArgIndex = playerArgIndex;
        this.includeSelf = includeSelf;
        this.fixedSubcommands = fixedSubcommands;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        int idx = args.length - 1;
        if (idx < 0) return List.of();
        String prefix = args[idx].toLowerCase(Locale.ROOT);

        if (fixedSubcommands != null && idx == 0) {
            return filter(fixedSubcommands, prefix);
        }
        if (idx != playerArgIndex) return List.of();

        return networkPlayerNames(sender, prefix);
    }

    public List<String> networkPlayerNames(CommandSender sender, String prefix) {
        List<String> out = new ArrayList<>();
        String self = sender instanceof Player p ? p.getName() : null;
        NetworkRoster roster = plugin.roster();
        if (roster != null && roster.total() > 0) {
            for (NetworkRoster.Entry e : roster.all()) {
                if (!includeSelf && self != null && e.name().equalsIgnoreCase(self)) continue;
                if (prefix.isEmpty() || e.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(e.name());
                }
            }
        }
        if (out.isEmpty()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!includeSelf && self != null && p.getName().equalsIgnoreCase(self)) continue;
                if (prefix.isEmpty() || p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (prefix.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(s);
        }
        return out;
    }
}
