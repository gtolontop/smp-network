package fr.smp.ptr.foundation.skill;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.registry.PtrIdentified;
import fr.smp.ptr.foundation.registry.PtrIds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Composable skill: pre-cast conditions, a targeter, and an ordered script
 * of {@link PtrSkillStep}s (mechanics interleaved with delays).
 *
 * <p>Cast lifecycle:
 *
 * <ol>
 *   <li>Each {@link PtrSkillCondition} is tested. First false → cast aborts.
 *   <li>The {@link PtrSkillTargeter} resolves entity / location targets into
 *       the {@link PtrSkillContext}.
 *   <li>Steps are iterated. Mechanic steps execute on the current entity
 *       thread. Delay steps yield to the entity scheduler and resume the
 *       remaining steps after the delay.
 * </ol>
 *
 * <p>Use {@link Builder} to construct.
 */
public final class PtrSkill implements PtrIdentified {

    private final NamespacedKey id;
    private final Component displayName;
    private final List<PtrSkillCondition> conditions;
    private final PtrSkillTargeter targeter;
    private final List<PtrSkillStep> steps;

    private PtrSkill(
            @NotNull NamespacedKey id,
            @NotNull Component displayName,
            @NotNull List<PtrSkillCondition> conditions,
            @NotNull PtrSkillTargeter targeter,
            @NotNull List<PtrSkillStep> steps) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.conditions = List.copyOf(conditions);
        this.targeter = Objects.requireNonNull(targeter, "targeter");
        this.steps = List.copyOf(steps);
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("skill " + id + " has no steps");
        }
    }

    @Override
    public @NotNull NamespacedKey id() {
        return id;
    }

    @Override
    public @NotNull Component displayName() {
        return displayName;
    }

    public @NotNull List<PtrSkillCondition> conditions() {
        return conditions;
    }

    public @NotNull PtrSkillTargeter targeter() {
        return targeter;
    }

    public @NotNull List<PtrSkillStep> steps() {
        return steps;
    }

    /**
     * Cast this skill in {@code ctx}'s caster's region. Returns immediately
     * if conditions fail; delays in the step list run asynchronously via
     * the entity scheduler.
     */
    public void cast(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(ctx, "ctx");

        for (PtrSkillCondition c : conditions) {
            if (!c.test(ctx)) {
                return;
            }
        }

        Collection<Entity> entities = targeter.entities(ctx);
        Collection<Location> locations = targeter.locations(ctx);
        ctx.entityTargets(entities);
        ctx.locationTargets(locations);

        runFrom(plugin, scheduler, ctx, 0);
    }

    private void runFrom(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx,
            int startIndex) {
        for (int i = startIndex; i < steps.size(); i++) {
            if (ctx.isCancelled()) {
                return;
            }
            PtrSkillStep step = steps.get(i);
            if (step instanceof PtrSkillStep.Delay d) {
                int next = i + 1;
                scheduler.runOnEntityLater(
                        ctx.caster(),
                        () -> runFrom(plugin, scheduler, ctx, next),
                        null,
                        d.ticks());
                return;
            }
            if (step instanceof PtrSkillStep.Mechanic m) {
                try {
                    m.mechanic().execute(plugin, scheduler, ctx);
                } catch (Throwable t) {
                    plugin.getLogger()
                            .warning(
                                    () ->
                                            "PtrSkill["
                                                    + id
                                                    + "] mechanic "
                                                    + m.mechanic().label()
                                                    + " threw: "
                                                    + t.getMessage());
                }
            }
        }
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** Fluent builder. */
    public static final class Builder {

        private @Nullable NamespacedKey id;
        private @Nullable Component displayName;
        private final List<PtrSkillCondition> conditions = new ArrayList<>();
        private @Nullable PtrSkillTargeter targeter;
        private final List<PtrSkillStep> steps = new ArrayList<>();

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

        public @NotNull Builder addCondition(@NotNull PtrSkillCondition c) {
            conditions.add(Objects.requireNonNull(c, "condition"));
            return this;
        }

        public @NotNull Builder targeter(@NotNull PtrSkillTargeter t) {
            this.targeter = Objects.requireNonNull(t, "targeter");
            return this;
        }

        public @NotNull Builder addMechanic(@NotNull PtrSkillMechanic m) {
            steps.add(new PtrSkillStep.Mechanic(Objects.requireNonNull(m, "mechanic")));
            return this;
        }

        public @NotNull Builder delay(long ticks) {
            steps.add(new PtrSkillStep.Delay(ticks));
            return this;
        }

        public @NotNull PtrSkill build() {
            if (id == null) {
                throw new IllegalStateException("skill id() is required");
            }
            if (displayName == null) {
                throw new IllegalStateException("skill displayName() is required");
            }
            if (targeter == null) {
                throw new IllegalStateException("skill targeter() is required");
            }
            return new PtrSkill(id, displayName, conditions, targeter, steps);
        }
    }
}
