package fr.smp.ptr.foundation.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The only sanctioned bridge to Folia's four schedulers.
 *
 * <p>Folia ticks distinct regions on distinct threads, so the legacy {@code
 * Bukkit.getScheduler()} contract no longer makes sense. Every piece of work
 * must declare which state it touches and route through the matching
 * scheduler here:
 *
 * <ul>
 *   <li>{@code runOnRegion} — touches blocks, entities, chunks, or any
 *       per-region state. The region owning the target location ticks it.
 *   <li>{@code runOnEntity} — touches a specific entity's state. The entity
 *       scheduler follows the entity if its region changes mid-task.
 *   <li>{@code runOnGlobal} — touches world-wide state (worldborder, weather,
 *       time of day). Single-threaded by definition.
 *   <li>{@code runAsync} — pure CPU / IO work that does not touch any Bukkit
 *       state. Never call Bukkit methods from here unless they're documented
 *       as thread-safe.
 * </ul>
 *
 * <p>Calling {@code Bukkit.getScheduler()} anywhere in the foundation is
 * banned by the {@code checkstyle.xml} config and rejected at build time.
 */
public interface SchedulerService {

    /** Run on the region that owns {@code location}, as soon as that region next ticks. */
    @NotNull ScheduledTask runOnRegion(@NotNull Location location, @NotNull Runnable task);

    /** Run on the region that owns the given chunk, as soon as that region next ticks. */
    @NotNull ScheduledTask runOnRegion(@NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task);

    /** Run after a delay on the region that owns {@code location}. */
    @NotNull ScheduledTask runOnRegionLater(
            @NotNull Location location, @NotNull Runnable task, long delayTicks);

    /** Run on a recurring tick interval on the region that owns {@code location}. */
    @NotNull ScheduledTask runOnRegionTimer(
            @NotNull Location location,
            @NotNull Runnable task,
            long delayTicks,
            long periodTicks);

    /**
     * Run on the entity's scheduler, which follows the entity across region
     * boundaries. The {@code retired} callback fires if the entity is removed
     * before the task gets to run; pass {@code null} if no cleanup is needed.
     */
    @NotNull ScheduledTask runOnEntity(
            @NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired);

    /** Run on the global region scheduler (worldborder, weather, time of day). */
    @NotNull ScheduledTask runOnGlobal(@NotNull Runnable task);

    /** Run after a delay on the global region scheduler. */
    @NotNull ScheduledTask runOnGlobalLater(@NotNull Runnable task, long delayTicks);

    /** Recurring task on the global region scheduler. */
    @NotNull ScheduledTask runOnGlobalTimer(
            @NotNull Runnable task, long delayTicks, long periodTicks);

    /** Run asynchronously, off all region threads. Never call Bukkit state mutation from here. */
    @NotNull ScheduledTask runAsync(@NotNull Runnable task);

    /** Run asynchronously after a wall-clock delay (milliseconds). */
    @NotNull ScheduledTask runAsyncLater(@NotNull Runnable task, long delayMillis);

    /** Recurring asynchronous task with wall-clock delay/period (milliseconds). */
    @NotNull ScheduledTask runAsyncTimer(@NotNull Runnable task, long delayMillis, long periodMillis);

    /**
     * Convenience: run {@code task} on the region that owns {@code location},
     * receiving the {@link ScheduledTask} handle (useful for self-cancelling
     * timers).
     */
    @NotNull ScheduledTask runOnRegion(
            @NotNull Location location, @NotNull Consumer<ScheduledTask> task);

    /** Cancel any pending tasks and release scheduler resources. */
    void shutdown();
}
