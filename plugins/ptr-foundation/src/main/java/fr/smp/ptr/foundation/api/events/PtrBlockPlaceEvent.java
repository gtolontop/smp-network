package fr.smp.ptr.foundation.api.events;

import fr.smp.ptr.foundation.registry.PtrBlockDef;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before a foundation block is placed in the world.
 *
 * <p>Cancellable. Content layers can listen to gate placement on claim
 * boundaries, anti-grief plugins, region permissions, etc.
 */
public final class PtrBlockPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final PtrBlockDef block;
    private final @Nullable Player placer;
    private boolean cancelled = false;

    public PtrBlockPlaceEvent(
            @NotNull Location location,
            @NotNull PtrBlockDef block,
            @Nullable Player placer) {
        this.location = Objects.requireNonNull(location, "location").clone();
        this.block = Objects.requireNonNull(block, "block");
        this.placer = placer;
    }

    public @NotNull Location location() {
        return location.clone();
    }

    public @NotNull PtrBlockDef block() {
        return block;
    }

    public @Nullable Player placer() {
        return placer;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
