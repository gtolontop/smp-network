package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Per-player playtime counter and saphir drip.
 *
 * Why not {@code Statistic.PLAY_ONE_MINUTE}: that stat is per-server, so
 * jumping from lobby to survival would restart the clock and the player
 * would lose visible playtime on cross-server transfers. We instead add
 * one second per wall-clock second the player is online to the shared
 * {@link PlayerData#playtimeSec} which is already persisted to SQLite.
 */
public class PlaytimeManager {

    private final SMPCore plugin;
    private final PlayerDataManager players;

    public PlaytimeManager(SMPCore plugin, PlayerDataManager players) {
        this.plugin = plugin;
        this.players = players;
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                long shardsPerMin = plugin.getConfig().getLong("economy.shards-per-minute", 1);
                for (Player p : Bukkit.getOnlinePlayers()) tick(p, shardsPerMin);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void syncNow(Player p) {
        tick(p, plugin.getConfig().getLong("economy.shards-per-minute", 1));
    }

    private void tick(Player p, long shardsPerMin) {
        PlayerData d = players.get(p);
        if (d == null) return;
        d.setPlaytimeSec(d.playtimeSec() + 1);

        if (shardsPerMin <= 0) return;
        long totalMin = d.playtimeSec() / 60L;
        long last = d.shardsLastMcMin();
        if (last < 0) {
            d.setShardsLastMcMin(totalMin);
        } else if (totalMin > last) {
            d.addShards((totalMin - last) * shardsPerMin);
            d.setShardsLastMcMin(totalMin);
        }
    }
}
