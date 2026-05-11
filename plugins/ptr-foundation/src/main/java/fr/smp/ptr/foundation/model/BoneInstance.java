package fr.smp.ptr.foundation.model;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One live bone in an {@link ActiveModel}: an {@link ItemDisplay} that
 * carries the bone's visual and gets its transform recomputed each tick
 * via the parent chain + the current animation sample.
 *
 * <p>The render item is supplied by the content layer (typically a vanilla
 * carrier item with a custom model component pointing at the bone's mesh).
 * If null at spawn time, an empty ItemDisplay is created and the bone is
 * effectively invisible — useful for non-visible logical bones (hitboxes,
 * mount points).
 */
public final class BoneInstance {

    private static final float PIXEL_TO_BLOCK = 1.0f / 16.0f;

    private final BlueprintBone bone;
    private final ItemDisplay display;
    private @Nullable BoneInstance parent;
    private Vec3 animatedPosition = Vec3.ZERO;
    private Vec3 animatedRotation = Vec3.ZERO;
    private Vec3 animatedScale = new Vec3(1, 1, 1);

    public BoneInstance(@NotNull BlueprintBone bone, @NotNull ItemDisplay display) {
        this.bone = Objects.requireNonNull(bone, "bone");
        this.display = Objects.requireNonNull(display, "display");
    }

    public @NotNull BlueprintBone bone() {
        return bone;
    }

    public @NotNull ItemDisplay display() {
        return display;
    }

    public void setParent(@Nullable BoneInstance parent) {
        this.parent = parent;
    }

    public @Nullable BoneInstance parent() {
        return parent;
    }

    /** Update the bone's animated channels for this tick. */
    public void applyAnimation(@NotNull Vec3 pos, @NotNull Vec3 rot, @NotNull Vec3 scale) {
        this.animatedPosition = pos;
        this.animatedRotation = rot;
        this.animatedScale = scale;
    }

    /** Reset the bone's render item (e.g. on phase change). */
    public void setItem(@NotNull ItemStack item) {
        display.setItemStack(Objects.requireNonNull(item, "item"));
    }

    /**
     * Recompute the bone's world-space transform from the parent chain +
     * animated channels, and push it onto the ItemDisplay.
     *
     * @param rootLocation the model's anchor location (the host entity's
     *     foot location, typically)
     */
    public void refresh(@NotNull Location rootLocation) {
        Vec3 origin = bone.origin().scale(PIXEL_TO_BLOCK);
        Vec3 animPos = animatedPosition.scale(PIXEL_TO_BLOCK);

        Vector3f translation =
                new Vector3f(
                        (float) (origin.x() + animPos.x()),
                        (float) (origin.y() + animPos.y()),
                        (float) (origin.z() + animPos.z()));

        Vec3 totalRot = bone.baseRotation().add(animatedRotation);
        Quaternionf leftRotation =
                new Quaternionf()
                        .rotateY((float) Math.toRadians(totalRot.y()))
                        .rotateX((float) Math.toRadians(totalRot.x()))
                        .rotateZ((float) Math.toRadians(totalRot.z()));

        Vector3f scale =
                new Vector3f(
                        (float) animatedScale.x(),
                        (float) animatedScale.y(),
                        (float) animatedScale.z());

        Transformation t =
                new Transformation(translation, leftRotation, scale, new Quaternionf());
        display.setTransformation(t);
        // Position the display at the model root; child transforms are local
        // and handled via the parent-chain Transformation. In the foundation
        // we collapse the chain into one transform per bone for simplicity —
        // multi-level parent chaining via teleport happens at the ActiveModel
        // level (it teleports children when the root moves).
        if (parent == null) {
            display.teleport(rootLocation);
        }
    }

    /** Remove the ItemDisplay entity. */
    public void destroy() {
        if (display.isValid()) {
            display.remove();
        }
    }
}
