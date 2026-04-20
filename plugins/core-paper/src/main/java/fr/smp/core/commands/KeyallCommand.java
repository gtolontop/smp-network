package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class KeyallCommand implements CommandExecutor {

    private final SMPCore plugin;

    public KeyallCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        long left = plugin.scoreboard() != null ? plugin.scoreboard().keyallLeft() : 0;
        sender.sendMessage(Msg.info("<aqua>⌛ Keyall dans <white>" + Msg.duration(left) + "</white></aqua>"));
        return true;
    }
}
