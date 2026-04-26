package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public VanishCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.vanish")) { sender.sendMessage(Msg.err("Permission refusée.")); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }

        if (args.length > 0 && args[0].equalsIgnoreCase("pickup")) {
            if (!plugin.vanish().isVanished(p)) {
                p.sendMessage(Msg.err("Tu dois être en vanish pour utiliser <white>/vanish pickup</white>."));
                return true;
            }
            boolean enabled = plugin.vanish().togglePickup(p);
            if (enabled) {
                p.sendMessage(Msg.ok("<gray>Ramassage d'items <green>activé</green>."));
            } else {
                p.sendMessage(Msg.ok("<gray>Ramassage d'items <red>désactivé</red>."));
            }
            return true;
        }

        plugin.vanish().toggle(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            if (pref.isEmpty() || "pickup".startsWith(pref)) {
                return List.of("pickup");
            }
        }
        return List.of();
    }
}
