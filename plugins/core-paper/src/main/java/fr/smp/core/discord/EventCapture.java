package fr.smp.core.discord;

import com.google.gson.JsonObject;
import fr.smp.core.SMPCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * Listens for the MC events that ought to surface on Discord and
 * forwards them over the bridge as typed packets.
 */
public class EventCapture implements Listener {

    private final SMPCore plugin;
    private final BridgeClient client;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public EventCapture(SMPCore plugin, BridgeClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent ev) {
        Player p = ev.getPlayer();
        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "chat");
        pkt.addProperty("uuid", p.getUniqueId().toString());
        pkt.addProperty("name", p.getName());
        pkt.addProperty("message", PLAIN.serialize(ev.message()));
        client.send(pkt);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "join");
        pkt.addProperty("uuid", p.getUniqueId().toString());
        pkt.addProperty("name", p.getName());
        pkt.addProperty("firstTime", !p.hasPlayedBefore());
        client.send(pkt);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "leave");
        pkt.addProperty("uuid", p.getUniqueId().toString());
        pkt.addProperty("name", p.getName());
        client.send(pkt);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent ev) {
        Player victim = ev.getEntity();
        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "death");
        pkt.addProperty("uuid", victim.getUniqueId().toString());
        pkt.addProperty("name", victim.getName());
        Component death = ev.deathMessage();
        pkt.addProperty("message", death != null ? PLAIN.serialize(death) : victim.getName() + " est mort.");
        if (victim.getKiller() != null) pkt.addProperty("killer", victim.getKiller().getName());
        client.send(pkt);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent ev) {
        Advancement adv = ev.getAdvancement();
        if (adv.getKey().getKey().startsWith("recipes/")) return;
        Player p = ev.getPlayer();
        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "advancement");
        pkt.addProperty("uuid", p.getUniqueId().toString());
        pkt.addProperty("name", p.getName());
        var display = adv.getDisplay();
        if (display != null) {
            pkt.addProperty("title", PLAIN.serialize(display.title()));
            pkt.addProperty("description", PLAIN.serialize(display.description()));
            pkt.addProperty("frame", display.frame().name().toLowerCase());
        } else {
            pkt.addProperty("title", adv.getKey().getKey());
            pkt.addProperty("frame", "task");
        }
        client.send(pkt);
    }
}
