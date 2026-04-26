package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class EndCommand implements CommandExecutor {

    private final SMPCore plugin;

    public EndCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
