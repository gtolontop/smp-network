package fr.smp.ptr.foundation.model;

import fr.smp.ptr.foundation.registry.PtrIdentified;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Parsed and immutable representation of a Blockbench {@code .bbmodel} file.
 *
 * <p>Carries the bone skeleton and the named animation tracks. The render
 * runtime ({@code ActiveModel}) holds the live ItemDisplay rig built from
 * a blueprint at spawn time.
 */
public record Blueprint(
        @NotNull NamespacedKey id,
        @NotNull Component displayName,
        @NotNull Map<String, BlueprintBone> bones,
        @NotNull List<String> rootBones,
        @NotNull Map<String, BlueprintAnimation> animations)
        implements PtrIdentified {

    public Blueprint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(bones, "bones");
        Objects.requireNonNull(rootBones, "rootBones");
        Objects.requireNonNull(animations, "animations");

        Map<String, BlueprintBone> bonesCopy = new LinkedHashMap<>(bones);
        Map<String, BlueprintAnimation> animsCopy = new LinkedHashMap<>(animations);
        bones = Map.copyOf(bonesCopy);
        rootBones = List.copyOf(rootBones);
        animations = Map.copyOf(animsCopy);

        for (String root : rootBones) {
            if (!bones.containsKey(root)) {
                throw new IllegalArgumentException(
                        "Root bone '" + root + "' not declared in bones map");
            }
        }
    }

    public @NotNull Optional<BlueprintBone> bone(@NotNull String name) {
        return Optional.ofNullable(bones.get(name));
    }

    public @NotNull Optional<BlueprintAnimation> animation(@NotNull String name) {
        return Optional.ofNullable(animations.get(name));
    }
}
