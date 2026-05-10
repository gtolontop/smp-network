package fr.smp.ptr.foundation.skill.targeter;

import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillTargeter;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * {@code @trigger} — the entity that caused this skill to fire (the
 * attacker for {@code onDamaged}, the killed player for {@code
 * onPlayerKill}, etc.). Empty if the trigger is poll-driven.
 */
public final class TriggerTargeter implements PtrSkillTargeter {

    public static final TriggerTargeter INSTANCE = new TriggerTargeter();

    private TriggerTargeter() {}

    @Override
    public @NotNull Collection<Entity> entities(@NotNull PtrSkillContext ctx) {
        return ctx.triggerEntity().map(List::<Entity>of).orElseGet(List::of);
    }

    @Override
    public @NotNull String label() {
        return "@trigger";
    }
}
