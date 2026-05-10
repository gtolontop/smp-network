package fr.smp.ptr.foundation.api.events;

import fr.smp.ptr.foundation.registry.PtrMobDef;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a foundation mob dies. Not cancellable — death has already
 * happened. Listeners can read the killer and drop context to award loot,
 * post leaderboards, etc.
 */
public final class PtrEntityDeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final PtrMobDef mob;
    private final @Nullable Player killer;

    public PtrEntityDeathEvent(
            @NotNull LivingEntity entity, @NotNull PtrMobDef mob, @Nullable Player killer) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.mob = Objects.requireNonNull(mob, "mob");
        this.killer = killer;
    }

    public @NotNull LivingEntity entity() {
        return entity;
    }

    public @NotNull PtrMobDef mob() {
        return mob;
    }

    public @Nullable Player killer() {
        return killer;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
