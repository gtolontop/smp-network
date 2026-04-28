package fr.smp.logger.backup;

import fr.smp.logger.SMPLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Periodic SQLite backup using the VACUUM INTO command — produces a fully
 * checkpointed, defragmented snapshot in one statement, no file locking gotchas.
 * Snapshots are timestamped and rotated.
 */
public class DatabaseBackup {

    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);

    private final SMPLogger plugin;
    private final boolean enabled;
    private final long intervalTicks;
    private final int keep;
    private final File backupDir;

    public DatabaseBackup(SMPLogger plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("backup.database.enabled", true);
        int minutes = Math.max(5, plugin.getConfig().getInt("backup.database.interval-minutes", 60));
        this.intervalTicks = minutes * 60L * 20L;
        this.keep = Math.max(2, plugin.getConfig().getInt("backup.database.keep-snapshots", 24));
        String relative = plugin.getConfig().getString(
                "backup.database.directory", "../shared-data/smplogger/db-backups");
        this.backupDir = new File(plugin.getServer().getWorldContainer(), relative);
    }

    public boolean enabled() { return enabled; }
    public long intervalTicks() { return intervalTicks; }

    public void backupNow() {
        if (!enabled) return;
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            plugin.getLogger().warning("Could not create db backup dir: " + backupDir);
            return;
        }
        File out = new File(backupDir, "smplogger-" + STAMP.format(new Date()) + ".db");
        try (Connection c = plugin.db().reader();
             Statement s = c.createStatement()) {
            // VACUUM INTO is the cleanest snapshot tool: produces a non-WAL .db file
            // identical to a fresh dump, no extra journal files to copy.
            s.execute("VACUUM INTO '" + out.getAbsolutePath().replace("'", "''") + "'");
            plugin.getLogger().info("DB backup written: " + out.getName()
                    + " (" + (out.length() / 1024) + " KB)");
        } catch (SQLException e) {
            plugin.getLogger().warning("DB backup failed: " + e.getMessage());
            return;
        }
        rotate();
    }

    private void rotate() {
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("smplogger-") && name.endsWith(".db"));
        if (files == null || files.length <= keep) return;
        List<File> sorted = new ArrayList<>();
        for (File f : files) sorted.add(f);
        sorted.sort(Comparator.comparing(File::getName));
        int toRemove = sorted.size() - keep;
        for (int i = 0; i < toRemove; i++) sorted.get(i).delete();
    }
}
