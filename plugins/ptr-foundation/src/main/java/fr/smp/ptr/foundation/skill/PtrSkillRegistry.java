package fr.smp.ptr.foundation.skill;

import fr.smp.ptr.foundation.registry.PtrRegistry;

/**
 * Registry of named {@link PtrSkill}s. Skills are addressable by id so a
 * {@link fr.smp.ptr.foundation.boss.BossPhase} can reference them by name
 * rather than holding the instance, useful when the same skill is shared
 * across multiple bosses.
 */
public final class PtrSkillRegistry extends PtrRegistry<PtrSkill> {

    public PtrSkillRegistry() {
        super("skill");
    }
}
