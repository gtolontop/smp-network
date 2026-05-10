package fr.smp.ptr.foundation.registry;

import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Helpers around the fixed {@code ptr:} namespace.
 *
 * <p>Every {@link PtrIdentified} value's {@link NamespacedKey} must live
 * under this namespace. Build keys via {@link #key(String)} or {@link
 * #parse(String)}; never call {@code new NamespacedKey("ptr", ...)} directly.
 */
public final class PtrIds {

    /** Fixed namespace for every foundation registry key. */
    public static final String NAMESPACE = "ptr";

    private PtrIds() {}

    /** Build {@code ptr:<path>}. */
    public static @NotNull NamespacedKey key(@NotNull String path) {
        Objects.requireNonNull(path, "path");
        NamespacedKey parsed = NamespacedKey.fromString(NAMESPACE + ":" + path);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid PTR key path: '" + path + "'");
        }
        return parsed;
    }

    /**
     * Parse {@code ptr:<path>}; reject anything outside the {@code ptr:}
     * namespace so we don't accidentally register {@code minecraft:} or
     * arbitrary plugin keys into a foundation registry.
     */
    public static @NotNull NamespacedKey parse(@NotNull String raw) {
        Objects.requireNonNull(raw, "raw");
        NamespacedKey parsed = NamespacedKey.fromString(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid namespaced key: '" + raw + "'");
        }
        if (!NAMESPACE.equals(parsed.getNamespace())) {
            throw new IllegalArgumentException(
                    "Foundation keys must live in the '" + NAMESPACE
                            + "' namespace, got: " + parsed);
        }
        return parsed;
    }
}
