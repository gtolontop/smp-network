package fr.smp.ptr.foundation.model.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.smp.ptr.foundation.model.Blueprint;
import fr.smp.ptr.foundation.model.BlueprintAnimation;
import fr.smp.ptr.foundation.model.BlueprintBone;
import fr.smp.ptr.foundation.model.BoneChannel;
import fr.smp.ptr.foundation.model.InterpolationMode;
import fr.smp.ptr.foundation.model.Keyframe;
import fr.smp.ptr.foundation.model.LoopMode;
import fr.smp.ptr.foundation.model.Vec3;
import fr.smp.ptr.foundation.registry.PtrIds;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parses a Blockbench {@code .bbmodel} (format version 5.x) into a
 * {@link Blueprint}.
 *
 * <p>What the foundation parser handles:
 *
 * <ul>
 *   <li>The {@code outliner} tree → {@link BlueprintBone} skeleton with
 *       parent/children relationships.
 *   <li>The {@code animations} array → {@link BlueprintAnimation}s with
 *       per-bone {@code rotation} / {@code position} / {@code scale}
 *       keyframes.
 *   <li>Linear interpolation (the most common) and {@code step}.
 * </ul>
 *
 * <p>What is intentionally left for the content layer / model-engine
 * follow-up:
 *
 * <ul>
 *   <li>Cube geometry — the parser ignores {@code elements} array entries
 *       that aren't referenced as bone children, because the runtime renders
 *       each bone as an ItemDisplay attached to a content-supplied custom
 *       model, not by reconstructing the cube faces server-side.
 *   <li>Texture / UV data — same reason.
 *   <li>Bezier / Catmull-Rom interpolation — falls back to linear.
 * </ul>
 */
public final class BlockbenchParser {

    private BlockbenchParser() {}

    /** Parse a JSON {@link Reader} into a {@link Blueprint}. */
    public static @NotNull Blueprint parse(@NotNull Reader json, @NotNull NamespacedKey id)
            throws IOException {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(id, "id");
        JsonObject root;
        try {
            root = JsonParser.parseReader(json).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Malformed .bbmodel JSON: " + e.getMessage(), e);
        }
        return parse(root, id);
    }

    /** Parse from a pre-loaded {@link JsonObject}. */
    public static @NotNull Blueprint parse(@NotNull JsonObject root, @NotNull NamespacedKey id) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(id, "id");

        String name =
                root.has("name") ? root.get("name").getAsString() : id.getKey();
        Component displayName = Component.text(name);

        // Bones: walk the outliner recursively. Bones are objects with a "name";
        // children may be UUID strings (referring to cubes) or nested objects.
        Map<String, BlueprintBone> bones = new LinkedHashMap<>();
        List<String> roots = new ArrayList<>();
        if (root.has("outliner")) {
            for (JsonElement node : root.getAsJsonArray("outliner")) {
                if (node.isJsonObject()) {
                    String rootBone = walkBone(node.getAsJsonObject(), null, bones);
                    if (rootBone != null) {
                        roots.add(rootBone);
                    }
                }
            }
        }

        // Animations
        Map<String, BlueprintAnimation> animations = new LinkedHashMap<>();
        if (root.has("animations")) {
            for (JsonElement animEl : root.getAsJsonArray("animations")) {
                if (!animEl.isJsonObject()) {
                    continue;
                }
                BlueprintAnimation anim = parseAnimation(animEl.getAsJsonObject());
                if (anim != null) {
                    animations.put(anim.name(), anim);
                }
            }
        }

