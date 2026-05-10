package fr.smp.ptr.foundation.boss;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One phase of a {@link BossDefinition}.
 *
 * <p>{@code hpThreshold} is the HP value at which the phase becomes active
 * (inclusive going downward). The first phase typically has
 * {@code hpThreshold = baseHp} (always active at full HP); subsequent
 * phases trigger as HP drops past their thresholds.
 *
 * <p>{@code skills} is the list of {@link TriggeredSkill}s active for the
 * duration of the phase. The legacy {@code onEnter} / {@code onTick}
 * callbacks are still supported and run alongside the skill system.
 *
 * @param hpThreshold HP value below which this phase activates
 * @param name human-readable phase name (used in chat barks)
 * @param telegraphIds telegraph keys to run during this phase
 * @param skills triggered skills active for this phase
 * @param music optional jukebox_song id played as the phase starts
 * @param onEnter hook called once when the phase activates ({@code null} = no-op)
 * @param onTick hook called every PhaseController tick while active ({@code null} = no-op)
 */
public record BossPhase(
        double hpThreshold,
        @NotNull Component name,
        @NotNull List<NamespacedKey> telegraphIds,
        @NotNull List<TriggeredSkill> skills,
        @Nullable NamespacedKey music,
        @Nullable Consumer<LivingEntity> onEnter,
        @Nullable Consumer<LivingEntity> onTick) {

    public BossPhase {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(telegraphIds, "telegraphIds");
        Objects.requireNonNull(skills, "skills");
        telegraphIds = List.copyOf(telegraphIds);
        skills = List.copyOf(skills);
    }

    /**
     * Convenience: legacy 6-arg constructor with no skills. Used by older
     * code that pre-dates the skill system.
     */
    public BossPhase(
            double hpThreshold,
            @NotNull Component name,
            @NotNull List<NamespacedKey> telegraphIds,
            @Nullable NamespacedKey music,
            @Nullable Consumer<LivingEntity> onEnter,
            @Nullable Consumer<LivingEntity> onTick) {
        this(hpThreshold, name, telegraphIds, List.of(), music, onEnter, onTick);
    }
}
