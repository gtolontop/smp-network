package fr.smp.ptr.foundation.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-safe registry of {@link PtrIdentified} values keyed by their {@link
 * NamespacedKey}.
 *
 * <p>Backed by a {@link LinkedHashMap} so iteration order is registration
 * order — useful for commands that enumerate entries. Concurrent access is
 * serialised by an internal monitor; reads return immutable snapshots.
 *
 * @param <T> registered value type
 */
public class PtrRegistry<T extends PtrIdentified> {

    private final String type;
    private final Map<NamespacedKey, T> entries = new LinkedHashMap<>();
    private final Object lock = new Object();

    /**
     * @param type short label used in error messages and command output ({@code "block"}, {@code
     *     "item"}, ...).
     */
    public PtrRegistry(@NotNull String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /** Short label identifying this registry. */
    public @NotNull String type() {
        return type;
    }

    /**
     * Register a definition. Throws if its id was already registered — late
     * registrations are programmer errors, not silent overwrites.
     */
    public void register(@NotNull T def) {
        Objects.requireNonNull(def, "def");
        NamespacedKey id = def.id();
        synchronized (lock) {
            if (entries.containsKey(id)) {
                throw new IllegalStateException(
                        "Duplicate " + type + " registration for " + id);
            }
            entries.put(id, def);
        }
    }

    /** Lookup by id. */
    public @NotNull Optional<T> get(@NotNull NamespacedKey id) {
        Objects.requireNonNull(id, "id");
        synchronized (lock) {
            return Optional.ofNullable(entries.get(id));
        }
    }

    /** Snapshot of every entry, registration-ordered, unmodifiable. */
    public @NotNull Map<NamespacedKey, T> view() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }

    /** Number of registered entries. */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /** True if no entries have been registered. */
    public boolean isEmpty() {
        synchronized (lock) {
            return entries.isEmpty();
        }
    }
}
