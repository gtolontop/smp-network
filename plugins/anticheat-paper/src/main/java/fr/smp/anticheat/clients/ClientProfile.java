package fr.smp.anticheat.clients;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Etat volatile par joueur : brand déclaré, channels enregistrés, flags déclenchés.
 * Partagé entre le main thread (events Bukkit) et les tâches planifiées, donc
 * les collections sont concurrentes.
 */
public final class ClientProfile {

    private volatile String brand = "?";
    private volatile long firstFlagAt = 0L;
    private final Set<String> channels = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Flag> flags = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Freecam heuristic state — cumulative rotation-only ticks + damping.
    private volatile int rotOnlyTicks;
    private volatile long lastMoveMs;

    public String brand() { return brand; }
    public void setBrand(String b) { this.brand = b == null ? "?" : b; }

    public Set<String> channels() { return channels; }

    public void addFlag(Flag f) {
        if (flags.add(f) && firstFlagAt == 0L) {
            firstFlagAt = System.currentTimeMillis();
        }
    }

    public void clearFlags() {
        flags.clear();
        firstFlagAt = 0L;
    }

    public boolean hasFlags() { return !flags.isEmpty(); }

    public Set<Flag> flags() {
        // Return an immutable snapshot so callers can iterate safely.
        return new LinkedHashSet<>(flags);
    }

    public long firstFlagAt() { return firstFlagAt; }

    public int rotOnlyTicks() { return rotOnlyTicks; }
    public void incRotOnlyTicks() { rotOnlyTicks++; }
    public void resetRotOnlyTicks() { rotOnlyTicks = 0; }

    public long lastMoveMs() { return lastMoveMs; }
    public void touchMove() { lastMoveMs = System.currentTimeMillis(); }

    /** Niveau le plus sévère parmi tous les flags actuels. */
    public CheatSignatures.Severity worstSeverity() {
        CheatSignatures.Severity worst = null;
        for (Flag f : flags) {
            if (worst == null || f.severity.ordinal() < worst.ordinal()) worst = f.severity;
        }
        return worst;
    }

    public record Flag(CheatSignatures.Severity severity, String source, String detail) {
        @Override public String toString() {
            return severity.name().toLowerCase() + "/" + source + "(" + detail + ")";
        }
    }
}
