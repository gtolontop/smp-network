package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import java.util.Objects;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code effect:particles} — spawn {@code count} particles at every target
 * (entity or location), with optional dust colour.
 */
public final class ParticleMechanic implements PtrSkillMechanic {

    private final Particle particle;
    private final int count;
    private final double spread;
    private final @Nullable Color dustColor;

    public ParticleMechanic(@NotNull Particle particle, int count, double spread) {
        this(particle, count, spread, null);
    }

    public ParticleMechanic(
            @NotNull Particle particle, int count, double spread, @Nullable Color dustColor) {
        this.particle = Objects.requireNonNull(particle, "particle");
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, got " + count);
        }
        if (spread < 0.0) {
            throw new IllegalArgumentException("spread must be >= 0, got " + spread);
        }
        this.count = count;
        this.spread = spread;
        this.dustColor = dustColor;
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
        if (dustColor != null && particle == Particle.DUST) {
            at.getWorld()
                    .spawnParticle(
                            particle,
                            at,
                            count,
                            spread,
                            spread,
                            spread,
                            0.0,
                            new Particle.DustOptions(dustColor, 1.0f));
        } else {
            at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, 0.0);
        }
    }

    @Override
    public @NotNull String label() {
        return "effect:particles{p=" + particle.name() + ";n=" + count + "}";
    }
}
