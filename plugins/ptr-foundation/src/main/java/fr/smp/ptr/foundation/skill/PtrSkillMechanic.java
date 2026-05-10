package fr.smp.ptr.foundation.skill;

import fr.smp.ptr.foundation.platform.SchedulerService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * One atomic action in a {@link PtrSkill}: damage, particles, teleport,
 * potion, sound, …
 *
 * <p>The skill engine guarantees the {@link PtrSkillContext#caster()}'s
 * entity scheduler thread when calling {@link #execute}. Mechanics that
 * touch unrelated regions or run heavy I/O must reschedule via the supplied
 * {@link SchedulerService}.
 */
public interface PtrSkillMechanic {

    /**
     * Execute the mechanic.
     *
     * @param plugin owning plugin (for scheduler ownership)
     * @param scheduler region-aware scheduler
     * @param ctx the skill cast context
     */
    void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx);

    /** Short description for logs / debug. */
    @NotNull
    String label();
}
