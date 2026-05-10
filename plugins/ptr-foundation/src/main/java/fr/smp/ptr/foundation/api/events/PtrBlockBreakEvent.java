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

/** Fired before a foundation block is broken. Cancellable. */
public final class PtrBlockBreakEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final PtrBlockDef block;
    private final @Nullable Player breaker;
    private boolean cancelled = false;
    private boolean dropLoot = true;

    public PtrBlockBreakEvent(
            @NotNull Location location,
            @NotNull PtrBlockDef block,
            @Nullable Player breaker) {
        this.location = Objects.requireNonNull(location, "location").clone();
        this.block = Objects.requireNonNull(block, "block");
        this.breaker = breaker;
    }

    public @NotNull Location location() {
        return location.clone();
    }

    public @NotNull PtrBlockDef block() {
        return block;
    }

    public @Nullable Player breaker() {
        return breaker;
    }

    /** Whether the foundation should drop the block's loot. */
    public boolean dropLoot() {
        return dropLoot;
    }

    public void setDropLoot(boolean drop) {
        this.dropLoot = drop;
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
