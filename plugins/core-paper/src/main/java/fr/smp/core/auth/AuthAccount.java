package fr.smp.core.auth;

import java.util.UUID;

/**
 * Persistent record for a player's authentication state.
 *
 * {@code passwordHash} is null for premium-only accounts (the Mojang session
 * IS the credential). {@code premiumUuid} is set the first time a name passes
 * online-mode handshake; once set, the player is considered the legitimate
 * owner of that username and any cracked password attached previously is
 * wiped on the next premium join.
 */
public final class AuthAccount {

    private final String nameLower;
    private String passwordHash;
    private UUID premiumUuid;
    private UUID crackedUuid;
    private final long registeredAt;
    private long lastLogin;
    private String lastIp;
    private int failedAttempts;
    private long lockedUntil;
    private boolean mustRechange;

    public AuthAccount(String nameLower,
                       String passwordHash,
                       UUID premiumUuid,
                       UUID crackedUuid,
                       long registeredAt,
                       long lastLogin,
                       String lastIp,
                       int failedAttempts,
                       long lockedUntil,
                       boolean mustRechange) {
        this.nameLower = nameLower;
        this.passwordHash = passwordHash;
        this.premiumUuid = premiumUuid;
        this.crackedUuid = crackedUuid;
        this.registeredAt = registeredAt;
        this.lastLogin = lastLogin;
        this.lastIp = lastIp;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.mustRechange = mustRechange;
    }

    public String nameLower() { return nameLower; }
    public String passwordHash() { return passwordHash; }
    public UUID premiumUuid() { return premiumUuid; }
    public UUID crackedUuid() { return crackedUuid; }
    public long registeredAt() { return registeredAt; }
    public long lastLogin() { return lastLogin; }
    public String lastIp() { return lastIp; }
    public int failedAttempts() { return failedAttempts; }
    public long lockedUntil() { return lockedUntil; }
    public boolean mustRechange() { return mustRechange; }

    public boolean hasPassword() { return passwordHash != null && !passwordHash.isEmpty(); }
    public boolean isPremiumOnly() { return !hasPassword() && premiumUuid != null; }
    public boolean isLocked(long now) { return lockedUntil > now; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setPremiumUuid(UUID premiumUuid) { this.premiumUuid = premiumUuid; }
    public void setCrackedUuid(UUID crackedUuid) { this.crackedUuid = crackedUuid; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public void setLockedUntil(long lockedUntil) { this.lockedUntil = lockedUntil; }
    public void setMustRechange(boolean mustRechange) { this.mustRechange = mustRechange; }
}
