package fr.smp.ptr.foundation.storage;

import fr.smp.ptr.foundation.platform.SchedulerService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Async-by-default repository skeleton.
 *
 * <p>Every method that touches the database hops through {@link
 * SchedulerService#runAsync(Runnable)} so the calling region thread is never
 * blocked on I/O. The {@link CompletableFuture} returned can be chained
 * back onto a region via {@code .whenComplete(...)} plus an explicit
 * scheduler call.
 *
 * @param <K> primary-key type
 * @param <V> stored value type
 */
public abstract class PtrRepository<K, V> {

    protected final PtrDatabase database;
    protected final SchedulerService scheduler;

    protected PtrRepository(@NotNull PtrDatabase database, @NotNull SchedulerService scheduler) {
        this.database = Objects.requireNonNull(database, "database");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Subclasses implement the actual SQL load. */
    protected abstract @NotNull Optional<V> doFind(@NotNull Connection conn, @NotNull K key)
            throws SQLException;

    /** Subclasses implement the actual SQL save. */
    protected abstract void doSave(@NotNull Connection conn, @NotNull V value) throws SQLException;

    /** Subclasses implement the actual SQL delete. */
    protected abstract boolean doDelete(@NotNull Connection conn, @NotNull K key)
            throws SQLException;

    /** Async find. Throws via the future if the SQL fails. */
    public @NotNull CompletableFuture<Optional<V>> find(@NotNull K key) {
        Objects.requireNonNull(key, "key");
        CompletableFuture<Optional<V>> result = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection c = database.connection()) {
                        result.complete(doFind(c, key));
                    } catch (Throwable t) {
                        result.completeExceptionally(t);
                    }
                });
        return result;
    }

    /** Async save (insert-or-replace, depending on subclass SQL). */
    public @NotNull CompletableFuture<Void> save(@NotNull V value) {
        Objects.requireNonNull(value, "value");
        CompletableFuture<Void> result = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection c = database.connection()) {
                        doSave(c, value);
                        result.complete(null);
                    } catch (Throwable t) {
                        result.completeExceptionally(t);
                    }
                });
        return result;
    }

    /** Async delete. Future resolves to {@code true} if a row was removed. */
    public @NotNull CompletableFuture<Boolean> delete(@NotNull K key) {
        Objects.requireNonNull(key, "key");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        scheduler.runAsync(
                () -> {
                    try (Connection c = database.connection()) {
                        result.complete(doDelete(c, key));
                    } catch (Throwable t) {
                        result.completeExceptionally(t);
                    }
                });
        return result;
    }
}
