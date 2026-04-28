package fr.smp.logger.backup;

import fr.smp.logger.SMPLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Periodic snapshot of vanilla per-player files: world/playerdata/<uuid>.dat,
 * stats/<uuid>.json, advancements/<uuid>.json. Snapshots live in a separate
 * dir keyed by timestamp, so /vanillabackup-style restores stay possible.
 *
 * Direct port of the survival logic from core-paper's VanillaPlayerBackupManager
 * — separated into the logger plugin per the user's "all backups in this plugin"
 * directive. The original SMPCore manager can be deprecated once this is live.
 */
public class PlayerDataBackup {

    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);

    private final SMPLogger plugin;
    private final boolean enabled;
    private final int keepPerPlayer;
    private final long intervalTicks;
    private final File backupRoot;

    public PlayerDataBackup(SMPLogger plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("backup.player-data.enabled", true);
        this.keepPerPlayer = Math.max(3, plugin.getConfig().getInt("backup.player-data.keep-per-player", 48));
        int seconds = Math.max(30, plugin.getConfig().getInt("backup.player-data.interval-seconds", 120));
        this.intervalTicks = seconds * 20L;
        String relative = plugin.getConfig().getString(
                "backup.player-data.directory", "../shared-data/player-file-backups");
        this.backupRoot = new File(plugin.getServer().getWorldContainer(), relative);
    }

    public boolean enabled() { return enabled; }
    public long intervalTicks() { return intervalTicks; }

    public void backupAllOnline(String reason) {
        if (!enabled) return;
        if (!backupRoot.exists() && !backupRoot.mkdirs()) {
            plugin.getLogger().warning("Could not create player backup dir: " + backupRoot);
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) backup(p, reason);
    }

    public void backup(Player p, String reason) {
        if (!enabled) return;
        try { p.saveData(); } catch (Throwable ignored) {}
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> doBackup(p.getUniqueId(), p.getName(), reason));
    }

    private void doBackup(UUID uuid, String name, String reason) {
        File world = Bukkit.getWorlds().get(0).getWorldFolder();
        File data = new File(world, "playerdata/" + uuid + ".dat");
        File stats = new File(world, "stats/" + uuid + ".json");
        File advs = new File(world, "advancements/" + uuid + ".json");
        if (!data.exists()) return;

        File playerDir = new File(backupRoot, uuid.toString());
        if (!playerDir.exists() && !playerDir.mkdirs()) return;
        String stamp = STAMP.format(new Date()) + "_" + reason;
        File snapDir = new File(playerDir, stamp);
        if (!snapDir.mkdirs()) return;

        copyIfPresent(data, new File(snapDir, "data.dat"));
        copyIfPresent(stats, new File(snapDir, "stats.json"));
        copyIfPresent(advs, new File(snapDir, "advancements.json"));

        rotate(playerDir);
    }

    private static void copyIfPresent(File src, File dst) {
        if (!src.exists()) return;
        try {
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void rotate(File dir) {
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null || children.length <= keepPerPlayer) return;
        List<File> sorted = new ArrayList<>();
        for (File f : children) sorted.add(f);
        sorted.sort(Comparator.comparing(File::getName));
        int toRemove = sorted.size() - keepPerPlayer;
        for (int i = 0; i < toRemove; i++) deleteRecursive(sorted.get(i));
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
