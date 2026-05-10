package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code damage{amount=X}} — deal {@code amount} damage to every entity
 * target, scaled by the context's {@link PtrSkillContext#power()}.
 *
 * <p>The {@code ignoreArmor} flag uses
 * {@link LivingEntity#setNoDamageTicks(int)} as a poor-man's
 * armor-bypass — call it before the damage application so the engine
 * doesn't dampen our hit.
 */
public final class DamageMechanic implements PtrSkillMechanic {

    private final double amount;
    private final boolean ignoreArmor;

    public DamageMechanic(double amount) {
        this(amount, false);
    }

    public DamageMechanic(double amount, boolean ignoreArmor) {
        if (amount < 0.0) {
            throw new IllegalArgumentException("amount must be >= 0, got " + amount);
        }
        this.amount = amount;
        this.ignoreArmor = ignoreArmor;
    }

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        double dmg = amount * ctx.power();
        for (Entity e : ctx.entityTargets()) {
            if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                continue;
            }
            if (ignoreArmor) {
                le.setNoDamageTicks(0);
            }
            le.damage(dmg, ctx.caster());
        }
    }

    @Override
    public @NotNull String label() {
        return "damage{amount=" + amount + (ignoreArmor ? ";ignorearmor=true" : "") + "}";
    }
}
