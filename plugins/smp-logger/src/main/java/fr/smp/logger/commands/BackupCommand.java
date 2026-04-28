package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BackupCommand implements CommandExecutor {

    private final SMPLogger plugin;

    public BackupCommand(SMPLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /loggerbackup data | inv | db", NamedTextColor.GRAY));
            return true;
        }
        String which = args[0].toLowerCase();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            switch (which) {
                case "data" -> { plugin.backup().backupPlayerData("manual"); sender.sendMessage(Component.text("Player data backup queued.", NamedTextColor.GREEN)); }
                case "inv"  -> { plugin.backup().snapshotAllInventories("manual"); sender.sendMessage(Component.text("Inventory snapshot queued.", NamedTextColor.GREEN)); }
                case "db"   -> { plugin.backup().backupDatabaseNow(); sender.sendMessage(Component.text("DB backup written.", NamedTextColor.GREEN)); }
                default     -> sender.sendMessage(Component.text("Unknown target.", NamedTextColor.RED));
            }
        });
        return true;
    }
}
