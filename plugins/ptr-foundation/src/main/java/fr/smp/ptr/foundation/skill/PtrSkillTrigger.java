package fr.smp.ptr.foundation.skill;

import org.jetbrains.annotations.NotNull;

/**
 * When does a {@link PtrSkill} activate?
 *
 * <p>The foundation only defines trigger vocabulary. Content plugins decide
 * which dispatcher owns these triggers.
 */
public sealed interface PtrSkillTrigger {

    /** Fires once when a scripted entity or sequence starts. */
    record OnSpawn() implements PtrSkillTrigger {}

    /** Fires once when the controller stops (entity becomes invalid or dead). */
    record OnDeath() implements PtrSkillTrigger {}

    /**
     * Fires every {@code periodTicks} entity-scheduler ticks. The first
     * fire happens after one full period, not immediately.
     */
    record OnTimer(long periodTicks) implements PtrSkillTrigger {
        public OnTimer {
            if (periodTicks <= 0) {
                throw new IllegalArgumentException("periodTicks must be > 0, got " + periodTicks);
            }
        }
    }

    /**
     * Fires once the first time the caster's health drops at or below
     * {@code percent} of its current max.
     */
    record OnHpBelow(double percent) implements PtrSkillTrigger {
        public OnHpBelow {
            if (percent < 0.0 || percent > 1.0) {
                throw new IllegalArgumentException(
                        "percent must be in [0, 1], got " + percent);
            }
        }
    }

    /** Short label used in logs / `/ptrf debug`. */
    @NotNull
    default String label() {
        return switch (this) {
            case OnSpawn ignored -> "onSpawn";
            case OnDeath ignored -> "onDeath";
            case OnTimer t -> "onTimer:" + t.periodTicks() + "t";
            case OnHpBelow h -> "onHpBelow:" + h.percent();
        };
    }
}
