package fr.smp.ptr.foundation.model;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One playing animation's runtime state: which animation, when it started,
 * its lerp-in / lerp-out / speed parameters, and its current weight.
 *
 * <p>Mutable — the {@link AnimationController} writes back each tick.
 */
public final class AnimationProperty {

    private final BlueprintAnimation animation;
    private final double lerpInSeconds;
    private final double lerpOutSeconds;
    private final double speed;

    private double elapsedSeconds = 0.0;
    private double weight = 0.0;
    private boolean stopping = false;
    private boolean finished = false;

    public AnimationProperty(
            @NotNull BlueprintAnimation animation,
            double lerpInSeconds,
            double lerpOutSeconds,
            double speed) {
        this.animation = Objects.requireNonNull(animation, "animation");
        if (speed <= 0.0) {
            throw new IllegalArgumentException("speed must be > 0, got " + speed);
        }
        this.lerpInSeconds = Math.max(0.0, lerpInSeconds);
        this.lerpOutSeconds = Math.max(0.0, lerpOutSeconds);
        this.speed = speed;
    }

    public @NotNull BlueprintAnimation animation() {
        return animation;
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public double weight() {
        return weight;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Mark the animation for fade-out; weight will lerp down over {@link #lerpOutSeconds}. */
    public void requestStop() {
        this.stopping = true;
    }

    public boolean isStopping() {
        return stopping;
    }

    /** Sample the animation at the current elapsed time, respecting loop mode. */
    public double sampleTime() {
        double scaled = elapsedSeconds * speed;
        return switch (animation.loopMode()) {
            case ONCE -> Math.min(scaled, animation.lengthSeconds());
            case HOLD -> Math.min(scaled, animation.lengthSeconds());
            case LOOP -> scaled % animation.lengthSeconds();
        };
    }

    /** Advance the animation by {@code dtSeconds} and update weight. */
    public void tick(double dtSeconds) {
        elapsedSeconds += dtSeconds;
        if (stopping) {
            if (lerpOutSeconds <= 0.0) {
                weight = 0.0;
                finished = true;
            } else {
                weight = Math.max(0.0, weight - dtSeconds / lerpOutSeconds);
                if (weight <= 0.0) {
                    finished = true;
                }
            }
        } else if (lerpInSeconds <= 0.0) {
            weight = 1.0;
        } else {
            weight = Math.min(1.0, weight + dtSeconds / lerpInSeconds);
        }

        // Auto-finish a ONCE animation when it reaches its length and isn't
        // already fading out — caller decides whether to chain into the next.
        if (animation.loopMode() == LoopMode.ONCE
                && elapsedSeconds * speed >= animation.lengthSeconds()
                && !stopping) {
            requestStop();
        }
    }
}
