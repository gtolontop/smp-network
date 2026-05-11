package fr.smp.ptr.foundation.model;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Keyframe interpolation modes. Linear is the only one implemented in V3 foundation. */
public enum InterpolationMode {
    /** No interpolation — value snaps to the next keyframe. */
    STEP,
    /** Linear interpolation between two keyframes. */
    LINEAR,
    /** Bezier (smooth) interpolation — falls back to LINEAR in the foundation. */
    BEZIER,
    /** Catmull-Rom smoothing — falls back to LINEAR in the foundation. */
    CATMULL_ROM;

    /** Parse from a Blockbench JSON string ({@code "linear"}, {@code "bezier"}, ...). */
    public static @NotNull InterpolationMode parse(@NotNull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "step" -> STEP;
            case "linear" -> LINEAR;
            case "bezier" -> BEZIER;
            case "catmullrom", "catmull_rom" -> CATMULL_ROM;
            default -> LINEAR;
        };
    }
}
