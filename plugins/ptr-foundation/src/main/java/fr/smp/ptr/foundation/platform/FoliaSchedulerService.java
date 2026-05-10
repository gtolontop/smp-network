package fr.smp.ptr.foundation.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Folia-native {@link SchedulerService} implementation.
 *
 * <p>Delegates to {@link Bukkit#getRegionScheduler()}, {@link
 * Bukkit#getGlobalRegionScheduler()}, {@link Bukkit#getAsyncScheduler()}, and
 * {@link Entity#getScheduler()}. Each instance is bound to a single {@link
 * Plugin} reference and uses it as the task owner so Folia can cancel
 * outstanding tasks on plugin disable.
 */
public final class FoliaSchedulerService implements SchedulerService {

    private final Plugin plugin;

    public FoliaSchedulerService(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public @NotNull ScheduledTask runOnRegion(@NotNull Location location, @NotNull Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        return Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
    }

    @Override
    public @NotNull ScheduledTask runOnRegion(
            @NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        return Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, t -> task.run());
    }

    @Override
    public @NotNull ScheduledTask runOnRegion(
            @NotNull Location location, @NotNull Consumer<ScheduledTask> task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        return Bukkit.getRegionScheduler().run(plugin, location, task::accept);
    }

    @Override
    public @NotNull ScheduledTask runOnRegionLater(
            @NotNull Location location, @NotNull Runnable task, long delayTicks) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        long ticks = Math.max(1L, delayTicks);
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), ticks);
    }

    @Override
    public @NotNull ScheduledTask runOnRegionTimer(
            @NotNull Location location,
            @NotNull Runnable task,
            long delayTicks,
            long periodTicks) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        return Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, location, t -> task.run(), delay, period);
    }

    @Override
    public @NotNull ScheduledTask runOnEntity(
            @NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        ScheduledTask scheduled =
                entity.getScheduler().run(plugin, t -> task.run(), retired);
        if (scheduled == null) {
            throw new IllegalStateException(
                    "EntityScheduler refused to run on " + entity.getUniqueId()
                            + " (entity already retired before scheduling)");
        }
        return scheduled;
    }

    @Override
    public @NotNull ScheduledTask runOnEntityLater(
            @NotNull Entity entity,
            @NotNull Runnable task,
            @Nullable Runnable retired,
            long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        long ticks = Math.max(1L, delayTicks);
        ScheduledTask scheduled =
                entity.getScheduler().runDelayed(plugin, t -> task.run(), retired, ticks);
        if (scheduled == null) {
            throw new IllegalStateException(
                    "EntityScheduler refused to runDelayed on " + entity.getUniqueId());
        }
        return scheduled;
    }

    @Override
    public @NotNull ScheduledTask runOnEntityTimer(
            @NotNull Entity entity,
            @NotNull Runnable task,
            @Nullable Runnable retired,
            long delayTicks,
            long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        ScheduledTask scheduled =
                entity.getScheduler()
                        .runAtFixedRate(plugin, t -> task.run(), retired, delay, period);
        if (scheduled == null) {
            throw new IllegalStateException(
                    "EntityScheduler refused to runAtFixedRate on " + entity.getUniqueId());
        }
        return scheduled;
    }

    @Override
    public @NotNull ScheduledTask runOnGlobal(@NotNull Runnable task) {
        Objects.requireNonNull(task, "task");
        return Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    @Override
    public @NotNull ScheduledTask runOnGlobalLater(@NotNull Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        long ticks = Math.max(1L, delayTicks);
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), ticks);
    }

    @Override
    public @NotNull ScheduledTask runOnGlobalTimer(
            @NotNull Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        return Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> task.run(), delay, period);
    }

    @Override
    public @NotNull ScheduledTask runAsync(@NotNull Runnable task) {
        Objects.requireNonNull(task, "task");
        return Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public @NotNull ScheduledTask runAsyncLater(@NotNull Runnable task, long delayMillis) {
        Objects.requireNonNull(task, "task");
        long millis = Math.max(1L, delayMillis);
        return Bukkit.getAsyncScheduler()
                .runDelayed(plugin, t -> task.run(), millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public @NotNull ScheduledTask runAsyncTimer(
            @NotNull Runnable task, long delayMillis, long periodMillis) {
        Objects.requireNonNull(task, "task");
        long initial = Math.max(1L, delayMillis);
        long period = Math.max(1L, periodMillis);
        return Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin, t -> task.run(), initial, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        // Region/entity tasks are also cancelled by Bukkit when the plugin disables;
        // we don't have a per-region API to enumerate them.
    }
}
