package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class FlyCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public FlyCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }

        Player target;
        if (args.length >= 1) {
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

        boolean nowFlying = !target.getAllowFlight();
        target.setAllowFlight(nowFlying);
        if (!nowFlying) {
            target.setFlying(false);
        }

        String state = nowFlying ? "<green>activé</green>" : "<red>désactivé</red>";
        if (target != sender) {
            sender.sendMessage(Msg.ok("<gray>Vol " + state + " pour <aqua>" + target.getName() + "</aqua>.</gray>"));
        }
        target.sendMessage(Msg.ok("<gray>Vol " + state + ".</gray>"));
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
