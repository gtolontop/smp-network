package fr.smp.ptr.foundation.api.events;

import fr.smp.ptr.foundation.skill.PtrSkill;
import fr.smp.ptr.foundation.skill.PtrSkillTrigger;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a skill begins executing. Listeners may cancel to block the
 * cast (e.g. silence debuff, world rule).
 */
public final class PtrSkillCastEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PtrSkill skill;
    private final LivingEntity caster;
    private final PtrSkillTrigger trigger;
    private boolean cancelled = false;

    public PtrSkillCastEvent(
            @NotNull PtrSkill skill,
            @NotNull LivingEntity caster,
            @NotNull PtrSkillTrigger trigger) {
        this.skill = Objects.requireNonNull(skill, "skill");
        this.caster = Objects.requireNonNull(caster, "caster");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
    }

    public @NotNull PtrSkill skill() {
        return skill;
    }

    public @NotNull LivingEntity caster() {
        return caster;
    }

    public @NotNull PtrSkillTrigger trigger() {
        return trigger;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
