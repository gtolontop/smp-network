package fr.smp.ptr.foundation.skill.condition;

import fr.smp.ptr.foundation.skill.PtrSkillCondition;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * {@code targetwithin{d=X}} — at least one entity target is within {@code
 * distance} blocks of the caster.
 *
 * <p>Evaluated AFTER the targeter resolves. Useful as a final gate to skip
 * "swing at nothing" skills when no target is actually in range.
 */
public final class TargetWithinCondition implements PtrSkillCondition {

    private final double distance;

    public TargetWithinCondition(double distance) {
        if (distance <= 0.0) {
            throw new IllegalArgumentException("distance must be > 0, got " + distance);
        }
        this.distance = distance;
    }

    @Override
    public boolean test(@NotNull PtrSkillContext ctx) {
        double dSq = distance * distance;
        for (Entity e : ctx.entityTargets()) {
            if (e.getLocation().distanceSquared(ctx.origin()) <= dSq) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull String label() {
        return "targetwithin{d=" + distance + "}";
    }
}
