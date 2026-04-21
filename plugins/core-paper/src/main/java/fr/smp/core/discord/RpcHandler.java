package fr.smp.core.discord;

import com.google.gson.JsonObject;
import fr.smp.core.SMPCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.UUID;

/**
 * Applies remote commands that arrive from the bot: console command
 * execution (with captured output), cross-server broadcast, injected
 * chat lines, targeted tells, and a small set of RPC methods the bot
 * calls synchronously.
 */
public class RpcHandler {

    private final SMPCore plugin;
    private final BridgeClient client;

    public RpcHandler(SMPCore plugin, BridgeClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void handle(JsonObject pkt) {
        String kind = pkt.has("kind") ? pkt.get("kind").getAsString() : "";
        switch (kind) {
            case "console" -> handleConsole(pkt);
            case "broadcast" -> handleBroadcast(pkt);
            case "chat_inject" -> handleChatInject(pkt);
            case "tell" -> handleTell(pkt);
            case "rpc" -> handleRpc(pkt);
            default -> { /* ignore unknown kinds */ }
        }
    }

    private void handleConsole(JsonObject pkt) {
        String id = pkt.has("id") ? pkt.get("id").getAsString() : "";
        String command = pkt.has("command") ? pkt.get("command").getAsString() : "";
        if (command.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (!id.isEmpty()) {
                JsonObject reply = new JsonObject();
                reply.addProperty("kind", "console_result");
                reply.addProperty("id", id);
                reply.addProperty("ok", ok);
                reply.addProperty("output", ok ? "(commande exécutée)" : "(échec)");
                client.send(reply);
            }
        });
    }

    private void handleBroadcast(JsonObject pkt) {
        String target = pkt.has("target") ? pkt.get("target").getAsString() : "all";
        if (!target.equals("all") && !target.equals(client.origin())) return;
        String message = pkt.has("message") ? pkt.get("message").getAsString() : "";
        String prefix = pkt.has("prefix") ? pkt.get("prefix").getAsString() : "Annonce";
        if (message.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcast(Component.text("§b[" + prefix + "] §f" + message)));
    }

    private void handleChatInject(JsonObject pkt) {
        String target = pkt.has("target") ? pkt.get("target").getAsString() : "all";
        if (!target.equals("all") && !target.equals(client.origin())) return;
        String author = pkt.has("author") ? pkt.get("author").getAsString() : "Discord";
        String message = pkt.has("message") ? pkt.get("message").getAsString() : "";
        if (message.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcast(Component.text("§9[Discord] §f" + author + " §7» §f" + message)));
    }

    private void handleTell(JsonObject pkt) {
        String uuid = pkt.has("toUuid") ? pkt.get("toUuid").getAsString() : "";
        String message = pkt.has("message") ? pkt.get("message").getAsString() : "";
        if (uuid.isEmpty() || message.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player p = Bukkit.getPlayer(UUID.fromString(uuid));
                if (p != null) p.sendMessage(Component.text("§d" + message));
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    private void handleRpc(JsonObject pkt) {
        String id = pkt.has("id") ? pkt.get("id").getAsString() : "";
        String method = pkt.has("method") ? pkt.get("method").getAsString() : "";
        JsonObject args = pkt.has("args") && pkt.get("args").isJsonObject() ? pkt.getAsJsonObject("args") : new JsonObject();

        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTask(plugin, () -> {
            JsonObject reply = new JsonObject();
            reply.addProperty("kind", "rpc_result");
            reply.addProperty("id", id);
            try {
                Object data = invoke(method, args);
                reply.addProperty("ok", true);
                if (data instanceof JsonObject jo) reply.add("data", jo);
                else if (data != null) {
                    JsonObject wrap = new JsonObject();
                    wrap.addProperty("value", String.valueOf(data));
                    reply.add("data", wrap);
                }
            } catch (Throwable err) {
                reply.addProperty("ok", false);
                reply.addProperty("error", err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName());
            }
            client.send(reply);
        });
    }

    private Object invoke(String method, JsonObject args) {
        return switch (method) {
            case "console" -> {
                String command = args.has("command") ? args.get("command").getAsString() : "";
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                JsonObject out = new JsonObject();
                out.addProperty("output", ok ? "(commande exécutée)" : "(échec)");
                out.addProperty("ok", ok);
                yield out;
            }
            case "online" -> {
                JsonObject out = new JsonObject();
                out.addProperty("count", Bukkit.getOnlinePlayers().size());
                yield out;
            }
            default -> throw new IllegalArgumentException("unknown rpc method: " + method);
        };
    }
}
