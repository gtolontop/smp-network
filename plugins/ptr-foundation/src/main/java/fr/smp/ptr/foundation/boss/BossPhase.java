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
 * @param hpThreshold HP value below which this phase activates
 * @param name human-readable phase name (used in chat barks)
 * @param telegraphIds telegraph keys to run during this phase
 * @param music optional jukebox_song id played as the phase starts
 * @param onEnter hook called once when the phase activates ({@code null} = no-op)
 * @param onTick hook called every PhaseController tick while active ({@code null} = no-op)
 */
public record BossPhase(
        double hpThreshold,
        @NotNull Component name,
        @NotNull List<NamespacedKey> telegraphIds,
        @Nullable NamespacedKey music,
        @Nullable Consumer<LivingEntity> onEnter,
        @Nullable Consumer<LivingEntity> onTick) {

    public BossPhase {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(telegraphIds, "telegraphIds");
        telegraphIds = List.copyOf(telegraphIds);
    }
}
