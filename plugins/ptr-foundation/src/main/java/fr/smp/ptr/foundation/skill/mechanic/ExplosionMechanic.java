package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code effect:explosion} — visual + (optional) terrain-damaging explosion
 * at each target. Set {@code breakBlocks = false} to keep the build area
 * intact — the foundation default.
 */
public final class ExplosionMechanic implements PtrSkillMechanic {

    private final float power;
    private final boolean breakBlocks;
    private final boolean setFire;

    public ExplosionMechanic(float power) {
        this(power, false, false);
    }

    public ExplosionMechanic(float power, boolean breakBlocks, boolean setFire) {
        if (power <= 0.0f) {
            throw new IllegalArgumentException("power must be > 0, got " + power);
        }
        this.power = power;
        this.breakBlocks = breakBlocks;
        this.setFire = setFire;
    }

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        for (Entity e : ctx.entityTargets()) {
            spawn(e.getLocation());
        }
        for (Location l : ctx.locationTargets()) {
            spawn(l);
        }
        if (ctx.entityTargets().isEmpty() && ctx.locationTargets().isEmpty()) {
            spawn(ctx.origin());
        }
    }

    private void spawn(Location at) {
        at.getWorld().createExplosion(at, power, setFire, breakBlocks);
    }

    @Override
    public @NotNull String label() {
        return "effect:explosion{p=" + power + ";break=" + breakBlocks + "}";
    }
}
