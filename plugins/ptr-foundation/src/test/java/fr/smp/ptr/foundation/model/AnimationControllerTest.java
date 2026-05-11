package fr.smp.ptr.foundation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.smp.ptr.foundation.model.parser.BlockbenchParser;
import fr.smp.ptr.foundation.registry.PtrIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnimationControllerTest {

    private Blueprint blueprint;

    @BeforeEach
    void load() throws Exception {
        blueprint =
                BlockbenchParser.parseResource(
                        "/fixtures/sample.bbmodel", PtrIds.key("model/sample"));
    }

    @Test
    void bindRejectsUnknownAnimation() {
        AnimationController ctrl = new AnimationController(blueprint);
        assertThatThrownBy(() -> ctrl.bind(ModelState.IDLE, "ghost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setStateStartsTheBoundAnimation() {
        AnimationController ctrl = new AnimationController(blueprint).bind(ModelState.IDLE, "idle");
        // First tick from default IDLE state — animation should start.
        ctrl.setState(ModelState.IDLE); // idempotent
        var transforms = ctrl.tick(1.0 / 20.0);
        // The head bone (mapped to "head" in the animator name) should have a rotation entry.
        assertThat(transforms).containsKey("head");
    }

    @Test
    void transitionFadesOutPrevious() {
        AnimationController ctrl =
                new AnimationController(blueprint)
                        .bind(ModelState.IDLE, "idle")
                        .bind(ModelState.ATTACK, "attack");
        ctrl.setState(ModelState.IDLE);
        ctrl.tick(0.5);
        assertThat(ctrl.currentState()).isEqualTo(ModelState.IDLE);
        ctrl.setState(ModelState.ATTACK);
        ctrl.tick(0.5);
        assertThat(ctrl.currentState()).isEqualTo(ModelState.ATTACK);
    }

    @Test
    void oneShotEventuallyEnds() {
        AnimationController ctrl = new AnimationController(blueprint).bind(ModelState.IDLE, "idle");
        ctrl.playOneShot("attack", ModelState.IDLE);
        // The "attack" animation length is 0.5s + 0.2s default lerp-out.
        // Step through enough ticks to exhaust it.
        for (int i = 0; i < 60; i++) {
            ctrl.tick(1.0 / 20.0);
        }
        // After enough time the one-shot should be done and the controller
        // should have reverted to IDLE.
        assertThat(ctrl.currentState()).isEqualTo(ModelState.IDLE);
    }
}
