package fr.smp.core.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * WebSocket client wrapper with auto-reconnect. Keeps a single peer
 * connection alive, sends a handshake on connect, and routes every
 * frame through a handler set by {@link DiscordBridge}.
 */
public class BridgeClient {

    private static final int PROTOCOL_VERSION = 1;
    private static final long RECONNECT_MIN_MS = 5_000;
    private static final long RECONNECT_MAX_MS = 60_000;

    private final SMPCore plugin;
    private final URI uri;
    private final String token;
    private final String origin;

    private WebSocketClient ws;
    private RpcHandler rpc;
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicLong backoff = new AtomicLong(RECONNECT_MIN_MS);
    private long lastHeartbeatSent;

    public BridgeClient(SMPCore plugin, String url, String token, String origin) {
        this.plugin = plugin;
        this.uri = URI.create(url);
        this.token = token;
        this.origin = origin;
    }

    public void setRpcHandler(RpcHandler handler) {
        this.rpc = handler;
    }

    public void connectAsync() {
        if (closing.get()) return;
        Thread t = new Thread(this::connectBlocking, "discord-bridge-connect");
        t.setDaemon(true);
        t.start();
    }

    private void connectBlocking() {
        while (!closing.get()) {
            try {
                openOnce();
                return;
            } catch (Throwable err) {
                plugin.getLogger().warning("Bridge connect failed: " + err.getMessage());
                sleep(backoff.get());
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
                hello.addProperty("origin", origin);
                hello.addProperty("software", Bukkit.getName() + " " + Bukkit.getVersion());
                hello.addProperty("mcVersion", Bukkit.getBukkitVersion());
                send(hello.toString());

                // Announce lifecycle.
                JsonObject lifecycle = new JsonObject();
                lifecycle.addProperty("kind", "lifecycle");
                lifecycle.addProperty("state", "started");
                send(lifecycle.toString());

                startHeartbeatThread();
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonObject pkt = JsonParser.parseString(message).getAsJsonObject();
                    String kind = pkt.has("kind") ? pkt.get("kind").getAsString() : "";
                    if ("pong".equals(kind) || "welcome".equals(kind)) return;
                    if (rpc != null) rpc.handle(pkt);
                } catch (Throwable err) {
                    plugin.getLogger().log(Level.FINE, "bridge onMessage error", err);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                plugin.getLogger().info("Bridge socket closed: " + code + " (" + reason + ")");
                if (!closing.get()) {
                    sleep(backoff.get());
                    backoff.set(Math.min(RECONNECT_MAX_MS, backoff.get() * 2));
                    connectAsync();
                }
            }

            @Override
            public void onError(Exception ex) {
                plugin.getLogger().warning("Bridge socket error: " + ex.getMessage());
            }
        };
        ws.setConnectionLostTimeout(30);
        ws.connectBlocking();
    }

    private void startHeartbeatThread() {
        Thread t = new Thread(() -> {
            while (!closing.get() && ws != null && ws.isOpen()) {
                try {
                    Thread.sleep(15_000);
                    JsonObject hb = new JsonObject();
                    hb.addProperty("kind", "heartbeat");
                    hb.addProperty("sentAt", System.currentTimeMillis());
                    lastHeartbeatSent = System.currentTimeMillis();
                    if (ws.isOpen()) ws.send(hb.toString());
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "discord-bridge-heartbeat");
        t.setDaemon(true);
        t.start();
    }

    public void send(JsonObject packet) {
        if (ws == null || !ws.isOpen()) return;
        try {
            ws.send(packet.toString());
        } catch (Throwable ignored) {
            // socket closed while sending; the reconnect thread handles recovery
        }
    }

    public boolean isConnected() {
        return ws != null && ws.isOpen();
    }

    public String origin() {
        return origin;
    }

    public void shutdown() {
        closing.set(true);
        WebSocketClient socket = ws;
        ws = null;
        if (socket != null) {
            try {
                JsonObject bye = new JsonObject();
                bye.addProperty("kind", "lifecycle");
                bye.addProperty("state", "stopping");
                if (socket.isOpen()) {
                    socket.send(bye.toString());
                }
            } catch (Throwable err) {
                plugin.getLogger().log(Level.FINE, "Bridge socket stop-notify failed", err);
            }
            try {
                socket.close();
            } catch (Throwable err) {
                plugin.getLogger().log(Level.WARNING, "Bridge socket close failed during shutdown", err);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
