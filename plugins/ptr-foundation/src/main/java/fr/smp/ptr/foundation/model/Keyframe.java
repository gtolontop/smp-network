package fr.smp.ptr.foundation.model;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One keyframe on a single bone's channel.
 *
 * @param time time in seconds along the animation timeline
 * @param value channel value at this keyframe (interpretation depends on the channel)
 * @param interpolation how this keyframe blends with the NEXT one
 */
public record Keyframe(double time, @NotNull Vec3 value, @NotNull InterpolationMode interpolation) {

    public Keyframe {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(interpolation, "interpolation");
        if (time < 0.0) {
            throw new IllegalArgumentException("time must be >= 0, got " + time);
        }
    }

    /** Convenience: linear keyframe at {@code (time, value)}. */
    public static @NotNull Keyframe linear(double time, @NotNull Vec3 value) {
        return new Keyframe(time, value, InterpolationMode.LINEAR);
    }
}
