package fr.smp.core.data;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String name;
    private double money;
    private long shards;
    private int kills;
    private int deaths;
    private long playtimeSec;
    private long firstJoin;
    private long lastSeen;
    private String teamId;
    private boolean scoreboard;
    private long shardsLastMcMin = -1;
    private int dailyKills;
    private String dailyKillsDate;
    private boolean survivalJoined;
    private String lastWorld;
    private Double lastX;
    private Double lastY;
    private Double lastZ;
    private Float lastYaw;
    private Float lastPitch;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        long now = System.currentTimeMillis() / 1000L;
        this.firstJoin = now;
        this.lastSeen = now;
        this.scoreboard = true;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public double money() { return money; }
    public void setMoney(double money) { this.money = money; }
    public void addMoney(double delta) { this.money += delta; }

    public long shards() { return shards; }
    public void setShards(long shards) { this.shards = shards; }
    public void addShards(long delta) { this.shards += delta; }

    public int kills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    public void incrementKills() { this.kills++; }

    public int deaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
    public void incrementDeaths() { this.deaths++; }

    public long playtimeSec() { return playtimeSec; }
    public void setPlaytimeSec(long s) { this.playtimeSec = s; }
    public void addPlaytimeSec(long delta) { this.playtimeSec += delta; }

    public long firstJoin() { return firstJoin; }
    public void setFirstJoin(long firstJoin) { this.firstJoin = firstJoin; }

    public long lastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public String teamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public boolean scoreboardEnabled() { return scoreboard; }
    public void setScoreboardEnabled(boolean v) { this.scoreboard = v; }

    public long shardsLastMcMin() { return shardsLastMcMin; }
    public void setShardsLastMcMin(long v) { this.shardsLastMcMin = v; }

    public int dailyKills() { return dailyKills; }
    public void setDailyKills(int v) { this.dailyKills = v; }
    public void incrementDailyKills() { this.dailyKills++; }

    public String dailyKillsDate() { return dailyKillsDate; }
    public void setDailyKillsDate(String d) { this.dailyKillsDate = d; }

    /** Kept for source compatibility — playtimeSec is now synced from Statistic.PLAY_ONE_MINUTE each tick. */
    public long totalPlaytimeWithSession() { return playtimeSec; }

    public boolean survivalJoined() { return survivalJoined; }
    public void setSurvivalJoined(boolean v) { this.survivalJoined = v; }

    public String lastWorld() { return lastWorld; }
    public Double lastX() { return lastX; }
    public Double lastY() { return lastY; }
    public Double lastZ() { return lastZ; }
    public Float lastYaw() { return lastYaw; }
    public Float lastPitch() { return lastPitch; }

    public boolean hasLastLocation() {
        return lastWorld != null && lastX != null && lastY != null && lastZ != null;
    }

    public void setLastLocation(String world, double x, double y, double z, float yaw, float pitch) {
        this.lastWorld = world;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }
}
