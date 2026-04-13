package fr.smp.core;

import fr.smp.core.commands.MenuCommand;
import fr.smp.core.commands.SpawnCommand;
import fr.smp.core.commands.SetSpawnCommand;
import fr.smp.core.gui.ServerSelectorGUI;
import fr.smp.core.listeners.ChatListener;
import fr.smp.core.listeners.JoinListener;
import fr.smp.core.listeners.GUIListener;
import fr.smp.core.listeners.InteractListener;
import fr.smp.core.utils.MessageChannel;
import org.bukkit.plugin.java.JavaPlugin;

public class SMPCore extends JavaPlugin {

    private static SMPCore instance;
    private ServerSelectorGUI serverSelector;
    private MessageChannel messageChannel;
    private String serverType;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        serverType = getConfig().getString("server-type", "lobby");
        messageChannel = new MessageChannel(this);
        serverSelector = new ServerSelectorGUI(this);

        // Register events
        var pm = getServer().getPluginManager();
        pm.registerEvents(new JoinListener(this), this);
        pm.registerEvents(new GUIListener(this), this);
        pm.registerEvents(new InteractListener(this), this);

        if (getConfig().getBoolean("chat.enabled", true)) {
            pm.registerEvents(new ChatListener(this), this);
        }

        // Register commands
        getCommand("menu").setExecutor(new MenuCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("setspawn").setExecutor(new SetSpawnCommand(this));

        getLogger().info("SMPCore loaded on " + serverType + " server!");
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public static SMPCore getInstance() { return instance; }
    public ServerSelectorGUI getServerSelector() { return serverSelector; }
    public MessageChannel getMessageChannel() { return messageChannel; }
    public String getServerType() { return serverType; }
    public boolean isLobby() { return "lobby".equals(serverType); }
}
