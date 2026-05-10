package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * {@code throw{velocity=H;velocityY=V}} — apply a knockback away from the
 * caster's origin. Useful as a punctuation after a slam.
 */
public final class ThrowMechanic implements PtrSkillMechanic {

    private final double horizontalVelocity;
    private final double verticalVelocity;

    public ThrowMechanic(double horizontalVelocity, double verticalVelocity) {
        this.horizontalVelocity = horizontalVelocity;
        this.verticalVelocity = verticalVelocity;
    }

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        for (Entity e : ctx.entityTargets()) {
            if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                continue;
            }
            Vector away =
                    le.getLocation().toVector().subtract(ctx.origin().toVector());
            if (away.lengthSquared() < 1.0e-6) {
                away = new Vector(0, 1, 0);
            } else {
                away.normalize();
            }
            Vector velocity =
                    new Vector(
                            away.getX() * horizontalVelocity * 0.1,
                            verticalVelocity * 0.1,
                            away.getZ() * horizontalVelocity * 0.1);
            le.setVelocity(le.getVelocity().add(velocity));
        }
    }

    @Override
    public @NotNull String label() {
        return "throw{h=" + horizontalVelocity + ";v=" + verticalVelocity + "}";
    }
}
