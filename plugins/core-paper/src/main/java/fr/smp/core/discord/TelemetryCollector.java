package fr.smp.core.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Pushes telemetry and the player roster to the bot on a tick-based
 * schedule. Runs on the main thread so Bukkit calls are legal; keeps
 * work minimal so even survival-scale counts don't regress TPS.
 */
public class TelemetryCollector {

    private final SMPCore plugin;
    private final BridgeClient client;
    private final int intervalTicks;
    private BukkitTask task;

    public TelemetryCollector(SMPCore plugin, BridgeClient client, int intervalTicks) {
        this.plugin = plugin;
        this.client = client;
        this.intervalTicks = Math.max(20, intervalTicks);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        if (!client.isConnected()) return;

        double[] tps = Bukkit.getTPS();
        double[] mspt = Bukkit.getAverageTickTime() != 0
                ? new double[]{Bukkit.getAverageTickTime(), Bukkit.getAverageTickTime()}
                : new double[]{0, 0};

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();

        JsonObject telemetry = new JsonObject();
        telemetry.addProperty("kind", "telemetry");
        telemetry.addProperty("tps1m", round(tps[0]));
        telemetry.addProperty("tps5m", round(tps[1]));
        telemetry.addProperty("tps15m", round(tps[2]));
        telemetry.addProperty("msptAvg", round(mspt[0]));
        telemetry.addProperty("msptP95", round(mspt[1]));
        telemetry.addProperty("online", Bukkit.getOnlinePlayers().size());
        telemetry.addProperty("maxOnline", Bukkit.getMaxPlayers());
        telemetry.addProperty("uptimeSec", (System.currentTimeMillis() - jvmStart()) / 1000);
        telemetry.addProperty("loadedChunks", totalLoadedChunks());
        telemetry.addProperty("entities", totalEntities());
        telemetry.addProperty("memUsedMb", used / (1024 * 1024));
        telemetry.addProperty("memMaxMb", rt.maxMemory() / (1024 * 1024));
        client.send(telemetry);

        JsonObject roster = new JsonObject();
        roster.addProperty("kind", "roster");
        JsonArray players = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject jp = new JsonObject();
            jp.addProperty("uuid", p.getUniqueId().toString());
            jp.addProperty("name", p.getName());
            jp.addProperty("ping", p.getPing());
            jp.addProperty("gamemode", p.getGameMode().name().toLowerCase());
            jp.addProperty("world", p.getWorld().getName());
            jp.addProperty("server", client.origin());
            players.add(jp);
        }
        roster.add("players", players);
        client.send(roster);
    }

    private static long jvmStart() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private static int totalLoadedChunks() {
        int n = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            // Paper exposes getChunkCount() that walks the chunk holder map without
            // allocating a Chunk[] copy — w.getLoadedChunks() does, and is wasted here.
            try { n += w.getChunkCount(); }
            catch (Throwable t) { n += w.getLoadedChunks().length; }
        }
        return n;
    }

    private static int totalEntities() {
        int n = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            try { n += w.getEntityCount(); }
            catch (Throwable t) { n += w.getEntities().size(); }
        }
        return n;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
