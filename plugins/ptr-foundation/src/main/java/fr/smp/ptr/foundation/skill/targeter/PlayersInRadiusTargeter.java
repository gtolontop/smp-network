package fr.smp.ptr.foundation.skill.targeter;

import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillTargeter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code @PlayersInRadius{r=X}} — every {@link Player} within {@code radius}
 * blocks of the caster's origin.
 *
 * <p>Folia note: scans use the world's nearby-entity index, which is only
 * region-safe for chunks the caster's region owns. Players in unrelated
 * regions may legitimately not be picked up — that's the trade-off of
 * Folia's threading model and matches caster-region scoping.
 */
public final class PlayersInRadiusTargeter implements PtrSkillTargeter {

    private final double radius;

    public PlayersInRadiusTargeter(double radius) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("radius must be > 0, got " + radius);
        }
        this.radius = radius;
    }

    public double radius() {
        return radius;
    }

    @Override
    public @NotNull Collection<Entity> entities(@NotNull PtrSkillContext ctx) {
        Location origin = ctx.origin();
        List<Entity> out = new ArrayList<>();
        double rSq = radius * radius;
        for (Entity nearby :
                origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (nearby instanceof Player p && p.isOnline() && !p.isDead()) {
                if (p.getLocation().distanceSquared(origin) <= rSq) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    @Override
    public @NotNull String label() {
        return "@PlayersInRadius{r=" + radius + "}";
    }
}
