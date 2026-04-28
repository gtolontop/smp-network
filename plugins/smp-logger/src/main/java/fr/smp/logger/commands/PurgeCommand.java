package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PurgeCommand implements CommandExecutor {

    private final SMPLogger plugin;

    public PurgeCommand(SMPLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int days = plugin.getConfig().getInt("retention.days", 7);
        if (args.length > 0) {
            try { days = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        int finalDays = days;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.partitions().purge(finalDays);
            sender.sendMessage(Component.text("Purge done (kept last " + finalDays + " days)", NamedTextColor.GREEN));
        });
        return true;
    }
}
