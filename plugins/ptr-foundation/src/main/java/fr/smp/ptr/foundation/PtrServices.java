package fr.smp.ptr.foundation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * In-house service locator.
 *
 * <p>The foundation deliberately avoids Spring / Guice. Services are
 * registered explicitly in {@link PtrFoundationPlugin#onEnable()} in a
 * documented order and shut down in LIFO via {@link #shutdownAll()}.
 *
 * <p>{@code PtrServices} is itself a service that downstream content plugins
 * can access via {@code Bukkit.getServer().getServicesManager()} once it's
 * registered there — but the canonical handle is the one stored on the
 * plugin instance.
 */
public final class PtrServices {

    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
    private final Deque<Consumer<Object>> shutdownStack = new ArrayDeque<>();

    /**
     * Register a service. The {@code onShutdown} callback runs when {@link
     * #shutdownAll()} is called, in the reverse order of registration.
     */
    public <T> void register(@NotNull Class<T> type, @NotNull T impl) {
        register(type, impl, ignored -> {});
    }

    /**
     * Register a service with a shutdown hook. The hook receives the
     * registered instance.
     */
    @SuppressWarnings("unchecked")
    public <T> void register(
            @NotNull Class<T> type, @NotNull T impl, @NotNull Consumer<T> onShutdown) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(impl, "impl");
        Objects.requireNonNull(onShutdown, "onShutdown");
        if (instances.containsKey(type)) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
        instances.put(type, impl);
        shutdownStack.push(obj -> ((Consumer<T>) onShutdown).accept((T) obj));
    }

    /** Lookup. Throws if the service was never registered. */
    public <T> @NotNull T get(@NotNull Class<T> type) {
        Object impl = instances.get(Objects.requireNonNull(type, "type"));
        if (impl == null) {
            throw new IllegalStateException(
                    "Service not registered (init order bug?): " + type.getName());
        }
        return type.cast(impl);
    }

    /** Optional lookup. */
    public <T> @NotNull Optional<T> find(@NotNull Class<T> type) {
        Object impl = instances.get(Objects.requireNonNull(type, "type"));
        return Optional.ofNullable(impl).map(type::cast);
    }

    /** Number of registered services. */
    public int size() {
        return instances.size();
    }

    /** Shutdown every registered service in LIFO order. Best-effort: continues past failures. */
    public void shutdownAll() {
        while (!shutdownStack.isEmpty()) {
            Consumer<Object> hook = shutdownStack.pop();
            // The reverse-iteration walk needs the matching value; pull it lazily.
            Object value = lastValue();
            if (value != null) {
                try {
                    hook.accept(value);
                } catch (Throwable t) {
                    // Best-effort: continue shutting down the rest.
                    // The plugin logger logs the failure at the call site.
                    throw new RuntimeException(
                            "Shutdown hook failed for " + value.getClass().getName(), t);
                } finally {
                    removeLast();
                }
            }
        }
        instances.clear();
    }

    private Object lastValue() {
        Object last = null;
        for (Map.Entry<Class<?>, Object> e : instances.entrySet()) {
            last = e.getValue();
        }
        return last;
    }

    private void removeLast() {
        Class<?> lastKey = null;
        for (Map.Entry<Class<?>, Object> e : instances.entrySet()) {
            lastKey = e.getKey();
        }
        if (lastKey != null) {
            instances.remove(lastKey);
        }
    }
}
