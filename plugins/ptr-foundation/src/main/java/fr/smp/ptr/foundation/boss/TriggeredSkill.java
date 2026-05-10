package fr.smp.ptr.foundation.boss;

import fr.smp.ptr.foundation.skill.PtrSkill;
import fr.smp.ptr.foundation.skill.PtrSkillTrigger;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Bundles a {@link PtrSkillTrigger} with the {@link PtrSkill} to fire and
 * its per-caster cooldown.
 *
 * <p>A {@link BossPhase} carries a list of these and the {@link
 * PhaseController} dispatches them on the entity scheduler tick.
 *
 * @param trigger when the skill activates within this phase
 * @param skill the skill to cast
 * @param cooldownMillis minimum interval between casts per caster (0 = no cooldown)
 */
public record TriggeredSkill(
        @NotNull PtrSkillTrigger trigger, @NotNull PtrSkill skill, long cooldownMillis) {

    public TriggeredSkill {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(skill, "skill");
        if (cooldownMillis < 0L) {
            throw new IllegalArgumentException("cooldownMillis must be >= 0, got " + cooldownMillis);
        }
    }

    /** No-cooldown convenience. */
    public TriggeredSkill(@NotNull PtrSkillTrigger trigger, @NotNull PtrSkill skill) {
        this(trigger, skill, 0L);
    }
}
