package fr.smp.ptr.foundation.skill.targeter;

import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillTargeter;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/** {@code @self} — the caster as the only entity target. */
public final class SelfTargeter implements PtrSkillTargeter {

    public static final SelfTargeter INSTANCE = new SelfTargeter();

    private SelfTargeter() {}

    @Override
    public @NotNull Collection<Entity> entities(@NotNull PtrSkillContext ctx) {
        return List.of(ctx.caster());
    }

    @Override
    public @NotNull String label() {
        return "@self";
    }
}
