package fr.smp.ptr.foundation.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PtrSkillTriggerTest {

    @Test
    void onTimerRejectsZeroOrNegativePeriod() {
        assertThatThrownBy(() -> new PtrSkillTrigger.OnTimer(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PtrSkillTrigger.OnTimer(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onHpBelowEnforces0to1Range() {
        assertThatThrownBy(() -> new PtrSkillTrigger.OnHpBelow(-0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PtrSkillTrigger.OnHpBelow(1.01))
                .isInstanceOf(IllegalArgumentException.class);
        // boundaries OK
        assertThat(new PtrSkillTrigger.OnHpBelow(0.0).percent()).isEqualTo(0.0);
        assertThat(new PtrSkillTrigger.OnHpBelow(1.0).percent()).isEqualTo(1.0);
    }

    @Test
    void labelsAreReadable() {
        assertThat(new PtrSkillTrigger.OnSpawn().label()).isEqualTo("onSpawn");
        assertThat(new PtrSkillTrigger.OnDeath().label()).isEqualTo("onDeath");
        assertThat(new PtrSkillTrigger.OnTimer(40).label()).isEqualTo("onTimer:40t");
        assertThat(new PtrSkillTrigger.OnHpBelow(0.5).label()).isEqualTo("onHpBelow:0.5");
    }
}
