package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.HeadsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HeadsCommand implements CommandExecutor {

    private final SMPCore plugin;

    public HeadsCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        new HeadsGUI(plugin).open(p);
        return true;
    }
}
