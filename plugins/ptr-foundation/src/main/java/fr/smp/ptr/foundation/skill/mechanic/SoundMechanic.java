package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code effect:sound{s=X;volume=V;pitch=P}} — play the sound at every
 * target. Defaults to {@code SoundCategory.HOSTILE} so the player can mute
 * boss sounds via the vanilla audio slider.
 */
public final class SoundMechanic implements PtrSkillMechanic {

    private final Sound sound;
    private final float volume;
    private final float pitch;
    private final SoundCategory category;

    public SoundMechanic(@NotNull Sound sound, float volume, float pitch) {
        this(sound, volume, pitch, SoundCategory.HOSTILE);
    }

    public SoundMechanic(
            @NotNull Sound sound,
            float volume,
            float pitch,
            @NotNull SoundCategory category) {
        this.sound = Objects.requireNonNull(sound, "sound");
        this.volume = volume;
        this.pitch = pitch;
        this.category = Objects.requireNonNull(category, "category");
    }

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        for (Entity e : ctx.entityTargets()) {
            e.getWorld().playSound(e.getLocation(), sound, category, volume, pitch);
        }
        for (Location l : ctx.locationTargets()) {
            l.getWorld().playSound(l, sound, category, volume, pitch);
        }
        if (ctx.entityTargets().isEmpty() && ctx.locationTargets().isEmpty()) {
            Location at = ctx.origin();
            at.getWorld().playSound(at, sound, category, volume, pitch);
        }
    }

    @Override
    public @NotNull String label() {
        return "effect:sound{s=" + sound + ";v=" + volume + ";p=" + pitch + "}";
    }
}
