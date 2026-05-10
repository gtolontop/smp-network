package fr.smp.ptr.foundation.skill;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime context threaded through a single skill cast.
 *
 * <p>Equivalent to the {@code SkillMetadata} concept from inspiration:
 * carries the caster, origin location, trigger, current entity / location
 * targets, and a string-keyed variable bag.
 *
 * <p>Instances are mutable but single-threaded — they live on the entity
 * scheduler thread of the caster.
 */
public final class PtrSkillContext {

    private final LivingEntity caster;
    private final PtrSkillTrigger trigger;
    private final Map<String, Object> variables = new HashMap<>();
    private Location origin;
    private @Nullable Entity triggerEntity;
    private @NotNull Collection<Entity> entityTargets = List.of();
    private @NotNull Collection<Location> locationTargets = List.of();
    private double power = 1.0;
    private boolean cancelled = false;

    public PtrSkillContext(@NotNull LivingEntity caster, @NotNull PtrSkillTrigger trigger) {
        this.caster = Objects.requireNonNull(caster, "caster");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.origin = caster.getLocation();
    }

    public @NotNull LivingEntity caster() {
        return caster;
    }

    public @NotNull PtrSkillTrigger trigger() {
        return trigger;
    }

    public @NotNull Location origin() {
        return origin.clone();
    }

    public @NotNull PtrSkillContext origin(@NotNull Location origin) {
        this.origin = Objects.requireNonNull(origin, "origin").clone();
        return this;
    }

    public @NotNull Optional<Entity> triggerEntity() {
        return Optional.ofNullable(triggerEntity);
    }

    public @NotNull PtrSkillContext triggerEntity(@Nullable Entity entity) {
        this.triggerEntity = entity;
        return this;
    }

    public @NotNull Collection<Entity> entityTargets() {
        return entityTargets;
    }

    public @NotNull PtrSkillContext entityTargets(@NotNull Collection<Entity> targets) {
        this.entityTargets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        return this;
    }

    public @NotNull Collection<Location> locationTargets() {
        return locationTargets;
    }

    public @NotNull PtrSkillContext locationTargets(@NotNull Collection<Location> targets) {
        this.locationTargets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        return this;
    }

    public double power() {
        return power;
    }

    public @NotNull PtrSkillContext power(double value) {
        this.power = value;
        return this;
    }

    /** Mark the rest of the cast cancelled. Mechanics that respect this should short-circuit. */
    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public @NotNull PtrSkillContext setVar(@NotNull String key, @Nullable Object value) {
        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
        return this;
    }

    public @NotNull Optional<Object> getVar(@NotNull String key) {
        return Optional.ofNullable(variables.get(key));
    }

    public @NotNull Map<String, Object> variables() {
        return Collections.unmodifiableMap(variables);
    }
}
