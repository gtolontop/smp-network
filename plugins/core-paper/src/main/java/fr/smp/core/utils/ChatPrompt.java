package fr.smp.core.utils;

import fr.smp.core.SMPCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One-shot chat input handler. Registered once, routes the next message of a pending player to a callback. */
public class ChatPrompt implements Listener {

    private record Pending(Consumer<String> cb, long expiresAt) {}

    private final SMPCore plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompt(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String instruction, long timeoutSeconds, Consumer<String> callback) {
        p.sendMessage(Msg.info(instruction));
        p.sendMessage(Msg.mm("<gray>Tape <yellow>annuler</yellow> pour annuler.</gray>"));
        pending.put(p.getUniqueId(), new Pending(callback,
                System.currentTimeMillis() + timeoutSeconds * 1000L));
    }

    public void cancel(UUID uuid) {
        pending.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Pending p = pending.remove(event.getPlayer().getUniqueId());
        if (p == null) return;
        event.setCancelled(true);
        if (p.expiresAt() < System.currentTimeMillis()) {
            event.getPlayer().sendMessage(Msg.err("Délai expiré."));
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (text.equalsIgnoreCase("annuler") || text.equalsIgnoreCase("cancel")) {
            event.getPlayer().sendMessage(Msg.info("<yellow>Annulé.</yellow>"));
            return;
        }
        // Callback must run on main thread for safety.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { p.cb().accept(text); }
            catch (Exception ex) { plugin.getLogger().warning("ChatPrompt callback: " + ex.getMessage()); }
        });
    }
}
