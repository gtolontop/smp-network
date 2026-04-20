package fr.smp.core;

public class ServerStats {
    public final double tps;
    public final int online;
    public final int max;
    public final long updatedAt;

    public ServerStats(double tps, int online, int max) {
        this.tps = tps;
        this.online = online;
        this.max = max;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isFresh() {
        return System.currentTimeMillis() - updatedAt < 30_000;
    }
}
