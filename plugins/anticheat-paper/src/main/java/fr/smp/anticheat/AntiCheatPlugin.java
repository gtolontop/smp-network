package fr.smp.anticheat;

import fr.smp.anticheat.clients.ClientDetectionModule;
import fr.smp.anticheat.command.AntiCheatCommand;
import fr.smp.anticheat.config.AntiCheatConfig;
import fr.smp.anticheat.containers.ContainerEspModule;
import fr.smp.anticheat.entity.EntityEspModule;
import fr.smp.anticheat.movement.MovementModule;
import fr.smp.anticheat.net.PacketInjector;
import fr.smp.anticheat.visibility.BlockChangeListener;
import fr.smp.anticheat.visibility.VisibilityEngine;
import fr.smp.anticheat.xray.XrayModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiCheatPlugin extends JavaPlugin {

    private static AntiCheatPlugin instance;

    private AntiCheatConfig acConfig;
    private VisibilityEngine visibility;
    private PacketInjector packetInjector;
    private XrayModule xrayModule;
    private ContainerEspModule containerModule;
    private EntityEspModule entityModule;
    private MovementModule movementModule;
    private ClientDetectionModule clientsModule;
    private BypassManager bypass;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        acConfig = new AntiCheatConfig(this);
        bypass = new BypassManager();

        // Identifier le type de serveur pour désactiver les modules "monde" sur lobby.
        String serverType = ClientDetectionModule.resolveServerType();
        boolean isLobby = "lobby".equalsIgnoreCase(serverType);
        getLogger().info("AntiCheat server-type=" + serverType);

        clientsModule = new ClientDetectionModule(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(clientsModule, this);
        if (clientsModule.freecam() != null) {
            pm.registerEvents(clientsModule.freecam(), this);
        }

        if (!isLobby) {
            // Modules "monde" — n'ont aucun sens sur un lobby adventure-mode. Les désactiver
            // évite aussi que l'anti-xray obfusque les chest/lectern du lobby et casse le GUI.
            visibility = new VisibilityEngine(this, acConfig);

            xrayModule = new XrayModule(this, acConfig, visibility);
            containerModule = new ContainerEspModule(this, acConfig, visibility);
            entityModule = new EntityEspModule(this, acConfig, visibility);
            movementModule = new MovementModule(this, acConfig);

            packetInjector = new PacketInjector(this, xrayModule, containerModule, entityModule);
            packetInjector.start();

            entityModule.start();
            movementModule.start();
            visibility.start();
            xrayModule.start();

            pm.registerEvents(packetInjector, this);
            pm.registerEvents(movementModule, this);
            pm.registerEvents(entityModule, this);
            pm.registerEvents(new BlockChangeListener(this), this);
        }

        if (getCommand("ac") != null) {
            AntiCheatCommand cmd = new AntiCheatCommand(this);
            getCommand("ac").setExecutor(cmd);
            getCommand("ac").setTabCompleter(cmd);
        }

        getLogger().info("AntiCheat enabled (xray=" + (xrayModule != null && acConfig.xrayEnabled())
                + ", containers=" + (containerModule != null && acConfig.containersEnabled())
                + ", entityEsp=" + (entityModule != null && acConfig.entityEspEnabled())
                + ", movement=" + (movementModule != null && acConfig.movementEnabled())
                + ", clients=" + clientsModule.enabled() + ")");
    }

    @Override
    public void onDisable() {
        if (packetInjector != null) packetInjector.shutdown();
        if (xrayModule != null) xrayModule.stop();
        if (entityModule != null) entityModule.shutdown();
        if (movementModule != null) movementModule.shutdown();
        if (visibility != null) visibility.shutdown();
        instance = null;
    }

    public static AntiCheatPlugin instance() { return instance; }
    public AntiCheatConfig acConfig() { return acConfig; }
    public VisibilityEngine visibility() { return visibility; }
    public XrayModule xray() { return xrayModule; }
    public ContainerEspModule containers() { return containerModule; }
    public EntityEspModule entities() { return entityModule; }
    public MovementModule movement() { return movementModule; }
    public ClientDetectionModule clients() { return clientsModule; }
    public BypassManager bypass() { return bypass; }
}
