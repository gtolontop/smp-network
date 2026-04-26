package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {

    private final SMPCore plugin;

    public BackCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }

        if (args.length == 0) {
            Location loc = plugin.back().getDeathLocation(admin.getUniqueId());
            if (loc == null) {
                admin.sendMessage(Msg.err("Aucune mort enregistrée pour toi."));
                return true;
            }
            String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            plugin.getLogger().info("[BACK] (admin:" + admin.getName() + ") -> sa propre dernière mort à "
                    + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " (" + world + ")");
            admin.teleportAsync(loc);
            admin.sendMessage(Msg.ok("Téléporté à ta dernière mort."));
        } else {
            String name = args[0];
            Location loc = plugin.back().getDeathLocationByName(name);
            if (loc == null) {
                admin.sendMessage(Msg.err("Aucune mort enregistrée pour <white>" + name + "</white>."));
                return true;
            }
            String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            plugin.getLogger().info("[BACK] (admin:" + admin.getName() + ") -> dernière mort de " + name
                    + " à " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " (" + world + ")");
            admin.teleportAsync(loc);
            admin.sendMessage(Msg.ok("Téléporté à la dernière mort de <white>" + name + "</white> <gray>(" +
                    world + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")</gray>."));
        }
        return true;
    }
}
