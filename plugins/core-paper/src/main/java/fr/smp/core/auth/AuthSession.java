package fr.smp.core.auth;

import org.bukkit.Location;

/**
 * Volatile per-connection state. Lives only as long as the player is online.
 *
 * {@code authenticated=false} means the player is frozen at {@code freezeLoc}
 * and only auth commands are accepted from them.
 */
public final class AuthSession {

    public enum Stage {
        /** Player has joined but Paper hasn't decided what to do yet (very brief). */
        PENDING,
        /** Cracked player must run /login or /register. Movement frozen. */
        AWAITING,
        /** Authenticated — full access. */
        AUTHENTICATED
    }

    private Stage stage;
    private final boolean premium;
    private final long joinedAt;
    private Location freezeLoc;
    private int failedThisSession;
    private long lastReminderMs;
    private long authenticatedAt;
    private long accountRegisteredAt;
    private int interactions;

    public AuthSession(boolean premium, long joinedAt, Location freezeLoc) {
        this.stage = Stage.PENDING;
        this.premium = premium;
        this.joinedAt = joinedAt;
        this.freezeLoc = freezeLoc;
    }

    public Stage stage() { return stage; }
    public boolean premium() { return premium; }
    public long joinedAt() { return joinedAt; }
    public Location freezeLoc() { return freezeLoc; }
    public int failedThisSession() { return failedThisSession; }
    public long lastReminderMs() { return lastReminderMs; }
    public long authenticatedAt() { return authenticatedAt; }
    public long accountRegisteredAt() { return accountRegisteredAt; }
    public int interactions() { return interactions; }

    public boolean isAuthenticated() { return stage == Stage.AUTHENTICATED; }

    public void setStage(Stage stage) { this.stage = stage; }
    public void setFreezeLoc(Location freezeLoc) { this.freezeLoc = freezeLoc; }
    public void incrementFailed() { this.failedThisSession++; }
    public void resetFailed() { this.failedThisSession = 0; }
    public void setLastReminderMs(long lastReminderMs) { this.lastReminderMs = lastReminderMs; }
    public void setAuthenticatedAt(long authenticatedAt) { this.authenticatedAt = authenticatedAt; }
    public void setAccountRegisteredAt(long accountRegisteredAt) { this.accountRegisteredAt = accountRegisteredAt; }
    public void incrementInteractions() { this.interactions++; }
}
