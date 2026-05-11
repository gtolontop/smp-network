package fr.smp.ptr.foundation.model;

/**
 * Immutable 3D vector. The whole model layer works in Blockbench's
 * right-handed coordinate space (XYZ = right/up/forward in pixels), and
 * converts to Bukkit's left-handed Minecraft space at the boundary.
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 sub(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    /** Linear interpolation between {@code this} and {@code other} at {@code t} in [0,1]. */
    public Vec3 lerp(Vec3 other, double t) {
        return new Vec3(
                x + (other.x - x) * t,
                y + (other.y - y) * t,
                z + (other.z - z) * t);
    }
}
