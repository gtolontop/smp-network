package fr.smp.ptr.foundation.skill;

import org.jetbrains.annotations.NotNull;

/**
 * Pre-cast guard. Conditions are evaluated in order; first false aborts.
 *
 * <p>Implementations are pure — they observe state, they don't mutate it.
 */
public interface PtrSkillCondition {

    boolean test(@NotNull PtrSkillContext ctx);

    /** Short description for logs / debug. */
    @NotNull
    String label();
}
