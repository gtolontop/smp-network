package fr.smp.ptr.foundation.model;

/**
 * High-level animation state.
 *
 * <p>The {@code AnimationController} maps an active {@link ModelState} to a
 * specific animation name on the blueprint. The state machine guarantees
 * smooth lerp transitions between states.
 */
public enum ModelState {
    SPAWN,
    IDLE,
    WALK,
    RUN,
    JUMP,
    HOVER,
    FLY,
    ATTACK,
    HURT,
    DEATH
}
