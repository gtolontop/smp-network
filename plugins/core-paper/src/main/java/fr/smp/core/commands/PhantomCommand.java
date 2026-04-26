package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PhantomCommand implements CommandExecutor {

    private final SMPCore plugin;

    public PhantomCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée.")); return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Msg.info("Phantoms: " +
                    (plugin.phantomToggle().enabled() ? "<green>activés</green>" : "<red>désactivés</red>")));
            sender.sendMessage(Msg.mm("<gray>/phantom on|off|toggle</gray>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "on", "enable" -> {
                plugin.phantomToggle().setEnabled(true);
                sender.sendMessage(Msg.ok("<green>Phantoms activés.</green>"));
            }
            case "off", "disable" -> {
                plugin.phantomToggle().setEnabled(false);
                sender.sendMessage(Msg.ok("<red>Phantoms désactivés.</red>"));
            }
            case "toggle" -> {
                boolean v = !plugin.phantomToggle().enabled();
                plugin.phantomToggle().setEnabled(v);
                sender.sendMessage(Msg.ok(v ? "<green>Phantoms activés.</green>" : "<red>Phantoms désactivés.</red>"));
            }
            default -> sender.sendMessage(Msg.err("/phantom on|off|toggle"));
        }
        return true;
    }
}
