package fr.smp.ptr.foundation.skill.targeter;

import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillTargeter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * {@code @LivingInRadius{r=X}} — every {@link LivingEntity} (incl. mobs)
 * within {@code radius} blocks of the caster, excluding the caster itself.
 */
public final class LivingInRadiusTargeter implements PtrSkillTargeter {

    private final double radius;
    private final boolean includeCaster;

    public LivingInRadiusTargeter(double radius) {
        this(radius, false);
    }

    public LivingInRadiusTargeter(double radius, boolean includeCaster) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("radius must be > 0, got " + radius);
        }
        this.radius = radius;
        this.includeCaster = includeCaster;
    }

    public double radius() {
        return radius;
    }

    @Override
    public @NotNull Collection<Entity> entities(@NotNull PtrSkillContext ctx) {
        Location origin = ctx.origin();
        List<Entity> out = new ArrayList<>();
        double rSq = radius * radius;
        for (Entity nearby :
                origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity le)) {
                continue;
            }
            if (!includeCaster && le.equals(ctx.caster())) {
                continue;
            }
            if (le.getLocation().distanceSquared(origin) <= rSq) {
                out.add(le);
            }
        }
        return out;
    }

    @Override
    public @NotNull String label() {
        return "@LivingInRadius{r=" + radius + (includeCaster ? ";self=true" : "") + "}";
    }
}
