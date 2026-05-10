package fr.smp.ptr.foundation.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.smp.ptr.foundation.registry.PtrIds;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class BossPhaseValidationTest {

    @Test
    void telegraphIdListIsCopiedAndImmutable() {
        java.util.ArrayList<NamespacedKey> mutable = new java.util.ArrayList<>();
        mutable.add(PtrIds.key("telegraph/ground_slam_ring"));

        BossPhase phase =
                new BossPhase(
                        100.0,
                        Component.text("phase 1"),
                        mutable,
                        null,
                        null,
                        null);

        // Mutating the source list shouldn't affect the phase.
        mutable.clear();
        assertThat(phase.telegraphIds()).hasSize(1);

        // The list inside the phase should refuse mutation.
        assertThatThrownBy(() -> phase.telegraphIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hpThresholdCanBeAnyNumber() {
        // The record itself does not constrain HP — PhaseController orders
        // phases at construction; foundation lets content set whatever
        // thresholds it needs.
        BossPhase phase =
                new BossPhase(0.0, Component.text("dying"), List.of(), null, null, null);
        assertThat(phase.hpThreshold()).isEqualTo(0.0);
    }
}
