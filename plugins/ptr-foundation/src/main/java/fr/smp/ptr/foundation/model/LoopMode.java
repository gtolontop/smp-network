package fr.smp.ptr.foundation.model;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Animation loop mode. */
public enum LoopMode {
    /** Play once, stop on the last frame. */
    ONCE,
    /** Hold the last frame until explicitly stopped. */
    HOLD,
    /** Loop indefinitely. */
    LOOP;

    public static @NotNull LoopMode parse(@NotNull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "once" -> ONCE;
            case "hold", "hold_on_last_frame" -> HOLD;
            case "loop" -> LOOP;
            default -> ONCE;
        };
    }
}
