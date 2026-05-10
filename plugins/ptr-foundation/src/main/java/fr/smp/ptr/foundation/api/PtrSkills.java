package fr.smp.ptr.foundation.api;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkill;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillRegistry;
import fr.smp.ptr.foundation.skill.PtrSkillTrigger;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/** Flat facade over {@link PtrSkillRegistry} + one-shot cast helper. */
public final class PtrSkills {

    private PtrSkills() {}

    public static @NotNull Map<NamespacedKey, PtrSkill> all() {
        return PtrFoundationApi.services().get(PtrSkillRegistry.class).view();
    }

    public static @NotNull Optional<PtrSkill> find(@NotNull NamespacedKey id) {
        return PtrFoundationApi.services().get(PtrSkillRegistry.class).get(id);
    }

    /** Register a named skill. */
    public static void register(@NotNull PtrSkill skill) {
        PtrFoundationApi.services().get(PtrSkillRegistry.class).register(skill);
    }

    /**
     * One-shot cast of a named skill by a caster. The {@code trigger} is
     * forwarded into the context for downstream conditions/targeters.
     */
    public static boolean cast(
            @NotNull Plugin plugin,
            @NotNull NamespacedKey skillId,
            @NotNull LivingEntity caster,
            @NotNull PtrSkillTrigger trigger) {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(trigger, "trigger");
        Optional<PtrSkill> skill = find(skillId);
        if (skill.isEmpty()) {
            return false;
        }
        SchedulerService sched = PtrFoundationApi.services().get(SchedulerService.class);
        PtrSkillContext ctx = new PtrSkillContext(caster, trigger);
        skill.get().cast(plugin, sched, ctx);
        return true;
    }
}
