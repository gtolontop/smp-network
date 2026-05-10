package fr.smp.ptr.foundation.boss;

import fr.smp.ptr.foundation.registry.PtrIdentified;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side declaration of a boss.
 *
 * <p>Records the base entity type the boss rides on, the attribute overrides
 * (max health, attack damage, knockback resistance), the ordered list of
 * {@link BossPhase}s, and references to drop / music ids.
 *
 * <p>The foundation ships zero boss definitions. Content layers create
 * instances via {@link Builder}.
 */
public record BossDefinition(
        @NotNull NamespacedKey id,
        @NotNull Component displayName,
        @NotNull EntityType baseEntityType,
        @NotNull Map<Attribute, Double> attributes,
        @NotNull List<BossPhase> phases,
        @Nullable NamespacedKey dropTableId,
        @Nullable NamespacedKey musicId,
        double arenaRadius)
        implements PtrIdentified {

    public BossDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(baseEntityType, "baseEntityType");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(phases, "phases");
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("phases must not be empty");
        }
        if (arenaRadius <= 0.0) {
            throw new IllegalArgumentException("arenaRadius must be positive");
        }
        attributes = Map.copyOf(attributes);
        phases = List.copyOf(phases);
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link BossDefinition}. */
    public static final class Builder {

        private NamespacedKey id;
        private Component displayName;
        private EntityType baseEntityType;
        private final java.util.LinkedHashMap<Attribute, Double> attributes =
                new java.util.LinkedHashMap<>();
        private final java.util.ArrayList<BossPhase> phases = new java.util.ArrayList<>();
        private @Nullable NamespacedKey dropTableId;
        private @Nullable NamespacedKey musicId;
        private double arenaRadius = 16.0;

        private Builder() {}

        public @NotNull Builder id(@NotNull NamespacedKey id) {
            this.id = id;
            return this;
        }

        public @NotNull Builder displayName(@NotNull Component name) {
            this.displayName = name;
            return this;
        }

        public @NotNull Builder baseEntityType(@NotNull EntityType type) {
            this.baseEntityType = type;
            return this;
        }

        public @NotNull Builder attribute(@NotNull Attribute attr, double value) {
            this.attributes.put(attr, value);
            return this;
        }

        public @NotNull Builder addPhase(@NotNull BossPhase phase) {
            this.phases.add(phase);
            return this;
        }

        public @NotNull Builder dropTable(@NotNull NamespacedKey id) {
            this.dropTableId = id;
            return this;
        }

        public @NotNull Builder music(@NotNull NamespacedKey id) {
            this.musicId = id;
            return this;
        }

        public @NotNull Builder arenaRadius(double radius) {
            this.arenaRadius = radius;
            return this;
        }

        public @NotNull BossDefinition build() {
            return new BossDefinition(
                    id,
                    displayName,
                    baseEntityType,
                    attributes,
                    phases,
                    dropTableId,
                    musicId,
                    arenaRadius);
        }
    }
}
