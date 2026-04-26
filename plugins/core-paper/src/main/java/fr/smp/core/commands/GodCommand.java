package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GodCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public GodCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        Player target;
        if (args.length >= 1 && sender.hasPermission("smp.admin")) {
            target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Msg.err("Joueur introuvable."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }

        boolean enabled = plugin.god().toggle(target);
        if (enabled) {
            var attr = target.getAttribute(Attribute.MAX_HEALTH);
            target.setHealth(attr != null ? attr.getValue() : 20.0);
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setFireTicks(0);
        }

        if (target != sender) {
            sender.sendMessage(Msg.ok("<gray>God mode <green>" + (enabled ? "activé" : "désactivé") + "</green> pour <aqua>" + target.getName() + "</aqua>.</gray>"));
        }
        target.sendMessage(Msg.ok("<gray>God mode <green>" + (enabled ? "activé" : "désactivé") + "</green>.</gray>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin") || args.length != 1) return List.of();
        var out = new ArrayList<String>();
        String pref = args[0].toLowerCase();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
        }
        return out;
    }
}
