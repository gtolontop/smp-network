package fr.smp.logger.queue;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.dict.MaterialDict;
import fr.smp.logger.dict.PlayerDict;
import fr.smp.logger.dict.StringDict;
import fr.smp.logger.dict.WorldDict;
import fr.smp.logger.items.PreciousStore;
import fr.smp.logger.model.Action;
import fr.smp.logger.model.Event;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fluent ergonomic builder for one event. The whole thing is mutable + reused
 * via thread-local pool to keep allocation pressure off the main thread.
 *
 * Usage:
 *   builder().action(BLOCK_BREAK).actor(player).at(block).material(block.getType()).submit();
 */
public final class EventBuilder {

    private static final ThreadLocal<EventBuilder> POOL = ThreadLocal.withInitial(EventBuilder::new);

    public static EventBuilder begin(SMPLogger plugin) {
        EventBuilder b = POOL.get();
        b.plugin = plugin;
        b.event.reset();
        return b;
    }

    private SMPLogger plugin;
    private final Event event = new Event();

    private EventBuilder() {}

    public EventBuilder action(Action a) { event.action = a; return this; }

    public EventBuilder time(long ms) { event.timestampMs = ms; return this; }

    public EventBuilder actor(Player p) {
        if (p == null) return this;
        event.actorId = dict().players().idOf(p.getUniqueId(), p.getName());
        return this;
    }

    public EventBuilder actor(OfflinePlayer p, String fallbackName) {
        if (p == null) return this;
        String name = p.getName() != null ? p.getName() : fallbackName;
        if (name == null) name = p.getUniqueId().toString();
        event.actorId = dict().players().idOf(p.getUniqueId(), name);
        return this;
    }

    public EventBuilder actorId(int id) { event.actorId = id; return this; }

    public EventBuilder target(Player p) {
        if (p == null) return this;
        event.targetId = dict().players().idOf(p.getUniqueId(), p.getName());
        return this;
    }

    public EventBuilder targetId(int id) { event.targetId = id; return this; }

    public EventBuilder world(World w) { event.worldId = dict().worlds().idOf(w); return this; }

    public EventBuilder at(Block b) {
        if (b == null) return this;
        event.worldId = dict().worlds().idOf(b.getWorld());
        event.x = b.getX(); event.y = b.getY(); event.z = b.getZ();
        return this;
    }

    public EventBuilder at(Location l) {
        if (l == null) return this;
        event.worldId = dict().worlds().idOf(l.getWorld());
        event.x = l.getBlockX(); event.y = l.getBlockY(); event.z = l.getBlockZ();
        return this;
    }

    public EventBuilder at(Entity e) {
        if (e == null) return this;
        return at(e.getLocation());
    }

    public EventBuilder coords(int x, int y, int z) {
        event.x = x; event.y = y; event.z = z; return this;
    }

    public EventBuilder material(Material m) {
        event.materialId = dict().materials().idOf(m);
        return this;
    }

    public EventBuilder material(String name) {
        event.materialId = dict().materials().idOf(name);
        return this;
    }

    public EventBuilder amount(int a) { event.amount = a; return this; }

    public EventBuilder text(String s) { event.textId = dict().strings().idOf(s); return this; }

    public EventBuilder meta(int m) { event.meta = m; return this; }

    /** If the item is precious, store its NBT (deduped) and embed the hash. */
    public EventBuilder item(ItemStack item) {
        if (item == null) return this;
        if (event.materialId == 0) event.materialId = dict().materials().idOf(item.getType());
        if (event.amount == 0) event.amount = item.getAmount();
        PreciousStore store = plugin.preciousStore();
        if (store != null) event.itemHash = store.storeIfPrecious(item);
        return this;
    }

    public boolean submit() {
        if (event.action == null) return false;
        Event copy = new Event();
        copy.timestampMs = event.timestampMs;
        copy.action = event.action;
        copy.actorId = event.actorId;
        copy.targetId = event.targetId;
        copy.worldId = event.worldId;
        copy.x = event.x; copy.y = event.y; copy.z = event.z;
        copy.materialId = event.materialId;
        copy.amount = event.amount;
        copy.itemHash = event.itemHash;
        copy.textId = event.textId;
        copy.meta = event.meta;
        return plugin.queue().submit(copy);
    }

    private DictView dict() { return plugin; }

    /** Marker interface implemented by SMPLogger to give us cheap access to dicts. */
    public interface DictView {
        PlayerDict players();
        MaterialDict materials();
        WorldDict worlds();
        StringDict strings();
    }
}
