package fr.smp.core;

import com.google.inject.Inject;
import fr.smp.core.auth.AuthBridge;
import fr.smp.core.auth.MojangApi;
import fr.smp.core.discord.VelocityDiscordBridge;
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

import com.velocitypowered.api.scheduler.ScheduledTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final Map<UUID, ScheduledTask> pendingJoin = new ConcurrentHashMap<>();
    private final Set<UUID> announced = ConcurrentHashMap.newKeySet();
    /** Cracked players that have been authenticated on any backend during this proxy session. */
    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private LastServerStore lastServer;
    private VelocityDiscordBridge discordBridge;

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
        cm.register(cm.metaBuilder("ptr").plugin(this).build(),
            new ServerCommand(this, "ptr"));
        cm.register(cm.metaBuilder("glist").aliases("players", "who").plugin(this).build(),
            new ListCommand(this));
        cm.register(cm.metaBuilder("servers").aliases("network", "online").plugin(this).build(),
            new ServersCommand(this));
        cm.register(cm.metaBuilder("tps").aliases("ntps", "networktps").plugin(this).build(),
            new TpsCommand(this));
        cm.register(cm.metaBuilder("nmaxplayers").aliases("setmaxplayers", "maxplayers").plugin(this).build(),
            new MaxPlayersCommand(this));

        // Discord bridge — WebSocket client to the companion bot.
        discordBridge = new VelocityDiscordBridge(this, server, logger, dataDir);
        discordBridge.start();

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

        // If this is a server switch (not initial join) and the player was
        // authenticated on the previous backend, tell the new backend so
        // it can skip the /login prompt.
        if (event.getPreviousServer().isPresent() && authenticatedPlayers.contains(player.getUniqueId())) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeUTF("auth-validated");
                out.writeUTF(player.getUniqueId().toString());
                out.writeUTF(player.getUsername());
                event.getServer().sendPluginMessage(CHANNEL, bytes.toByteArray());
                logger.info("Sent auth-validated for '{}' to {}", player.getUsername(),
                        event.getServer().getServerInfo().getName());
            } catch (Exception e) {
                logger.warn("Failed to send auth-validated for '{}': {}",
                        player.getUsername(), e.getMessage());
            }
        }

        if (event.getPreviousServer().isEmpty()) {
            UUID uuid = player.getUniqueId();
            String name = player.getUsername();
            ScheduledTask task = server.getScheduler().buildTask(this, () -> {
                pendingJoin.remove(uuid);
                if (player.isActive()) {
                    announced.add(uuid);
                    Component msg = mm.deserialize("<gray>[<green>+</green>]</gray> <white>" + name + "</white> <gray>a rejoint le réseau</gray>");
                    server.getAllPlayers().forEach(p -> p.sendMessage(msg));
                }
            }).delay(2, TimeUnit.SECONDS).schedule();
            pendingJoin.put(uuid, task);
        }

        syncNetworkTabList();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        authenticatedPlayers.remove(uuid);

        ScheduledTask pending = pendingJoin.remove(uuid);
        if (pending != null) {
            pending.cancel();
            return;
        }

        if (announced.remove(uuid)) {
            Component msg = mm.deserialize("<gray>[<red>-</red>]</gray> <white>" + player.getUsername() + "</white> <gray>a quitté le réseau</gray>");
            server.getAllPlayers().forEach(p -> p.sendMessage(msg));
        }

        syncNetworkTabList();
    }

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public MiniMessage getMiniMessage() { return mm; }

    /** Called by PluginMessageListener when a backend reports a player authenticated. */
    public void markAuthenticated(UUID uuid) { authenticatedPlayers.add(uuid); }
    public boolean isAuthenticated(UUID uuid) { return authenticatedPlayers.contains(uuid); }

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
            List<PluginMessageListener.RosterEntry> all = buildNetworkRosterSnapshot();

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

            syncNetworkTabList(all);
        } catch (Exception e) {
            logger.debug("broadcastRoster failed: {}", e.getMessage());
        }
    }

    private List<PluginMessageListener.RosterEntry> buildNetworkRosterSnapshot() {
        java.util.Map<String, String> prefixByName = new java.util.HashMap<>();
        for (List<PluginMessageListener.RosterEntry> list : rosterByServer.values()) {
            if (list == null) continue;
            for (PluginMessageListener.RosterEntry entry : list) {
                if (entry == null || entry.name() == null) continue;
                prefixByName.putIfAbsent(entry.name().toLowerCase(), entry.prefix() == null ? "" : entry.prefix());
            }
        }

        List<PluginMessageListener.RosterEntry> all = new ArrayList<>();
        for (RegisteredServer registeredServer : server.getAllServers()) {
            String serverName = registeredServer.getServerInfo().getName();
            for (Player player : registeredServer.getPlayersConnected()) {
                String prefix = prefixByName.getOrDefault(player.getUsername().toLowerCase(), "");
                all.add(new PluginMessageListener.RosterEntry(player.getUsername(), serverName, prefix));
            }
        }
        return all;
    }

    private void syncNetworkTabList() {
        syncNetworkTabList(buildNetworkRosterSnapshot());
    }

    private void syncNetworkTabList(List<PluginMessageListener.RosterEntry> all) {
        try {
            NetworkTabListSync.syncAll(server, server.getAllPlayers(), all);
        } catch (Throwable t) {
            logger.debug("tab sync failed: {}", t.getMessage());
        }
    }
}
