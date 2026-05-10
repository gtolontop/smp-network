package fr.smp.ptr.foundation.skill.targeter;

import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillTargeter;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/** {@code @origin} — the caster's location at the start of the cast. */
public final class OriginTargeter implements PtrSkillTargeter {

    public static final OriginTargeter INSTANCE = new OriginTargeter();

    private OriginTargeter() {}

    @Override
    public @NotNull Collection<Location> locations(@NotNull PtrSkillContext ctx) {
        return List.of(ctx.origin());
    }

    @Override
    public @NotNull String label() {
        return "@origin";
    }
}
