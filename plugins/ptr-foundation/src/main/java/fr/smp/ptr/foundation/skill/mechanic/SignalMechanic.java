package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code signal{name=X}} — write the signal name into the context's
 * variable bag under the {@code "signal"} key. Other skills (or the
 * boss controller, in a follow-up iteration) can poll for it.
 *
 * <p>The foundation stops there — there is no built-in skill-to-skill
 * subscription bus yet; content layers wire signals to their own listeners.
 */
public final class SignalMechanic implements PtrSkillMechanic {

    public static final String CONTEXT_KEY = "signal";

    private final String signalName;

    public SignalMechanic(@NotNull String signalName) {
        this.signalName = Objects.requireNonNull(signalName, "signalName");
        if (signalName.isBlank()) {
            throw new IllegalArgumentException("signalName must not be blank");
        }
    }

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        ctx.setVar(CONTEXT_KEY, signalName);
    }

    @Override
    public @NotNull String label() {
        return "signal{name=" + signalName + "}";
    }
}
