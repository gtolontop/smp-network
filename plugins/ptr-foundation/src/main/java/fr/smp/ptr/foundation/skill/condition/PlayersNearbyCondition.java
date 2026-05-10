package fr.smp.ptr.foundation.skill.condition;

import fr.smp.ptr.foundation.skill.PtrSkillCondition;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code playersnearby{r=X;min=N}} — at least {@code min} players are
 * within {@code radius} blocks of the caster.
 */
public final class PlayersNearbyCondition implements PtrSkillCondition {

    private final double radius;
    private final int minPlayers;

    public PlayersNearbyCondition(double radius, int minPlayers) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("radius must be > 0, got " + radius);
        }
        if (minPlayers < 1) {
            throw new IllegalArgumentException("minPlayers must be >= 1, got " + minPlayers);
        }
        this.radius = radius;
        this.minPlayers = minPlayers;
    }

    @Override
    public boolean test(@NotNull PtrSkillContext ctx) {
        Location origin = ctx.origin();
        double rSq = radius * radius;
        int count = 0;
        for (Entity nearby :
                origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (nearby instanceof Player p
                    && p.isOnline()
                    && !p.isDead()
                    && p.getLocation().distanceSquared(origin) <= rSq) {
                count++;
                if (count >= minPlayers) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public @NotNull String label() {
        return "playersnearby{r=" + radius + ";min=" + minPlayers + "}";
    }
}
