package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpEndCommand implements CommandExecutor {

    private final SMPCore plugin;

    public TpEndCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!plugin.endToggle().enabled() && !p.hasPermission("smp.admin")) {
            p.sendMessage(Msg.err("<red>L'End est désactivé.</red>"));
            return true;
        }
        if (plugin.combat() != null && plugin.combat().isTagged(p)) {
            p.sendMessage(Msg.err("Tu es en combat. Attends <white>" + plugin.combat().remainingSec(p) + "s</white>."));
            return true;
        }
        String worldName = plugin.getConfig().getString("rtp.world-end", "world_the_end");
        World w = plugin.resolveWorld(worldName, World.Environment.THE_END);
        if (w == null) {
            p.sendMessage(Msg.err("Monde End introuvable."));
            return true;
        }
        Location platform = new Location(w, 100.5, 50, 0.5, -90, 0);
        p.teleportAsync(platform);
        p.sendMessage(Msg.ok("Bienvenue dans l'End !"));
        return true;
    }
}
