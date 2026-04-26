package fr.smp.core.managers;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackManager {

    private record Entry(Location location, String playerName) {}

    private final Map<UUID, Entry> deaths = new HashMap<>();
    private final Map<String, UUID> nameIndex = new HashMap<>();

    public void setDeathLocation(UUID uuid, String name, Location loc) {
        deaths.put(uuid, new Entry(loc.clone(), name));
        nameIndex.put(name.toLowerCase(), uuid);
    }

    public Location getDeathLocation(UUID uuid) {
        Entry e = deaths.get(uuid);
        return e != null ? e.location() : null;
    }

    public Location getDeathLocationByName(String name) {
        UUID uuid = nameIndex.get(name.toLowerCase());
        if (uuid == null) return null;
        Entry e = deaths.get(uuid);
        return e != null ? e.location() : null;
    }
}
