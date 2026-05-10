package fr.smp.ptr.foundation.skill;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Per-(skill, caster) cooldown tracker, in wall-clock milliseconds.
 *
 * <p>Wall-clock is deliberate — region threads on Folia don't share a tick
 * counter and {@code System.currentTimeMillis()} is monotonically defined
 * by the JVM. The trade-off is that a paused server would not "pause" the
 * cooldown; this is fine for the foundation.
 */
public final class PtrCooldownStore {

    private final ConcurrentMap<String, Long> lastFireMillis = new ConcurrentHashMap<>();

    /**
     * Try to consume the cooldown for {@code skill} cast by {@code caster}.
     * Returns true if the cast may proceed; false if still on cooldown.
     */
    public boolean tryAcquire(
            @NotNull NamespacedKey skill, @NotNull UUID caster, long cooldownMillis) {
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(caster, "caster");
        if (cooldownMillis <= 0L) {
            return true;
        }
        String key = key(skill, caster);
        long now = System.currentTimeMillis();
        Long previous = lastFireMillis.get(key);
        if (previous != null && now < previous + cooldownMillis) {
            return false;
        }
        lastFireMillis.put(key, now);
        return true;
    }

    /** Force-reset the cooldown so the next call to {@link #tryAcquire} succeeds. */
    public void reset(@NotNull NamespacedKey skill, @NotNull UUID caster) {
        lastFireMillis.remove(key(skill, caster));
    }

    /** Drop everything for a caster (e.g. on entity removal). */
    public void resetAll(@NotNull UUID caster) {
        String suffix = ":" + caster;
        lastFireMillis.keySet().removeIf(k -> k.endsWith(suffix));
    }

    /** Drop everything (e.g. on plugin disable). */
    public void clear() {
        lastFireMillis.clear();
    }

    private static String key(NamespacedKey skill, UUID caster) {
        return skill + ":" + caster;
    }
}
