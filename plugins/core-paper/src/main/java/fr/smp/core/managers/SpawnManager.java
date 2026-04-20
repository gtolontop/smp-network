package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class SpawnManager {

    private final SMPCore plugin;

    public SpawnManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    /** Survival spawn (used by /spawn and death respawn). */
    public Location spawn() {
        return read("spawn");
    }

    /** Hub spawn (used on lobby server). */
    public Location hub() {
        Location l = read("hub-spawn");
        return l != null ? l : read("spawn");
    }

    public void setSpawn(Location loc) {
        write("spawn", loc);
    }

    public void setHub(Location loc) {
        write("hub-spawn", loc);
    }

    private Location read(String key) {
        FileConfiguration c = plugin.getConfig();
        if (!c.isConfigurationSection(key)) return null;
        String worldName = c.getString(key + ".world");
        if (worldName == null) return null;
        World w = plugin.getServer().getWorld(worldName);
        if (w == null) return null;
        return new Location(w,
                c.getDouble(key + ".x"),
                c.getDouble(key + ".y"),
                c.getDouble(key + ".z"),
                (float) c.getDouble(key + ".yaw"),
                (float) c.getDouble(key + ".pitch"));
    }

    private void write(String key, Location loc) {
        FileConfiguration c = plugin.getConfig();
        c.set(key + ".world", loc.getWorld().getName());
        c.set(key + ".x", loc.getX());
        c.set(key + ".y", loc.getY());
        c.set(key + ".z", loc.getZ());
        c.set(key + ".yaw", (double) loc.getYaw());
        c.set(key + ".pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }
}
