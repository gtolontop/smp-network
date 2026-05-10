package fr.smp.ptr.block;

import fr.smp.ptr.PtrShowcase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/** Tracks custom blocks placed in the world. Memory-only; survives until restart. */
public class PlacedBlocks {

    private final PtrShowcase plugin;
    private final Map<String, String> blocks = new HashMap<>();

    public PlacedBlocks(PtrShowcase plugin) { this.plugin = plugin; }

    public void mark(Location at, String blockId) {
        blocks.put(key(at), blockId);
    }

    public String get(Location at) {
        return blocks.get(key(at));
    }

    public String remove(Location at) {
        return blocks.remove(key(at));
    }

    /** Remove all display entities at this block location. */
    public void cleanupAt(Location at) {
        var key = plugin.key("ptr_block_id");
        for (Entity e : at.getWorld().getNearbyEntities(at.clone().add(0.5, 0.5, 0.5), 1.5, 2.5, 1.5)) {
            if (!(e instanceof Display || e instanceof Interaction || e instanceof TextDisplay)) continue;
            if (e.getPersistentDataContainer().has(key, PersistentDataType.STRING)) e.remove();
        }
    }

    private String key(Location at) {
        return at.getWorld().getUID() + ":" + at.getBlockX() + ":" + at.getBlockY() + ":" + at.getBlockZ();
    }
}
