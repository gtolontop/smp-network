package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EndCommand implements CommandExecutor {

    private final SMPCore plugin;

    public EndCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Joueur sans args → tp à la plateforme de l'End
        if (sender instanceof Player p && !p.hasPermission("smp.admin")) {
            if (!plugin.endToggle().enabled()) {
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

        // Admin
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée.")); return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Msg.info("End: " +
                    (plugin.endToggle().enabled() ? "<green>activé</green>" : "<red>désactivé</red>")));
            sender.sendMessage(Msg.mm("<gray>/end on|off|toggle</gray>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "on", "enable" -> {
                plugin.endToggle().setEnabled(true);
                sender.sendMessage(Msg.ok("<green>End activé.</green>"));
            }
            case "off", "disable" -> {
                plugin.endToggle().setEnabled(false);
                sender.sendMessage(Msg.ok("<red>End désactivé.</red>"));
            }
            case "toggle" -> {
                boolean v = !plugin.endToggle().enabled();
                plugin.endToggle().setEnabled(v);
                sender.sendMessage(Msg.ok(v ? "<green>End activé.</green>" : "<red>End désactivé.</red>"));
            }
            default -> sender.sendMessage(Msg.err("/end on|off|toggle"));
        }
        return true;
    }
}
