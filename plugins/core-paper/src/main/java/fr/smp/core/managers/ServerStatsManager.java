package fr.smp.core.managers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cache of per-server player counts pushed from the Velocity proxy. */
public class ServerStatsManager {

    public record ServerStat(int online, int max) {}

    private final Map<String, ServerStat> stats = new ConcurrentHashMap<>();

    public void put(String server, int online, int max) {
        stats.put(server.toLowerCase(), new ServerStat(online, max));
    }

    public ServerStat get(String server) {
        return stats.get(server.toLowerCase());
    }

    public int onlineOr(String server, int fallback) {
        ServerStat s = get(server);
        return s == null ? fallback : s.online();
    }
}
