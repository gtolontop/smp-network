package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class WorldBorderManager {

    private final SMPCore plugin;

    public WorldBorderManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void applyAll() {
        FileConfiguration c = plugin.getConfig();
        ConfigurationSection borders = c.getConfigurationSection("worldborder");
        if (borders == null) return;
        for (String key : borders.getKeys(false)) {
            ConfigurationSection sec = borders.getConfigurationSection(key);
            if (sec == null) continue;
            World w = Bukkit.getWorld(key);
            if (w == null) continue;
            double size = sec.getDouble("size", 10_000);
            double cx = sec.getDouble("center.x", 0);
            double cz = sec.getDouble("center.z", 0);
            w.getWorldBorder().setCenter(cx, cz);
            w.getWorldBorder().setSize(size);
        }
    }

    public void setBorder(World world, double size, double cx, double cz) {
        String key = "worldborder." + world.getName();
        FileConfiguration c = plugin.getConfig();
        c.set(key + ".size", size);
        c.set(key + ".center.x", cx);
        c.set(key + ".center.z", cz);
        plugin.saveConfig();
        world.getWorldBorder().setCenter(cx, cz);
        world.getWorldBorder().setSize(size);
    }

    public double sizeOf(World world) {
        return world.getWorldBorder().getSize();
    }

    public double centerX(World world) {
        return world.getWorldBorder().getCenter().getX();
    }

    public double centerZ(World world) {
        return world.getWorldBorder().getCenter().getZ();
    }

    /** Radius used by RTP for a given world (defaults to border size / 2 - padding). */
    public double rtpRadius(World world) {
        FileConfiguration c = plugin.getConfig();
        String k = "rtp.radius." + world.getName();
        if (c.contains(k)) return c.getDouble(k);
        return Math.max(200, sizeOf(world) / 2.0 - 50);
    }
}
