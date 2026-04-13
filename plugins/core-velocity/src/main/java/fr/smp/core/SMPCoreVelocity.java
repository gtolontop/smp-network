package fr.smp.core;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

@Plugin(
    id = "smp-core",
    name = "SMP Core",
    version = "1.0.0",
    description = "Core network plugin for SMP",
    authors = {"SMP Team"}
)
public class SMPCoreVelocity {
    // Channel for backend servers to request transfers
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("smp", "core");

    private final ProxyServer server;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Inject
    public SMPCoreVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        // Register plugin message channel
        server.getChannelRegistrar().register(CHANNEL);

        // Register plugin message listener for server transfer requests from backend
        server.getEventManager().register(this, new PluginMessageListener(this));

        // Register commands
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("globby").aliases("lobby", "hub", "l").plugin(this).build(),
            new ServerCommand(this, "lobby")
        );
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("gsurvival").aliases("survival", "surv", "s").plugin(this).build(),
            new ServerCommand(this, "survival")
        );
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("glist").plugin(this).build(),
            new ListCommand(this)
        );

        logger.info("SMP Core Velocity loaded!");
    }

    // Network-wide join message
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        if (event.getPreviousServer().isEmpty()) {
            // First join - broadcast to all
            Component msg = mm.deserialize("<gray>[<green>+</green>]</gray> <white>" + player.getUsername() + "</white> <gray>a rejoint le réseau</gray>");
            server.getAllPlayers().forEach(p -> p.sendMessage(msg));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        Component msg = mm.deserialize("<gray>[<red>-</red>]</gray> <white>" + player.getUsername() + "</white> <gray>a quitté le réseau</gray>");
        server.getAllPlayers().forEach(p -> p.sendMessage(msg));
    }

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public MiniMessage getMiniMessage() { return mm; }
}
