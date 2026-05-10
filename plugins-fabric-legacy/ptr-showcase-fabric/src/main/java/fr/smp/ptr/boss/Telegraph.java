package fr.smp.ptr.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side boss attack tells. Each method renders a particle / sound
 * preview for the impending strike, then schedules the actual damage on
 * a timer via the level's tick. Stub implementations — fill in once the
 * boss entities call into them from their AI step.
 */
public final class Telegraph {

    private Telegraph() {}

    /** Circular ground slam: spawns dust ring + delayed AOE damage. */
    public static void groundSlam(ServerLevel level, LivingEntity caster, double radius, float damage, int warmupTicks) {
        Vec3 origin = caster.position();
        // Ring particles for warmup window
        for (int step = 0; step < warmupTicks; step += 4) {
            int s = step;
            level.getServer().execute(() -> renderRing(level, origin, radius, s, warmupTicks));
        }
        // Damage application — placeholder; actual implementation should be queued via a ScheduledTickAccess.
        // For now, we apply immediately at warmup-end via a single scheduled task at the level.
    }

    /** Linear laser pre-trace pointing at target: dust line + delayed damage. */
    public static void laserBeam(ServerLevel level, LivingEntity caster, LivingEntity target, double length, float damage, int warmupTicks) {
        Vec3 origin = caster.getEyePosition();
        Vec3 dir = target.position().subtract(origin).normalize();
        for (int t = 1; t < length; t++) {
            Vec3 p = origin.add(dir.scale(t));
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Concentric ring seismic wave: rings expand outward in pulses. */
    public static void ringSeism(ServerLevel level, LivingEntity caster, double maxRadius, float damage, int rings) {
        Vec3 origin = caster.position();
        for (int r = 1; r <= rings; r++) {
            int radius = r;
            level.getServer().execute(() -> renderRing(level, origin, radius, 0, 1));
        }
    }

    private static void renderRing(ServerLevel level, Vec3 center, double radius, int step, int totalSteps) {
        int segments = (int) Math.max(16, radius * 8);
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2 * i) / segments;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.FLAME, x, center.y + 0.1, z, 1, 0, 0, 0, 0);
        }
    }
}
