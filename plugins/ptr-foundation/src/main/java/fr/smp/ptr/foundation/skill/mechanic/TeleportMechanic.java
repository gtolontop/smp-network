package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.RegionLocator;
import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import java.util.Iterator;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code teleport @<targeter>} — teleport the caster to the first available
 * location or entity target.
 *
 * <p>If the destination is in a different region, Bukkit's async-teleport
 * promise is used so the move is region-safe.
 */
public final class TeleportMechanic implements PtrSkillMechanic {

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        Location destination = resolveDestination(ctx);
        if (destination == null) {
            return;
        }
        if (RegionLocator.isOwnedByCurrentRegion(destination)) {
            ctx.caster().teleport(destination);
        } else {
            ctx.caster().teleportAsync(destination);
        }
    }

    private static Location resolveDestination(PtrSkillContext ctx) {
        Iterator<Entity> it = ctx.entityTargets().iterator();
        if (it.hasNext()) {
            return it.next().getLocation();
        }
        Iterator<Location> li = ctx.locationTargets().iterator();
        if (li.hasNext()) {
            return li.next();
        }
        return null;
    }

    @Override
    public @NotNull String label() {
        return "teleport";
    }
}
