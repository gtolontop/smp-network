package fr.smp.logger.backup;

import fr.smp.logger.SMPLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wires the three backup subsystems together so /loggerbackup, periodic
 * timers and quit events all converge into a single coordinator.
 */
public class BackupModule implements Listener {

    private final SMPLogger plugin;
    private final PlayerDataBackup playerData;
    private final InventorySnapshotter inv;
    private final DatabaseBackup dbBackup;

    private BukkitTask playerDataTask;
    private BukkitTask invTask;
    private BukkitTask dbTask;

    public BackupModule(SMPLogger plugin) {
        this.plugin = plugin;
        this.playerData = new PlayerDataBackup(plugin);
        this.inv = new InventorySnapshotter(plugin);
        this.dbBackup = new DatabaseBackup(plugin);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (playerData.enabled()) {
            long ticks = playerData.intervalTicks();
            playerDataTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> playerData.backupAllOnline("periodic"), ticks, ticks);
        }
        if (inv.enabled()) {
            long ticks = inv.intervalTicks();
            invTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> inv.snapshotAll("periodic"), ticks, ticks);
        }
        if (dbBackup.enabled()) {
            long ticks = dbBackup.intervalTicks();
            dbTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                    dbBackup::backupNow, ticks, ticks);
        }
    }

    public void stop() {
        if (playerDataTask != null) playerDataTask.cancel();
        if (invTask != null) invTask.cancel();
        if (dbTask != null) dbTask.cancel();
        // Final shutdown sweep to avoid losing recent state.
        if (playerData.enabled()) playerData.backupAllOnline("shutdown");
        if (inv.enabled()) inv.snapshotAll("shutdown");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (inv.enabled()) inv.snapshot(p, "quit");
        if (playerData.enabled()) playerData.backup(p, "quit");
    }

    // Public façades for /loggerbackup
    public void backupPlayerData(String reason) { playerData.backupAllOnline(reason); }
    public void snapshotAllInventories(String reason) { inv.snapshotAll(reason); }
    public void backupDatabaseNow() { dbBackup.backupNow(); }
}
