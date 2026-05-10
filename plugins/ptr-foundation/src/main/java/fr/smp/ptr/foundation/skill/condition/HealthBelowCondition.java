package fr.smp.ptr.foundation.skill.condition;

import fr.smp.ptr.foundation.skill.PtrSkillCondition;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.jetbrains.annotations.NotNull;

/**
 * {@code healthbelow{p=X}} — the caster's current HP is at or below {@code
 * percent} of its max.
 */
public final class HealthBelowCondition implements PtrSkillCondition {

    private final double percent;

    public HealthBelowCondition(double percent) {
        if (percent < 0.0 || percent > 1.0) {
            throw new IllegalArgumentException("percent must be in [0,1], got " + percent);
        }
        this.percent = percent;
    }

    public double percent() {
        return percent;
    }

    @Override
    public boolean test(@NotNull PtrSkillContext ctx) {
        AttributeInstance attr = ctx.caster().getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? 20.0 : attr.getValue();
        if (max <= 0.0) {
            return true;
        }
        return ctx.caster().getHealth() <= max * percent;
    }

    @Override
    public @NotNull String label() {
        return "healthbelow{p=" + percent + "}";
    }
}
