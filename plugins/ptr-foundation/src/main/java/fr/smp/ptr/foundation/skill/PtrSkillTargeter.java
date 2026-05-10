package fr.smp.ptr.foundation.skill;

import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves a {@link PtrSkillContext} into a set of entity and/or location
 * targets that the following {@link PtrSkillMechanic}s operate on.
 *
 * <p>Implementations must respect Folia: only inspect entities and locations
 * the caster's region owns. Use {@code world.getNearbyLivingEntities(loc,
 * radius)} or the entity-bound APIs to stay region-safe.
 */
public interface PtrSkillTargeter {

    /** Entities this targeter returns. Empty if the targeter is location-only. */
    @NotNull
    default Collection<Entity> entities(@NotNull PtrSkillContext ctx) {
        return List.of();
    }

    /** Locations this targeter returns. Empty if the targeter is entity-only. */
    @NotNull
    default Collection<Location> locations(@NotNull PtrSkillContext ctx) {
        return List.of();
    }

    /** Short description for logs / debug. */
    @NotNull
    String label();
}
