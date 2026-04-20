package fr.smp.core.utils;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class TpsReporter {

    private final SMPCore plugin;
    private BukkitTask task;

    public TpsReporter(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long periodTicks = 20L * 5L; // every 5 seconds
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::report, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void report() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return; // no carrier, skip
        double[] tps = Bukkit.getServer().getTPS();
        double tps1m = tps.length > 0 ? tps[0] : 20.0;
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        plugin.getMessageChannel().sendTps(tps1m, online, max);
        plugin.getMessageChannel().sendRoster();
    }
}
