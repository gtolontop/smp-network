package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {

    private final SMPCore plugin;

    public VanishCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.vanish")) { sender.sendMessage(Msg.err("Permission refusée.")); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        plugin.vanish().toggle(p);
        return true;
    }
}
