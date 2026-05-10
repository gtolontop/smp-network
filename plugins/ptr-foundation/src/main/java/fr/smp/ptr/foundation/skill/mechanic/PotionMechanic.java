package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import java.util.Objects;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * {@code potion{type=POISON;lvl=2;duration=100}} — apply a potion effect
 * to every entity target.
 */
public final class PotionMechanic implements PtrSkillMechanic {

    private final PotionEffectType type;
    private final int amplifier;
    private final int durationTicks;

    public PotionMechanic(@NotNull PotionEffectType type, int amplifier, int durationTicks) {
        this.type = Objects.requireNonNull(type, "type");
        if (amplifier < 0) {
            throw new IllegalArgumentException("amplifier must be >= 0, got " + amplifier);
        }
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be > 0, got " + durationTicks);
        }
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
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
            le.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true, true));
        }
    }

    @Override
    public @NotNull String label() {
        return "potion{type=" + type.getKey() + ";lvl=" + amplifier + ";d=" + durationTicks + "}";
    }
}
