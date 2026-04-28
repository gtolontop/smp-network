package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import org.bukkit.command.PluginCommand;

public class CommandsRegistrar {

    private final SMPLogger plugin;

    public CommandsRegistrar(SMPLogger plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        bind("lookup", new LookupCommand(plugin));
        InspectCommand inspect = new InspectCommand(plugin);
        bind("inspect", inspect);
        plugin.getServer().getPluginManager().registerEvents(inspect, plugin);
        bind("seen", new SeenCommand(plugin));
        bind("trades", new TradesCommand(plugin));
        bind("coalt", new CoAltCommand(plugin));
        bind("rare", new RareCommand(plugin));
        bind("scan", new ScanCommand(plugin));
        bind("loggerstats", new StatsCommand(plugin));
        bind("loggerpurge", new PurgeCommand(plugin));
        bind("loggerbackup", new BackupCommand(plugin));
    }

    private void bind(String name, org.bukkit.command.CommandExecutor ex) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("Command '" + name + "' missing from plugin.yml");
            return;
        }
        cmd.setExecutor(ex);
        if (ex instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
    }
}