        return new Blueprint(id, displayName, bones, roots, animations);
    }

    /** Convenience: parse from a classpath resource (e.g. test fixture). */
    public static @NotNull Blueprint parseResource(
            @NotNull String path, @NotNull NamespacedKey id) throws IOException {
        try (Reader r =
                new java.io.InputStreamReader(
                        Objects.requireNonNull(
                                BlockbenchParser.class.getResourceAsStream(path),
                                "resource not found: " + path),
                        java.nio.charset.StandardCharsets.UTF_8)) {
            return parse(r, id);
        }
    }

    /** Quick helper for content layers building blueprints programmatically. */
    public static @NotNull NamespacedKey defaultId(@NotNull String path) {
        return PtrIds.key("model/" + path);
    }

    private static @Nullable String walkBone(
            JsonObject node, @Nullable String parent, Map<String, BlueprintBone> bones) {
        if (!node.has("name")) {
            return null;
        }
        String name = node.get("name").getAsString();
        Vec3 origin = readVec3(node, "origin");
        Vec3 rotation = readVec3(node, "rotation");
        List<String> children = new ArrayList<>();
        if (node.has("children")) {
            for (JsonElement childEl : node.getAsJsonArray("children")) {
                if (childEl.isJsonObject()) {
                    String childName = walkBone(childEl.getAsJsonObject(), name, bones);
                    if (childName != null) {
                        children.add(childName);
                    }
                }
                // UUID-string children are cube references — ignored in the foundation.
            }
        }
        bones.put(name, new BlueprintBone(name, origin, rotation, parent, children));
        return name;
    }

    private static @Nullable BlueprintAnimation parseAnimation(JsonObject anim) {
        String name = anim.has("name") ? anim.get("name").getAsString() : null;
        if (name == null) {
            return null;
        }
        double length = anim.has("length") ? anim.get("length").getAsDouble() : 1.0;
        if (length <= 0.0) {
            length = 1.0;
        }
        LoopMode loop =
                LoopMode.parse(anim.has("loop") ? anim.get("loop").getAsString() : "once");

        Map<String, Map<BoneChannel, List<Keyframe>>> lanes = new LinkedHashMap<>();
        if (anim.has("animators")) {
            for (Map.Entry<String, JsonElement> entry :
                    anim.getAsJsonObject("animators").entrySet()) {
                JsonObject animator = entry.getValue().getAsJsonObject();
                String boneName =
                        animator.has("name") ? animator.get("name").getAsString() : null;
                if (boneName == null) {
                    continue;
                }
                Map<BoneChannel, List<Keyframe>> perChannel = new EnumMap<>(BoneChannel.class);
                if (animator.has("keyframes")) {
                    for (JsonElement kfEl : animator.getAsJsonArray("keyframes")) {
                        if (!kfEl.isJsonObject()) {
                            continue;
                        }
                        JsonObject kf = kfEl.getAsJsonObject();
                        if (!kf.has("channel") || !kf.has("data_points")) {
                            continue;
                        }
                        BoneChannel channel = readChannel(kf.get("channel").getAsString());
                        if (channel == null) {
                            continue;
                        }
                        double time = kf.has("time") ? kf.get("time").getAsDouble() : 0.0;
                        InterpolationMode interp =
                                kf.has("interpolation")
                                        ? InterpolationMode.parse(
                                                kf.get("interpolation").getAsString())
                                        : InterpolationMode.LINEAR;
                        JsonArray dp = kf.getAsJsonArray("data_points");
                        if (dp.isEmpty()) {
                            continue;
                        }
                        JsonObject point = dp.get(0).getAsJsonObject();
                        Vec3 value = readVec3FromXYZ(point);
                        perChannel
                                .computeIfAbsent(channel, c -> new ArrayList<>())
                                .add(new Keyframe(time, value, interp));
                    }
                }
                // Sort each channel by time.
                for (Map.Entry<BoneChannel, List<Keyframe>> ce : perChannel.entrySet()) {
                    ce.getValue().sort(Comparator.comparingDouble(Keyframe::time));
                }
                if (!perChannel.isEmpty()) {
                    lanes.put(boneName, perChannel);
                }
            }
        }
        return new BlueprintAnimation(name, length, loop, lanes);
    }

    private static @Nullable BoneChannel readChannel(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "rotation" -> BoneChannel.ROTATION;
            case "position" -> BoneChannel.POSITION;
            case "scale" -> BoneChannel.SCALE;
            default -> null;
        };
    }

    private static Vec3 readVec3(JsonObject node, String key) {
        if (!node.has(key) || !node.get(key).isJsonArray()) {
            return Vec3.ZERO;
        }
        JsonArray arr = node.getAsJsonArray(key);
        if (arr.size() < 3) {
            return Vec3.ZERO;
        }
        return new Vec3(
                arr.get(0).getAsDouble(),
                arr.get(1).getAsDouble(),
                arr.get(2).getAsDouble());
    }

    private static Vec3 readVec3FromXYZ(JsonObject point) {
        double x = point.has("x") ? readNumber(point.get("x")) : 0.0;
        double y = point.has("y") ? readNumber(point.get("y")) : 0.0;
        double z = point.has("z") ? readNumber(point.get("z")) : 0.0;
        return new Vec3(x, y, z);
    }

    private static double readNumber(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return 0.0;
        }
        if (el.isJsonPrimitive()) {
            try {
                return el.getAsDouble();
            } catch (NumberFormatException ignored) {
                // Strings like "0+t" not supported in foundation; default to 0.
                return 0.0;
            }
        }
        return 0.0;
    }
}
