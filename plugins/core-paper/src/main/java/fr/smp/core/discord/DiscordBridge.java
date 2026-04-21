package fr.smp.core.discord;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Entry point for the Discord bridge on the Paper side. Owns the
 * WebSocket client and the per-origin event capture + RPC handlers,
 * kicks off telemetry collection, and exposes a single init/shutdown
 * pair the plugin lifecycle can call.
 *
 * Config (plugins/SMPCore/config.yml):
 *
 *     discord-bridge:
 *       enabled: true
 *       url: "ws://127.0.0.1:8787"
 *       token: "same-as-BRIDGE_TOKEN-in-discord-bot"
 *       origin: "survival"   # or "lobby"
 *       telemetry-interval-ticks: 40
 */
public class DiscordBridge {

    private final SMPCore plugin;
    private BridgeClient client;
    private TelemetryCollector telemetry;
    private EventCapture capture;
    private boolean enabled;

    public DiscordBridge(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("discord-bridge");
        if (cfg == null || !cfg.getBoolean("enabled", false)) {
            plugin.getLogger().info("Discord bridge disabled in config");
            return;
        }

        String url = cfg.getString("url", "ws://127.0.0.1:8787");
        String token = cfg.getString("token", "");
        String origin = cfg.getString("origin", inferOrigin()).toLowerCase(Locale.ROOT);
        int telemetryTicks = cfg.getInt("telemetry-interval-ticks", 40);

        if (token.isEmpty()) {
            plugin.getLogger().warning("Discord bridge enabled but no token — refusing to start");
            return;
        }

        this.client = new BridgeClient(plugin, url, token, origin);
        this.client.connectAsync();

        this.telemetry = new TelemetryCollector(plugin, client, telemetryTicks);
        this.telemetry.start();

        this.capture = new EventCapture(plugin, client);
        Bukkit.getPluginManager().registerEvents(capture, plugin);

        RpcHandler rpc = new RpcHandler(plugin, client);
        client.setRpcHandler(rpc);

        this.enabled = true;
        plugin.getLogger().info("Discord bridge started against " + url + " as " + origin);
    }

    public void shutdown() {
        if (!enabled) return;
        if (telemetry != null) telemetry.stop();
        if (capture != null) capture.stop();
        if (client != null) client.shutdown();
    }

    private String inferOrigin() {
        String serverType = plugin.getConfig().getString("server-type", "survival");
        return serverType.toLowerCase(Locale.ROOT);
    }
}
