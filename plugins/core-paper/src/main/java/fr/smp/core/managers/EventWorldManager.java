package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class EventWorldManager {

    public enum Type {
        VOID,
        FLAT;

        public static Type parse(String raw) {
            if (raw == null) return FLAT;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "void", "empty", "vide" -> VOID;
                default -> FLAT;
            };
        }
    }

    public record EventWorld(String id, String worldName, Type type) {
        public Location spawn() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        }
    }

    private final SMPCore plugin;
    private final Map<String, EventWorld> worlds = new LinkedHashMap<>();

    public EventWorldManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void loadConfiguredWorlds() {
        worlds.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("event-worlds.worlds");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = "event-worlds.worlds." + id + ".";
            String server = plugin.getConfig().getString(path + "server", plugin.getServerType());
            if (!server.equalsIgnoreCase(plugin.getServerType())) continue;

            String worldName = plugin.getConfig().getString(path + "world", worldName(id));
            Type type = Type.parse(plugin.getConfig().getString(path + "type", "flat"));
            EventWorld eventWorld = new EventWorld(sanitize(id), worldName, type);
            World world = loadWorld(eventWorld);
            if (world != null) {
                applyRules(world, eventWorld);
                worlds.put(eventWorld.id(), eventWorld);
            }
        }
    }

    public EventWorld create(String rawName, Type type) {
        String id = sanitize(rawName);
        if (id.isEmpty()) return null;
        EventWorld eventWorld = new EventWorld(id, worldName(id), type);
        World world = loadWorld(eventWorld);
        if (world == null) return null;

        applyRules(world, eventWorld);
        worlds.put(id, eventWorld);
        persist(eventWorld);
        return eventWorld;
    }

    public EventWorld get(String rawName) {
        String id = sanitize(rawName);
        EventWorld found = worlds.get(id);
        if (found != null) return found;

        String worldName = rawName;
        if (worldName != null && worldName.startsWith("event_")) {
            return worlds.get(sanitize(worldName.substring("event_".length())));
        }
        return null;
    }

    public Collection<EventWorld> all() {
        ArrayList<EventWorld> list = new ArrayList<>(worlds.values());
        list.sort(Comparator.comparing(EventWorld::id));
        return list;
    }

    public boolean isEventWorld(World world) {
        if (world == null) return false;
        for (EventWorld eventWorld : worlds.values()) {
            if (eventWorld.worldName().equalsIgnoreCase(world.getName())) return true;
        }
        return false;
    }

    public boolean createPlatform(EventWorld eventWorld, Material material, int radius) {
        World world = Bukkit.getWorld(eventWorld.worldName());
        if (world == null || material == null || !material.isBlock()) return false;

        int safeRadius = Math.max(0, Math.min(64, radius));
        int y = Math.max(world.getMinHeight() + 4, 63);
        for (int x = -safeRadius; x <= safeRadius; x++) {
            for (int z = -safeRadius; z <= safeRadius; z++) {
                world.getBlockAt(x, y, z).setType(material, false);
            }
        }
        world.setSpawnLocation(0, y + 1, 0);
        return true;
    }

    private World loadWorld(EventWorld eventWorld) {
        World loaded = Bukkit.getWorld(eventWorld.worldName());
        if (loaded != null) return loaded;

        WorldCreator creator = new WorldCreator(eventWorld.worldName());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        if (eventWorld.type() == Type.VOID) {
            creator.generator(new VoidGenerator());
        }
        return creator.createWorld();
    }

    @SuppressWarnings("removal")
    private void applyRules(World world, EventWorld eventWorld) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setPVP(true);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);

        double defaultBorder = plugin.getConfig().getDouble("event-worlds.default-border-size", 512.0);
        double borderSize = plugin.getConfig().getDouble(
                "event-worlds.worlds." + eventWorld.id() + ".border-size",
                defaultBorder);
        borderSize = Math.max(16.0, Math.min(10_000.0, borderSize));
        world.getWorldBorder().setCenter(0.0, 0.0);
        world.getWorldBorder().setSize(borderSize);
    }

    private void persist(EventWorld eventWorld) {
        String path = "event-worlds.worlds." + eventWorld.id() + ".";
        plugin.getConfig().set(path + "server", plugin.getServerType());
        plugin.getConfig().set(path + "world", eventWorld.worldName());
        plugin.getConfig().set(path + "type", eventWorld.type().name().toLowerCase(Locale.ROOT));
        plugin.saveConfig();
    }

    private static String worldName(String id) {
        String sanitized = sanitize(id);
        if (sanitized.startsWith("event_")) return sanitized;
        return "event_" + sanitized;
    }

    public static String sanitize(String raw) {
        if (raw == null) return "";
        String clean = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        clean = clean.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (clean.startsWith("event_")) clean = clean.substring("event_".length());
        if (clean.length() > 32) clean = clean.substring(0, 32);
        return clean;
    }

    public static class VoidGenerator extends ChunkGenerator {
        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            // Empty chunks by design.
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 64.0, 0.5);
        }
    }
}
