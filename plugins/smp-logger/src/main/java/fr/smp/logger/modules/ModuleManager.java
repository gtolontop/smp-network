package fr.smp.logger.modules;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.listeners.BlockModule;
import fr.smp.logger.listeners.ChatModule;
import fr.smp.logger.listeners.CommandModule;
import fr.smp.logger.listeners.ContainerModule;
import fr.smp.logger.listeners.EntityModule;
import fr.smp.logger.listeners.MovementModule;
import fr.smp.logger.listeners.SessionModule;
import fr.smp.logger.listeners.TradeModule;
import fr.smp.logger.listeners.WorldChangeModule;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

/** Registers/unregisters every Bukkit listener module based on config toggles. */
public class ModuleManager {

    private final SMPLogger plugin;
    private final List<Listener> registered = new ArrayList<>();

    public ModuleManager(SMPLogger plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        if (plugin.getConfig().getBoolean("modules.block", true))       reg(new BlockModule(plugin));
        if (plugin.getConfig().getBoolean("modules.container", true))   reg(new ContainerModule(plugin));
        if (plugin.getConfig().getBoolean("modules.entity", true))      reg(new EntityModule(plugin));
        if (plugin.getConfig().getBoolean("modules.chat", true))        reg(new ChatModule(plugin));
        if (plugin.getConfig().getBoolean("modules.command", true))     reg(new CommandModule(plugin));
        if (plugin.getConfig().getBoolean("modules.session", true))     reg(new SessionModule(plugin));
        if (plugin.getConfig().getBoolean("modules.movement", true))    reg(new MovementModule(plugin));
        if (plugin.getConfig().getBoolean("modules.worldchange", true)) reg(new WorldChangeModule(plugin));
        if (plugin.getConfig().getBoolean("modules.trade", true))       reg(new TradeModule(plugin));
        // Rare tracker is a listener too — registered through its own object held by SMPLogger.
        if (plugin.getConfig().getBoolean("modules.rare", true)) {
            reg(plugin.rareTracker());
        }
        plugin.getLogger().info("Modules registered: " + registered.size());
    }

    private void reg(Listener l) {
        plugin.getServer().getPluginManager().registerEvents(l, plugin);
        registered.add(l);
    }

    public void unregisterAll() {
        for (Listener l : registered) HandlerList.unregisterAll(l);
        registered.clear();
    }
}
