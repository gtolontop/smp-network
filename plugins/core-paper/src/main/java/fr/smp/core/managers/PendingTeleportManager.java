package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * File-based cross-server pending teleport.
 * Each entry is one file in shared-data/pending-tp/<uuid>.yml so both the
 * lobby and survival Paper instances can see it.
 */
public class PendingTeleportManager {

    public enum Kind { LOC, RTP, SPAWN }

    public record Pending(Kind kind, String world, double x, double y, double z,
                          float yaw, float pitch, long createdAt, String targetServer) {

        public Pending(Kind kind, String world, double x, double y, double z,
                       float yaw, float pitch, long createdAt) {
            this(kind, world, x, y, z, yaw, pitch, createdAt, null);
        }

        public boolean targets(String serverType) {
            return targetServer == null || targetServer.isBlank()
                    || targetServer.equalsIgnoreCase(serverType);
        }
    }

    private final SMPCore plugin;
    private final File dir;

    public PendingTeleportManager(SMPCore plugin) {
        this.plugin = plugin;
        File shared = new File(plugin.getConfig().getString("storage.directory", "../shared-data"));
        this.dir = new File(shared, "pending-tp");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create pending-tp dir: " + dir.getAbsolutePath());
        }
    }

    private File fileFor(UUID uuid) { return new File(dir, uuid.toString() + ".yml"); }

    public void set(UUID uuid, Pending p) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("kind", p.kind().name());
        y.set("world", p.world());
        y.set("x", p.x());
        y.set("y", p.y());
        y.set("z", p.z());
        y.set("yaw", (double) p.yaw());
        y.set("pitch", (double) p.pitch());
        y.set("createdAt", p.createdAt());
        y.set("targetServer", p.targetServer());
        try { y.save(fileFor(uuid)); }
        catch (IOException e) { plugin.getLogger().warning("pending-tp save: " + e.getMessage()); }
    }

    public Pending peek(UUID uuid) {
        File f = fileFor(uuid);
        if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Kind kind;
        try { kind = Kind.valueOf(y.getString("kind", "LOC")); }
        catch (IllegalArgumentException e) { kind = Kind.LOC; }
        return new Pending(kind,
                y.getString("world", "world"),
                y.getDouble("x"),
                y.getDouble("y"),
                y.getDouble("z"),
                (float) y.getDouble("yaw"),
                (float) y.getDouble("pitch"),
                y.getLong("createdAt"),
                y.getString("targetServer"));
    }

    public Pending consume(UUID uuid) {
        Pending p = peek(uuid);
        File f = fileFor(uuid);
        if (f.exists()) f.delete();
        return p;
    }
}
