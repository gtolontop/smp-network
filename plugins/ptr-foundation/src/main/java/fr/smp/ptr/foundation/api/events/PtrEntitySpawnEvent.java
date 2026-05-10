package fr.smp.ptr.foundation.api.events;

import fr.smp.ptr.foundation.registry.PtrMobDef;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired before a foundation mob is fully spawned. Cancellable (entity is removed if cancelled). */
public final class PtrEntitySpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final PtrMobDef mob;
    private boolean cancelled = false;

    public PtrEntitySpawnEvent(@NotNull LivingEntity entity, @NotNull PtrMobDef mob) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.mob = Objects.requireNonNull(mob, "mob");
    }

    public @NotNull LivingEntity entity() {
        return entity;
    }

    public @NotNull PtrMobDef mob() {
        return mob;
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
