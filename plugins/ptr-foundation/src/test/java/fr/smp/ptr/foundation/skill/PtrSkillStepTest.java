package fr.smp.ptr.foundation.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.smp.ptr.foundation.platform.SchedulerService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class PtrSkillStepTest {

    @Test
    void delayRejectsZeroOrNegative() {
        assertThatThrownBy(() -> new PtrSkillStep.Delay(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PtrSkillStep.Delay(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mechanicWraps() {
        PtrSkillMechanic m =
                new PtrSkillMechanic() {
                    @Override
                    public void execute(
                            @NotNull Plugin plugin,
                            @NotNull SchedulerService scheduler,
                            @NotNull PtrSkillContext ctx) {
                        // no-op for test
                    }

                    @Override
                    public @NotNull String label() {
                        return "noop";
                    }
                };

        PtrSkillStep step = new PtrSkillStep.Mechanic(m);
        assertThat(step).isInstanceOf(PtrSkillStep.Mechanic.class);
        assertThat(((PtrSkillStep.Mechanic) step).mechanic().label()).isEqualTo("noop");
    }
}
