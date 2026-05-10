package fr.smp.ptr.foundation.skill;

import java.util.Objects;

/**
 * One step in a {@link PtrSkill}'s ordered script.
 *
 * <p>Either a mechanic (executed inline) or a delay (the engine yields, then
 * resumes at the next step). Delays are scheduled on the caster's entity
 * scheduler so they follow the caster across region boundaries.
 */
public sealed interface PtrSkillStep {

    /** Execute a mechanic. */
    record Mechanic(PtrSkillMechanic mechanic) implements PtrSkillStep {
        public Mechanic {
            Objects.requireNonNull(mechanic, "mechanic");
        }
    }

    /** Yield for {@code ticks} entity-scheduler ticks before resuming the script. */
    record Delay(long ticks) implements PtrSkillStep {
        public Delay {
            if (ticks <= 0) {
                throw new IllegalArgumentException("delay ticks must be > 0, got " + ticks);
            }
        }
    }
}
