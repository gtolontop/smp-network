package fr.smp.core.managers;

import com.google.gson.JsonParser;
import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class VanillaPlayerBackupManager implements Listener {

    public enum Part {
        DATA("data"),
        STATS("stats"),
        ADVANCEMENTS("advancements");

        private final String key;

        Part(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public record Snapshot(File directory, long createdAt, String source) {}

    private static final SimpleDateFormat DIRECTORY_DATE =
            new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);

    private final SMPCore plugin;
    private final File backupRoot;
    private final boolean enabled;
    private final int keepPerPlayer;
    private final long intervalTicks;
    private BukkitTask periodicTask;

    public VanillaPlayerBackupManager(SMPCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("player-file-backups.enabled", true);
        this.keepPerPlayer = Math.max(3, plugin.getConfig().getInt("player-file-backups.keep-per-player", 48));
        int seconds = Math.max(30, plugin.getConfig().getInt("player-file-backups.interval-seconds", 120));
        this.intervalTicks = seconds * 20L;

        String relative = plugin.getConfig().getString(
                "player-file-backups.directory", "../shared-data/player-file-backups");
        this.backupRoot = new File(plugin.getServer().getWorldContainer(), relative);
    }

    public boolean isEnabled() {
        return enabled && plugin.isMainSurvival();
    }

    public void start() {
        if (!isEnabled()) return;
        if (!backupRoot.exists() && !backupRoot.mkdirs()) {
            plugin.getLogger().warning("Could not create vanilla player backup dir: "
                    + backupRoot.getAbsolutePath());
            return;
        }
        repairCorruptJsonFilesOnStartup();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        periodicTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> backupAllOnline("periodic"), intervalTicks, intervalTicks);
        plugin.getLogger().info("Vanilla player file backups enabled "
                + "(interval=" + (intervalTicks / 20) + "s, keep=" + keepPerPlayer + ")");
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
    }

    public void backupAllOnlineNow(String source) {
        if (!isEnabled()) return;
        backupAllOnline(source);
    }

    public boolean backup(Player player, String source) {
        if (!isEnabled()) return false;
        player.saveData();
        return copyPlayerFiles(player.getUniqueId(), player.getName(), source);
    }

    public List<Snapshot> list(UUID uuid, int limit) {
        File playerDir = new File(backupRoot, uuid.toString());
        File[] dirs = playerDir.listFiles(File::isDirectory);
        if (dirs == null) return List.of();

        List<Snapshot> snapshots = new ArrayList<>();
        for (File dir : dirs) {
            snapshots.add(new Snapshot(dir, readCreatedAt(dir), readSource(dir)));
        }
        snapshots.sort(Comparator.comparingLong(Snapshot::createdAt).reversed());
        return snapshots.subList(0, Math.min(Math.max(1, limit), snapshots.size()));
    }

    public boolean restore(UUID uuid, Snapshot snapshot, Set<Part> parts) {
        if (!isEnabled() || snapshot == null || parts.isEmpty()) return false;
        if (Bukkit.getPlayer(uuid) != null) return false;

        File worldFolder = mainWorldFolder();
        if (worldFolder == null) return false;

        boolean copied = false;
        for (Part part : parts) {
            File source = new File(snapshot.directory(), part.key() + ".bak");
            File target = fileFor(worldFolder, uuid, part);
            if (!source.isFile() || target == null) continue;
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied = true;
            } catch (IOException e) {
                plugin.getLogger().warning("vanillaBackup.restore " + uuid + " "
                        + part.key() + ": " + e.getMessage());
                return false;
            }
        }
        return copied;
    }

    public Set<Part> parseParts(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("vanilla")) {
            return EnumSet.of(Part.STATS, Part.ADVANCEMENTS);
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "all" -> EnumSet.allOf(Part.class);
            case "data", "playerdata" -> EnumSet.of(Part.DATA);
            case "stats", "stat" -> EnumSet.of(Part.STATS);
            case "advancements", "advancement", "succes", "success", "achievements" ->
                    EnumSet.of(Part.ADVANCEMENTS);
            default -> Set.of();
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        backup(event.getPlayer(), "quit");
    }

    private void backupAllOnline(String source) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            backup(player, source);
        }
    }

    private boolean copyPlayerFiles(UUID uuid, String name, String source) {
        File worldFolder = mainWorldFolder();
        if (worldFolder == null) return false;

        String stamp = DIRECTORY_DATE.format(new Date());
        File dir = new File(new File(backupRoot, uuid.toString()), stamp);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create vanilla player backup snapshot: "
                    + dir.getAbsolutePath());
            return false;
        }

        boolean copied = false;
        for (Part part : Part.values()) {
            File sourceFile = fileFor(worldFolder, uuid, part);
            if (sourceFile == null || !sourceFile.isFile()) continue;
            if (isJsonPart(part) && !isValidJson(sourceFile)) {
                plugin.getLogger().warning("Skipping corrupt vanilla " + part.key()
                        + " backup for " + uuid + ": " + sourceFile.getName());
                continue;
            }
            try {
                Files.copy(sourceFile.toPath(), new File(dir, part.key() + ".bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                copied = true;
            } catch (IOException e) {
                plugin.getLogger().warning("vanillaBackup.copy " + uuid + " "
                        + part.key() + ": " + e.getMessage());
            }
        }

        if (copied) {
            writeMeta(dir, uuid, name, source);
            prune(uuid);
        }
        return copied;
    }

    private void repairCorruptJsonFilesOnStartup() {
        File worldFolder = mainWorldFolder();
        if (worldFolder == null) return;

        repairCorruptJsonFiles(worldFolder, Part.STATS);
        repairCorruptJsonFiles(worldFolder, Part.ADVANCEMENTS);
    }

    private void repairCorruptJsonFiles(File worldFolder, Part part) {
        File dir = directoryFor(worldFolder, part);
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            if (isValidJson(file)) continue;

            UUID uuid = uuidFromJsonFile(file.getName());
            if (uuid == null) {
                plugin.getLogger().warning("Corrupt vanilla " + part.key()
                        + " file has invalid UUID name: " + file.getName());
                continue;
            }

            Snapshot snapshot = latestSnapshotWithPart(uuid, part);
            if (snapshot == null) {
                plugin.getLogger().warning("Corrupt vanilla " + part.key()
                        + " file for " + uuid + " has no backup to restore.");
                continue;
            }

            File source = new File(snapshot.directory(), part.key() + ".bak");
            File quarantine = new File(file.getAbsolutePath()
                    + ".corrupt-" + DIRECTORY_DATE.format(new Date()));
            try {
                Files.copy(file.toPath(), quarantine.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(source.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().warning("Repaired corrupt vanilla " + part.key()
                        + " file for " + uuid + " from backup " + snapshot.directory().getName());
            } catch (IOException e) {
                plugin.getLogger().warning("vanillaBackup.repair " + uuid + " "
                        + part.key() + ": " + e.getMessage());
            }
        }
    }

    private Snapshot latestSnapshotWithPart(UUID uuid, Part part) {
        for (Snapshot snapshot : list(uuid, Integer.MAX_VALUE)) {
            if (new File(snapshot.directory(), part.key() + ".bak").isFile()) {
                return snapshot;
            }
        }
        return null;
    }

    private boolean isValidJson(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonParser.parseReader(reader);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean isJsonPart(Part part) {
        return part == Part.STATS || part == Part.ADVANCEMENTS;
    }

    private UUID uuidFromJsonFile(String fileName) {
        if (fileName == null || !fileName.endsWith(".json")) return null;
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - 5));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private File mainWorldFolder() {
        World world = plugin.resolveWorld(null, World.Environment.NORMAL);
        return world == null ? null : world.getWorldFolder();
    }

    private File fileFor(File worldFolder, UUID uuid, Part part) {
        String fileName = uuid + switch (part) {
            case DATA -> ".dat";
            case STATS, ADVANCEMENTS -> ".json";
        };
        return new File(directoryFor(worldFolder, part), fileName);
    }

    private File directoryFor(File worldFolder, Part part) {
        return switch (part) {
            case DATA -> new File(worldFolder, "players/data");
            case STATS -> new File(worldFolder, "players/stats");
            case ADVANCEMENTS -> new File(worldFolder, "players/advancements");
        };
    }

    private void writeMeta(File dir, UUID uuid, String name, String source) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        yaml.set("name", name);
        yaml.set("source", source);
        yaml.set("created-at", System.currentTimeMillis());
        yaml.set("server", plugin.getServerType());
        try {
            yaml.save(new File(dir, "meta.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("vanillaBackup.meta " + uuid + ": " + e.getMessage());
        }
    }

    private long readCreatedAt(File dir) {
        File meta = new File(dir, "meta.yml");
        if (meta.isFile()) {
            return YamlConfiguration.loadConfiguration(meta).getLong("created-at", dir.lastModified());
        }
        return dir.lastModified();
    }

    private String readSource(File dir) {
        File meta = new File(dir, "meta.yml");
        if (meta.isFile()) {
            return YamlConfiguration.loadConfiguration(meta).getString("source", "?");
        }
        return "?";
    }

    private void prune(UUID uuid) {
        List<Snapshot> snapshots = list(uuid, Integer.MAX_VALUE);
        for (int i = keepPerPlayer; i < snapshots.size(); i++) {
            deleteRecursively(snapshots.get(i).directory());
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
