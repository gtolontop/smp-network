package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HeadsCommand implements CommandExecutor {

    private final SMPCore plugin;

    public HeadsCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>La commande /heads est désactivée.</red>"));
        return true;
    }
}
