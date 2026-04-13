package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ChatListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    // NORMAL priority so other plugins can override if needed
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String format = plugin.getConfig().getString(
                "chat.format", "<gray>%player%</gray> <dark_gray>»</dark_gray> <white>%message%</white>");

        // Serialize to plain text first to strip any incoming Adventure formatting
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String playerName = event.getPlayer().getName();

        event.renderer((source, sourceDisplayName, msg, audience) ->
                mm.deserialize(format
                        .replace("%player%", playerName)
                        .replace("%message%", message))
        );
    }
}
