package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.boss.Telegraph;
import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code telegraph} — render an existing {@link Telegraph} at every
 * location/entity target. Bridges the boss telegraph catalogue into the
 * skill engine.
 */
public final class TelegraphMechanic implements PtrSkillMechanic {

    private final Telegraph telegraph;
    private final double radius;
    private final int warmupTicks;

    public TelegraphMechanic(@NotNull Telegraph telegraph, double radius, int warmupTicks) {
        this.telegraph = Objects.requireNonNull(telegraph, "telegraph");
        if (radius <= 0.0) {
            throw new IllegalArgumentException("radius must be > 0, got " + radius);
        }
        if (warmupTicks <= 0) {
            throw new IllegalArgumentException("warmupTicks must be > 0, got " + warmupTicks);
        }
        this.radius = radius;
        this.warmupTicks = warmupTicks;
    }

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        Audience audience = audienceOf(ctx);
        boolean anyTarget = false;
        for (Entity e : ctx.entityTargets()) {
            telegraph.render(plugin, scheduler, audience, e.getLocation(), radius, warmupTicks);
            anyTarget = true;
        }
        for (Location l : ctx.locationTargets()) {
            telegraph.render(plugin, scheduler, audience, l, radius, warmupTicks);
            anyTarget = true;
        }
        if (!anyTarget) {
            telegraph.render(plugin, scheduler, audience, ctx.origin(), radius, warmupTicks);
        }
    }

    private static Audience audienceOf(PtrSkillContext ctx) {
        return Audience.audience(
                ctx.entityTargets().stream()
                        .filter(Player.class::isInstance)
                        .map(Audience.class::cast)
                        .toList());
    }

    @Override
    public @NotNull String label() {
        return "telegraph{id=" + telegraph.id() + ";r=" + radius + ";w=" + warmupTicks + "}";
    }
}
