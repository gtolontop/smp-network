package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final boolean hub;

    public SetSpawnCommand(SMPCore plugin, boolean hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }
        if (!p.hasPermission("smp.admin")) {
            p.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        Location l = p.getLocation();
        if (hub) plugin.spawns().setHub(l);
        else plugin.spawns().setSpawn(l);
        p.sendMessage(Msg.ok("<green>" + (hub ? "Hub" : "Spawn") + " défini.</green>"));
        plugin.logs().log(LogCategory.ADMIN, p, "setspawn " + (hub ? "hub" : "") + " @" +
                l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        return true;
    }
}
