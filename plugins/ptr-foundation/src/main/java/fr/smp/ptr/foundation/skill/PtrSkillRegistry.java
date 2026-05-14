package fr.smp.ptr.foundation.skill;

import fr.smp.ptr.foundation.registry.PtrRegistry;

/** Registry of named {@link PtrSkill}s addressable by id from future content layers. */
public final class PtrSkillRegistry extends PtrRegistry<PtrSkill> {

    public PtrSkillRegistry() {
        super("skill");
    }
}
