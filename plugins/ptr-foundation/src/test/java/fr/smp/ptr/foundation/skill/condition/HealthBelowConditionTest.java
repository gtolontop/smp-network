package fr.smp.ptr.foundation.skill.condition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HealthBelowConditionTest {

    @Test
    void rejectsOutOfRangePercent() {
        assertThatThrownBy(() -> new HealthBelowCondition(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HealthBelowCondition(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelIsReadable() {
        org.assertj.core.api.Assertions.assertThat(
                        new HealthBelowCondition(0.25).label())
                .isEqualTo("healthbelow{p=0.25}");
    }
}
