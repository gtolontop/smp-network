package fr.smp.core.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state for a single in-progress duel. The match owns its world
 * (copied from the arena template) so its lifecycle is bound to the world's
 * lifecycle: when the match ends, the world is unloaded and the directory
 * deleted on disk.
 */
public final class DuelMatch {

    public enum State { STARTING, FIGHTING, ENDING }

    private final long id;
    private final DuelArena arena;
    private final String worldName;       // instance world name (e.g. "match_42_<arena>")
    private final UUID playerA;
    private final UUID playerB;
    private final String nameA;
    private final String nameB;
    private final long startedAt;
    private final Set<UUID> spectators = new HashSet<>();

    private volatile State state = State.STARTING;
    private volatile UUID winner;          // null until decided
    private volatile UUID loser;
    private volatile boolean drawByDisconnect;

    public DuelMatch(long id, DuelArena arena, String worldName,
                     UUID playerA, String nameA, UUID playerB, String nameB) {
        this.id = id;
        this.arena = arena;
        this.worldName = worldName;
        this.playerA = playerA;
        this.playerB = playerB;
        this.nameA = nameA;
        this.nameB = nameB;
        this.startedAt = System.currentTimeMillis();
    }

    public long id() { return id; }
    public DuelArena arena() { return arena; }
    public String worldName() { return worldName; }
    public World world() { return Bukkit.getWorld(worldName); }
    public UUID playerA() { return playerA; }
    public UUID playerB() { return playerB; }
    public String nameA() { return nameA; }
    public String nameB() { return nameB; }
    public long startedAt() { return startedAt; }
    public Set<UUID> spectators() { return spectators; }

    public State state() { return state; }
    public void setState(State s) { this.state = s; }
    public UUID winner() { return winner; }
    public UUID loser() { return loser; }
    public boolean drawByDisconnect() { return drawByDisconnect; }
    public void setOutcome(UUID winner, UUID loser, boolean draw) {
        this.winner = winner;
        this.loser = loser;
        this.drawByDisconnect = draw;
    }

    public boolean isParticipant(UUID uuid) {
        return playerA.equals(uuid) || playerB.equals(uuid);
    }

    public UUID otherSide(UUID uuid) {
        if (playerA.equals(uuid)) return playerB;
        if (playerB.equals(uuid)) return playerA;
        return null;
    }

    /** Resolve the spawn location for this player inside this match's instance world. */
    public Location spawnFor(UUID uuid) {
        if (arena.spawns().isEmpty()) return null;
        DuelArena.SpawnPair pair = arena.spawns().get(0);
        Location ref = playerA.equals(uuid) ? pair.a() : pair.b();
        if (ref == null) return null;
        World w = world();
        if (w == null) return null;
        return new Location(w, ref.getX(), ref.getY(), ref.getZ(), ref.getYaw(), ref.getPitch());
    }

    public Player onlineA() { return Bukkit.getPlayer(playerA); }
    public Player onlineB() { return Bukkit.getPlayer(playerB); }
}
