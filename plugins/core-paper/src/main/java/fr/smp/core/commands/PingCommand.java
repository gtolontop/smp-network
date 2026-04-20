package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PingCommand implements CommandExecutor {

    private final SMPCore plugin;

    public PingCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(Msg.err("Joueur hors-ligne.")); return true; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Msg.err("/ping <joueur>")); return true;
        }
        int ping = target.getPing();
        String color = ping < 80 ? "<green>" : ping < 180 ? "<yellow>" : "<red>";
        sender.sendMessage(Msg.info("<aqua>" + target.getName() + "</aqua> <gray>→</gray> " +
                color + ping + "ms</>"));
        return true;
    }
}
