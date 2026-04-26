package fr.smp.core.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thin Velocity-side bridge. Announces proxy presence, forwards a
 * small roster snapshot, and reacts to broadcast/chat_inject/tell
 * packets targeted at the "velocity" origin (or "all").
 */
public class VelocityDiscordBridge {

    private static final int PROTOCOL_VERSION = 1;
    private static final long RECONNECT_MIN_MS = 5_000;
    private static final long RECONNECT_MAX_MS = 60_000;

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private URI uri;
    private String token;
    private WebSocketClient ws;
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicLong backoff = new AtomicLong(RECONNECT_MIN_MS);

    public VelocityDiscordBridge(Object plugin, ProxyServer proxy, Logger logger, Path dataDir) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    public void start() {
        Properties cfg = loadConfig();
        if (!"true".equalsIgnoreCase(cfg.getProperty("enabled", "false"))) {
            logger.info("Discord bridge disabled (velocity/discord-bridge.properties)");
            return;
        }
        String url = cfg.getProperty("url", "ws://127.0.0.1:8787");
        this.token = cfg.getProperty("token", "");
        if (token.isEmpty()) {
            logger.warn("Discord bridge enabled but no token — refusing to start");
            return;
        }
        this.uri = URI.create(url);

        connectAsync();

        proxy.getScheduler().buildTask(plugin, this::tickTelemetry)
                .delay(5, TimeUnit.SECONDS)
                .repeat(5, TimeUnit.SECONDS)
                .schedule();
    }

    public void shutdown() {
        closing.set(true);
        if (ws != null) {
            try { ws.close(); } catch (Throwable ignored) {}
        }
    }

