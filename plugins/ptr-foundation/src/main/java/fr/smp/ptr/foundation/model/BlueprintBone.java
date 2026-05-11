package fr.smp.ptr.foundation.model;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One bone in a {@link Blueprint}'s skeleton.
 *
 * @param name bone name (matches the Blockbench outliner)
 * @param origin pivot point of the bone (in pixel units, Blockbench convention)
 * @param baseRotation rotation at rest (degrees, around the pivot)
 * @param parent parent bone name, or {@code null} for root bones
 * @param children direct children bone names (for traversal)
 */
public record BlueprintBone(
        @NotNull String name,
        @NotNull Vec3 origin,
        @NotNull Vec3 baseRotation,
        @Nullable String parent,
        @NotNull List<String> children) {

    public BlueprintBone {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(baseRotation, "baseRotation");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
