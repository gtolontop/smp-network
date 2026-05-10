package fr.smp.ptr.foundation.drop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jetbrains.annotations.NotNull;

/**
 * Tracks total damage dealt to each foundation mob per player.
 *
 * <p>The {@link fr.smp.ptr.foundation.boss.PhaseController} or a future
 * boss-listener wires {@code addDamage()} from {@code
 * EntityDamageByEntityEvent}. {@link DropDispatcher} consumes the resulting
 * leaderboard at death.
 *
 * <p>Thread-safe — region threads and event threads can both write.
 */
public final class DamageTracker {

    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Double>> byBoss =
            new ConcurrentHashMap<>();

    /** Record {@code amount} damage dealt by {@code player} to {@code boss}. */
    public void addDamage(@NotNull UUID boss, @NotNull UUID player, double amount) {
        Objects.requireNonNull(boss, "boss");
        Objects.requireNonNull(player, "player");
        if (amount <= 0.0) {
            return;
        }
        byBoss.computeIfAbsent(boss, b -> new ConcurrentHashMap<>())
                .merge(player, amount, Double::sum);
    }

    /** Damage dealt by one player to one boss (0 if none). */
    public double damage(@NotNull UUID boss, @NotNull UUID player) {
        ConcurrentMap<UUID, Double> map = byBoss.get(boss);
        return map == null ? 0.0 : map.getOrDefault(player, 0.0);
    }

    /** Total damage taken by the boss across every player. */
    public double totalDamage(@NotNull UUID boss) {
        ConcurrentMap<UUID, Double> map = byBoss.get(boss);
        if (map == null) {
            return 0.0;
        }
        return map.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Sorted leaderboard for one boss — highest damage first. Returns an
     * immutable list of {@link Entry} records.
     */
    public @NotNull List<Entry> leaderboard(@NotNull UUID boss) {
        ConcurrentMap<UUID, Double> map = byBoss.get(boss);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>(map.size());
        for (Map.Entry<UUID, Double> e : map.entrySet()) {
            entries.add(new Entry(e.getKey(), e.getValue()));
        }
        entries.sort(Comparator.comparingDouble(Entry::damage).reversed());
        return Collections.unmodifiableList(entries);
    }

    /** Drop the leaderboard for {@code boss} (e.g. on entity removal). */
    public void clear(@NotNull UUID boss) {
        byBoss.remove(boss);
    }

    /** Drop everything (plugin disable). */
    public void clearAll() {
        byBoss.clear();
    }

    /** One leaderboard line. */
    public record Entry(@NotNull UUID player, double damage) {

        public Entry {
            Objects.requireNonNull(player, "player");
        }
    }
}
