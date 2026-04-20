package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /here / /coords / /h — broadcasts the caller's position to every
 * player on this backend. Local-only to keep the message relevant
 * ("follow me" wouldn't make sense across servers anyway).
 */
public class HereCommand implements CommandExecutor {

    private final SMPCore plugin;

    public HereCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        Location l = p.getLocation();
        String line = "<aqua>" + p.getName() + "</aqua> <gray>est à</gray> <white>" +
                l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() +
                "</white>";
        for (Player other : Bukkit.getOnlinePlayers()) other.sendMessage(Msg.info(line));
        return true;
    }
}
