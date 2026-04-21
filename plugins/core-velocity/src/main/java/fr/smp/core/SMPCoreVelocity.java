package fr.smp.core;

import com.google.inject.Inject;
import fr.smp.core.auth.AuthBridge;
import fr.smp.core.auth.MojangApi;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "smp-core",
    name = "SMP Core",
    version = "1.0.0",
    description = "Core network plugin for SMP",
    authors = {"SMP Team"}
)
public class SMPCoreVelocity {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("smp", "core");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDir;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, ServerStats> statsByServer = new ConcurrentHashMap<>();
    private final Map<String, List<PluginMessageListener.RosterEntry>> rosterByServer = new ConcurrentHashMap<>();
    private LastServerStore lastServer;

    @Inject
    public SMPCoreVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDir) {
        this.server = server;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        this.lastServer = new LastServerStore(dataDir.resolve("last-server.json"));

        server.getChannelRegistrar().register(CHANNEL);
        server.getEventManager().register(this, new PluginMessageListener(this));
        server.getEventManager().register(this, new KickHandler(this));
        server.getEventManager().register(this, new AuthBridge(new MojangApi(logger), logger));

        server.getScheduler().buildTask(this, this::broadcastStats)
                .delay(5, TimeUnit.SECONDS)
                .repeat(5, TimeUnit.SECONDS)
                .schedule();

        server.getScheduler().buildTask(this, this::broadcastRoster)
                .delay(5, TimeUnit.SECONDS)
                .repeat(5, TimeUnit.SECONDS)
                .schedule();

        var cm = server.getCommandManager();

        cm.register(cm.metaBuilder("hub").aliases("lobby", "l").plugin(this).build(),
            new ServerCommand(this, "lobby"));
        cm.register(cm.metaBuilder("survival").aliases("surv", "s").plugin(this).build(),
            new ServerCommand(this, "survival"));
        cm.register(cm.metaBuilder("glist").aliases("players", "who").plugin(this).build(),
            new ListCommand(this));
        cm.register(cm.metaBuilder("servers").aliases("network", "online").plugin(this).build(),
            new ServersCommand(this));
        cm.register(cm.metaBuilder("tps").aliases("ntps", "networktps").plugin(this).build(),
            new TpsCommand(this));
        cm.register(cm.metaBuilder("nmaxplayers").aliases("setmaxplayers", "maxplayers").plugin(this).build(),
            new MaxPlayersCommand(this));

        logger.info("SMP Core Velocity loaded — chat/perm/roster relay active.");
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (lastServer == null) return;
        lastServer.get(event.getPlayer().getUniqueId())
            .flatMap(server::getServer)
            .ifPresent(event::setInitialServer);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        if (lastServer != null) {
            lastServer.put(player.getUniqueId(), event.getServer().getServerInfo().getName());
        }
        if (event.getPreviousServer().isEmpty()) {
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

    public void putStats(String serverName, ServerStats stats) {
        statsByServer.put(serverName, stats);
    }

    public ServerStats getStats(String serverName) {
        return statsByServer.get(serverName);
    }

    public void putRoster(String serverName, List<PluginMessageListener.RosterEntry> entries) {
        rosterByServer.put(serverName, entries);
    }

    private void broadcastStats() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("stats");
            var backends = server.getAllServers();
            out.writeInt(backends.size());
            for (RegisteredServer rs : backends) {
                String name = rs.getServerInfo().getName();
                int online = rs.getPlayersConnected().size();
                ServerStats s = statsByServer.get(name);
                int max = s != null ? s.max : 0;
                out.writeUTF(name);
                out.writeInt(online);
                out.writeInt(max);
            }
            byte[] payload = bytes.toByteArray();
            for (RegisteredServer rs : backends) {
                if (!rs.getPlayersConnected().isEmpty()) {
                    rs.sendPluginMessage(CHANNEL, payload);
                }
            }
        } catch (Exception e) {
            logger.debug("broadcastStats failed: {}", e.getMessage());
        }
    }

    /** Flatten every backend's roster and push to all backends so they can build
     *  a network-wide tab list. Also sync virtual tab entries so every proxied
     *  player sees the full network in their tablist.
     *
     *  Built from Velocity's own view of connected players (so it works even
     *  before backends have pushed their roster), with prefixes merged in from
     *  whatever backends have reported. */
    private void broadcastRoster() {
        try {
            java.util.Map<String, String> prefixByName = new java.util.HashMap<>();
            for (List<PluginMessageListener.RosterEntry> list : rosterByServer.values()) {
                if (list == null) continue;
                for (PluginMessageListener.RosterEntry e : list) {
                    if (e == null || e.name() == null) continue;
                    prefixByName.putIfAbsent(e.name().toLowerCase(), e.prefix() == null ? "" : e.prefix());
                }
            }

            List<PluginMessageListener.RosterEntry> all = new ArrayList<>();
            for (RegisteredServer rs : server.getAllServers()) {
                String srvName = rs.getServerInfo().getName();
                for (Player p : rs.getPlayersConnected()) {
                    String prefix = prefixByName.getOrDefault(p.getUsername().toLowerCase(), "");
                    all.add(new PluginMessageListener.RosterEntry(p.getUsername(), srvName, prefix));
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("roster");
            out.writeInt(all.size());
            for (PluginMessageListener.RosterEntry e : all) {
                out.writeUTF(e.name());
                out.writeUTF(e.server());
                out.writeUTF(e.prefix());
            }
            byte[] payload = bytes.toByteArray();
            for (RegisteredServer rs : server.getAllServers()) {
                if (!rs.getPlayersConnected().isEmpty()) {
                    rs.sendPluginMessage(CHANNEL, payload);
                }
            }

            try {
                NetworkTabListSync.syncAll(server, server.getAllPlayers(), all);
            } catch (Throwable t) {
                logger.debug("tab sync failed: {}", t.getMessage());
            }
        } catch (Exception e) {
            logger.debug("broadcastRoster failed: {}", e.getMessage());
        }
    }
}
