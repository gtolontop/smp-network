package fr.smp.ptr.foundation.api.events;

import fr.smp.ptr.foundation.registry.PtrBlockDef;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.jetbrains.annotations.NotNull;

/** Fired when a player right- or left-clicks a foundation block. Cancellable. */
public final class PtrBlockInteractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final PtrBlockDef block;
    private final Player player;
    private final Action action;
    private boolean cancelled = false;

    public PtrBlockInteractEvent(
            @NotNull Location location,
            @NotNull PtrBlockDef block,
            @NotNull Player player,
            @NotNull Action action) {
        this.location = Objects.requireNonNull(location, "location").clone();
        this.block = Objects.requireNonNull(block, "block");
        this.player = Objects.requireNonNull(player, "player");
        this.action = Objects.requireNonNull(action, "action");
    }

    public @NotNull Location location() {
        return location.clone();
    }

    public @NotNull PtrBlockDef block() {
        return block;
    }

    public @NotNull Player player() {
        return player;
    }

    public @NotNull Action action() {
        return action;
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
