package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {

    private final SMPCore plugin;

    public AdminCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }
        boolean nowAdmin = plugin.adminMode().toggle(p);
        if (plugin.fullbright() != null) {
            plugin.fullbright().refresh(p);
        }
        if (nowAdmin) {
            p.sendMessage(Msg.ok("<gray>Mode admin <green>activé</green> — ton stuff joueur est en sécurité.</gray>"));
        } else {
            p.sendMessage(Msg.ok("<gray>Mode joueur <aqua>restauré</aqua>.</gray>"));
        }
        return true;
    }
}
