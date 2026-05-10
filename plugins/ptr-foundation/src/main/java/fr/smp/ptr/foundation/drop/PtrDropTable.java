package fr.smp.ptr.foundation.drop;

import fr.smp.ptr.foundation.registry.PtrIdentified;
import fr.smp.ptr.foundation.registry.PtrIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Drop table that splits loot four ways:
 *
 * <ul>
 *   <li><b>Guaranteed</b> — dropped at the death location, no player attribution.
 *   <li><b>Random</b> — rolled once at the death location, no attribution.
 *   <li><b>Rank</b> — given to the top-N damage-dealers, one entry per rank.
 *   <li><b>Participation</b> — given to every player whose damage % is above
 *       a threshold.
 * </ul>
 *
 * <p>Inspired by the {@code DropTable} concept from premium boss plugins —
 * the per-player attribution is what makes public boss fights feel fair.
 */
public final class PtrDropTable implements PtrIdentified {

    private final NamespacedKey id;
    private final Component displayName;
    private final List<ItemStack> guaranteed;
    private final List<RandomDrop> randoms;
    private final Map<Integer, ItemStack> rankDrops;
    private final List<ParticipationDrop> participation;

    private PtrDropTable(
            NamespacedKey id,
            Component displayName,
            List<ItemStack> guaranteed,
            List<RandomDrop> randoms,
            Map<Integer, ItemStack> rankDrops,
            List<ParticipationDrop> participation) {
        this.id = id;
        this.displayName = displayName;
        this.guaranteed = List.copyOf(guaranteed);
        this.randoms = List.copyOf(randoms);
        this.rankDrops = Map.copyOf(rankDrops);
        this.participation = List.copyOf(participation);
    }

    @Override
    public @NotNull NamespacedKey id() {
        return id;
    }

    @Override
    public @NotNull Component displayName() {
        return displayName;
    }

    /** Roll the table against a leaderboard, returning per-player loot. */
    public @NotNull Roll roll(@NotNull List<DamageTracker.Entry> leaderboard) {
        return roll(leaderboard, ThreadLocalRandom.current());
    }

    /** Same as {@link #roll(List)} but with an explicit {@link Random} for tests. */
    public @NotNull Roll roll(@NotNull List<DamageTracker.Entry> leaderboard, @NotNull Random rng) {
        Objects.requireNonNull(leaderboard, "leaderboard");
        Objects.requireNonNull(rng, "rng");

        List<ItemStack> ground = new ArrayList<>();
        Map<UUID, List<ItemStack>> perPlayer = new LinkedHashMap<>();

        for (ItemStack g : guaranteed) {
            ground.add(g.clone());
        }
        for (RandomDrop r : randoms) {
            if (rng.nextDouble() < r.probability()) {
                ground.add(r.stack().clone());
            }
        }

        double total = leaderboard.stream().mapToDouble(DamageTracker.Entry::damage).sum();
        for (int i = 0; i < leaderboard.size(); i++) {
            DamageTracker.Entry entry = leaderboard.get(i);
            int rank = i + 1;
            ItemStack rankItem = rankDrops.get(rank);
            if (rankItem != null) {
                perPlayer.computeIfAbsent(entry.player(), k -> new ArrayList<>())
                        .add(rankItem.clone());
            }
            if (total > 0.0) {
                double percent = entry.damage() / total;
                for (ParticipationDrop p : participation) {
                    if (percent >= p.minDamagePercent()) {
                        perPlayer.computeIfAbsent(entry.player(), k -> new ArrayList<>())
                                .add(p.stack().clone());
                    }
                }
            }
        }

        return new Roll(Collections.unmodifiableList(ground), Collections.unmodifiableMap(perPlayer));
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** One random-roll drop entry. */
    public record RandomDrop(@NotNull ItemStack stack, double probability) {
        public RandomDrop {
            Objects.requireNonNull(stack, "stack");
            if (probability < 0.0 || probability > 1.0) {
                throw new IllegalArgumentException(
                        "probability must be in [0,1], got " + probability);
            }
        }
    }

    /** One participation-threshold drop entry. */
    public record ParticipationDrop(@NotNull ItemStack stack, double minDamagePercent) {
        public ParticipationDrop {
            Objects.requireNonNull(stack, "stack");
            if (minDamagePercent < 0.0 || minDamagePercent > 1.0) {
                throw new IllegalArgumentException(
                        "minDamagePercent must be in [0,1], got " + minDamagePercent);
            }
        }
    }

    /** Roll result: world drops + per-player drops. */
    public record Roll(
            @NotNull List<ItemStack> worldDrops,
            @NotNull Map<UUID, List<ItemStack>> perPlayerDrops) {

        public Roll {
            Objects.requireNonNull(worldDrops, "worldDrops");
            Objects.requireNonNull(perPlayerDrops, "perPlayerDrops");
        }
    }

    /** Fluent builder. */
    public static final class Builder {

        private NamespacedKey id;
        private Component displayName;
        private final List<ItemStack> guaranteed = new ArrayList<>();
        private final List<RandomDrop> randoms = new ArrayList<>();
        private final Map<Integer, ItemStack> rankDrops = new LinkedHashMap<>();
        private final List<ParticipationDrop> participation = new ArrayList<>();

        private Builder() {}

        public @NotNull Builder id(@NotNull NamespacedKey id) {
            this.id = id;
            return this;
        }

        public @NotNull Builder id(@NotNull String path) {
            this.id = PtrIds.key(path);
            return this;
        }

        public @NotNull Builder displayName(@NotNull Component name) {
            this.displayName = name;
            return this;
        }

        public @NotNull Builder addGuaranteed(@NotNull ItemStack stack) {
            guaranteed.add(Objects.requireNonNull(stack, "stack"));
            return this;
        }

        public @NotNull Builder addRandom(@NotNull ItemStack stack, double probability) {
            randoms.add(new RandomDrop(stack, probability));
            return this;
        }

        public @NotNull Builder addRankDrop(int rank, @NotNull ItemStack stack) {
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be >= 1, got " + rank);
            }
            rankDrops.put(rank, Objects.requireNonNull(stack, "stack"));
            return this;
        }

        public @NotNull Builder addParticipation(@NotNull ItemStack stack, double minDamagePercent) {
            participation.add(new ParticipationDrop(stack, minDamagePercent));
            return this;
        }

        public @NotNull PtrDropTable build() {
            if (id == null) {
                throw new IllegalStateException("drop table id() required");
            }
            if (displayName == null) {
                throw new IllegalStateException("drop table displayName() required");
            }
            return new PtrDropTable(
                    id, displayName, guaranteed, randoms, rankDrops, participation);
        }
    }
}