    private Properties loadConfig() {
        Properties p = new Properties();
        Path path = dataDir.resolve("discord-bridge.properties");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(dataDir);
                Files.writeString(path, DEFAULT_CONFIG);
                logger.info("Wrote default discord-bridge.properties to {}", path);
            }
            try (var in = Files.newInputStream(path)) {
                p.load(in);
            }
        } catch (Exception err) {
            logger.warn("Could not load discord-bridge.properties: {}", err.getMessage());
        }
        return p;
    }

    private static final String DEFAULT_CONFIG = """
            # Discord bridge — Velocity side.
            # Mirror the values from discord-bot/.env (BRIDGE_TOKEN, BRIDGE_HOST, BRIDGE_PORT).
            enabled=false
            url=ws://127.0.0.1:8787
            token=
            """;

    private void connectAsync() {
        if (closing.get()) return;
        Thread t = new Thread(this::connectBlocking, "velocity-discord-bridge");
        t.setDaemon(true);
        t.start();
    }

    private void connectBlocking() {
        while (!closing.get()) {
            try {
                openOnce();
                return;
            } catch (Throwable err) {
                logger.warn("Bridge connect failed: {}", err.getMessage());
                try { Thread.sleep(backoff.get()); } catch (InterruptedException ignored) { return; }
                backoff.set(Math.min(RECONNECT_MAX_MS, backoff.get() * 2));
            }
        }
    }

    private void openOnce() throws Exception {
        ws = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                backoff.set(RECONNECT_MIN_MS);
                JsonObject hello = new JsonObject();
                hello.addProperty("kind", "hello");
                hello.addProperty("v", PROTOCOL_VERSION);
                hello.addProperty("token", token);
                hello.addProperty("origin", "velocity");
                hello.addProperty("software", "Velocity");
                hello.addProperty("mcVersion", proxy.getVersion().getVersion());
                send(hello.toString());
                logger.info("Discord bridge (velocity) connected");
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonObject pkt = JsonParser.parseString(message).getAsJsonObject();
                    handle(pkt);
                } catch (Throwable err) {
                    logger.debug("bridge onMessage error", err);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("Bridge socket closed: {} ({})", code, reason);
                if (!closing.get()) {
                    try { Thread.sleep(backoff.get()); } catch (InterruptedException ignored) { return; }
                    backoff.set(Math.min(RECONNECT_MAX_MS, backoff.get() * 2));
                    connectAsync();
                }
            }

            @Override
            public void onError(Exception ex) {
                logger.warn("Bridge socket error: {}", ex.getMessage());
            }
        };
        ws.setConnectionLostTimeout(30);
        ws.connectBlocking();

        Thread hb = new Thread(() -> {
            while (!closing.get() && ws != null && ws.isOpen()) {
                try {
                    Thread.sleep(15_000);
                    JsonObject hbp = new JsonObject();
                    hbp.addProperty("kind", "heartbeat");
                    hbp.addProperty("sentAt", System.currentTimeMillis());
                    if (ws.isOpen()) ws.send(hbp.toString());
                } catch (InterruptedException ignored) { return; }
            }
        }, "velocity-discord-heartbeat");
        hb.setDaemon(true);
        hb.start();
    }

    private void tickTelemetry() {
        if (ws == null || !ws.isOpen()) return;
        JsonObject telemetry = new JsonObject();
        telemetry.addProperty("kind", "telemetry");
        telemetry.addProperty("tps1m", 20.0);
        telemetry.addProperty("tps5m", 20.0);
        telemetry.addProperty("tps15m", 20.0);
        telemetry.addProperty("msptAvg", 0.0);
        telemetry.addProperty("msptP95", 0.0);
        telemetry.addProperty("online", proxy.getPlayerCount());
        telemetry.addProperty("maxOnline", proxy.getConfiguration().getShowMaxPlayers());
        telemetry.addProperty("uptimeSec",
                (System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000);
        telemetry.addProperty("loadedChunks", 0);
        telemetry.addProperty("entities", 0);
        Runtime rt = Runtime.getRuntime();
        telemetry.addProperty("memUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        telemetry.addProperty("memMaxMb", rt.maxMemory() / (1024 * 1024));
        ws.send(telemetry.toString());
    }

    private void handle(JsonObject pkt) {
        String kind = pkt.has("kind") ? pkt.get("kind").getAsString() : "";
        switch (kind) {
            case "console" -> handleConsole(pkt);
            case "broadcast" -> {
                String target = pkt.has("target") ? pkt.get("target").getAsString() : "all";
                if (!"all".equals(target) && !"velocity".equals(target)) return;
                String message = pkt.get("message").getAsString();
                String prefix = pkt.has("prefix") ? pkt.get("prefix").getAsString() : "Annonce";
                Component line = Component.text()
                        .append(Component.text("[", NamedTextColor.AQUA))
                        .append(Component.text(prefix, NamedTextColor.AQUA))
                        .append(Component.text("] ", NamedTextColor.AQUA))
                        .append(Component.text(message, NamedTextColor.WHITE))
                        .build();
                proxy.getAllPlayers().forEach(player -> player.sendMessage(line));
            }
            case "chat_inject" -> {
                String target = pkt.has("target") ? pkt.get("target").getAsString() : "all";
                if (!"all".equals(target) && !"velocity".equals(target)) return;
                String author = pkt.has("author") ? pkt.get("author").getAsString() : "Discord";
                String message = pkt.has("message") ? pkt.get("message").getAsString() : "";
                if (message.isEmpty()) return;
                Component line = Component.text()
                        .append(Component.text("[Discord] ", NamedTextColor.BLUE))
                        .append(Component.text(author, NamedTextColor.WHITE))
                        .append(Component.text(" » ", NamedTextColor.GRAY))
                        .append(Component.text(message, NamedTextColor.WHITE))
                        .build();
                proxy.getAllPlayers().forEach(player -> player.sendMessage(line));
            }
            case "tell" -> {
                String uuid = pkt.has("toUuid") ? pkt.get("toUuid").getAsString() : "";
                String message = pkt.has("message") ? pkt.get("message").getAsString() : "";
                if (uuid.isEmpty() || message.isEmpty()) return;
                try {
                    UUID u = UUID.fromString(uuid);
                    proxy.getPlayer(u).ifPresent((Player p) ->
                            p.sendMessage(Component.text(message, NamedTextColor.LIGHT_PURPLE)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "rpc" -> handleRpc(pkt);
            default -> { /* ignore */ }
        }
    }

    private void handleConsole(JsonObject pkt) {
        String id = pkt.has("id") ? pkt.get("id").getAsString() : "";
        String command = pkt.has("command") ? pkt.get("command").getAsString() : "";
        if (command.isEmpty()) return;
        proxy.getCommandManager()
                .executeAsync(proxy.getConsoleCommandSource(), command)
                .whenComplete((ok, err) -> {
                    if (id.isEmpty()) return;
                    JsonObject reply = new JsonObject();
                    reply.addProperty("kind", "console_result");
                    reply.addProperty("id", id);
                    boolean success = err == null && Boolean.TRUE.equals(ok);
                    reply.addProperty("ok", success);
                    reply.addProperty("output", success ? "(command executed)" : errorMessage(err, "command failed"));
                    send(reply);
                });
    }

    private void handleRpc(JsonObject pkt) {
        String id = pkt.has("id") ? pkt.get("id").getAsString() : "";
        String method = pkt.has("method") ? pkt.get("method").getAsString() : "";
        JsonObject args = pkt.has("args") && pkt.get("args").isJsonObject()
                ? pkt.getAsJsonObject("args")
                : new JsonObject();

        if ("console".equals(method)) {
            String command = args.has("command") ? args.get("command").getAsString() : "";
            if (command.isEmpty()) {
                JsonObject reply = new JsonObject();
                reply.addProperty("kind", "rpc_result");
                reply.addProperty("id", id);
                reply.addProperty("ok", false);
                reply.addProperty("error", "missing command");
                send(reply);
                return;
            }
            proxy.getCommandManager()
                    .executeAsync(proxy.getConsoleCommandSource(), command)
                    .whenComplete((ok, err) -> {
                        JsonObject reply = new JsonObject();
                        reply.addProperty("kind", "rpc_result");
                        reply.addProperty("id", id);
                        boolean success = err == null && Boolean.TRUE.equals(ok);
                        reply.addProperty("ok", success);
                        if (success) {
                            JsonObject data = new JsonObject();
                            data.addProperty("output", "(command executed)");
                            data.addProperty("ok", true);
                            reply.add("data", data);
                        } else {
                            reply.addProperty("error", errorMessage(err, "command failed"));
                        }
                        send(reply);
                    });
            return;
        }

        JsonObject reply = new JsonObject();
        reply.addProperty("kind", "rpc_result");
        reply.addProperty("id", id);
        reply.addProperty("ok", false);
        reply.addProperty("error", "unknown rpc method: " + method);
        send(reply);
    }

    private void send(JsonObject packet) {
        WebSocketClient socket = ws;
        if (socket == null || !socket.isOpen()) return;
        socket.send(packet.toString());
    }

    private static String errorMessage(Throwable err, String fallback) {
        if (err == null) return fallback;
        return err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
    }
}
