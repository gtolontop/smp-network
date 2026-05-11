package fr.smp.ptr.foundation.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One animation track in a {@link Blueprint}.
 *
 * <p>{@code lanes} maps {@code (boneName, channel)} → sorted keyframe list.
 * Empty lanes mean the bone holds its base transform on that channel for
 * the duration of this animation.
 *
 * @param name animation name (e.g. {@code "idle"}, {@code "attack"})
 * @param lengthSeconds total duration before loop/hold
 * @param loopMode {@link LoopMode#LOOP}, {@link LoopMode#HOLD}, {@link LoopMode#ONCE}
 * @param lanes per-bone-per-channel keyframes
 */
public record BlueprintAnimation(
        @NotNull String name,
        double lengthSeconds,
        @NotNull LoopMode loopMode,
        @NotNull Map<String, Map<BoneChannel, List<Keyframe>>> lanes) {

    public BlueprintAnimation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(loopMode, "loopMode");
        Objects.requireNonNull(lanes, "lanes");
        if (lengthSeconds <= 0.0) {
            throw new IllegalArgumentException(
                    "lengthSeconds must be > 0, got " + lengthSeconds);
        }
        // Defensive copy with EnumMap for inner maps.
        Map<String, Map<BoneChannel, List<Keyframe>>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<BoneChannel, List<Keyframe>>> e : lanes.entrySet()) {
            Map<BoneChannel, List<Keyframe>> inner = new EnumMap<>(BoneChannel.class);
            for (Map.Entry<BoneChannel, List<Keyframe>> c : e.getValue().entrySet()) {
                inner.put(c.getKey(), List.copyOf(c.getValue()));
            }
            copy.put(e.getKey(), Map.copyOf(inner));
        }
        lanes = Map.copyOf(copy);
    }

    /** Find the surrounding keyframes for a bone+channel at time {@code t}. */
    public @NotNull KeyframePair sample(@NotNull String bone, @NotNull BoneChannel channel, double t) {
        Map<BoneChannel, List<Keyframe>> bones = lanes.get(bone);
        if (bones == null) {
            return KeyframePair.empty();
        }
        List<Keyframe> frames = bones.get(channel);
        if (frames == null || frames.isEmpty()) {
            return KeyframePair.empty();
        }
        if (frames.size() == 1 || t <= frames.get(0).time()) {
            return new KeyframePair(frames.get(0), frames.get(0), 0.0);
        }
        Keyframe last = frames.get(frames.size() - 1);
        if (t >= last.time()) {
            return new KeyframePair(last, last, 0.0);
        }
        for (int i = 0; i < frames.size() - 1; i++) {
            Keyframe a = frames.get(i);
            Keyframe b = frames.get(i + 1);
            if (t >= a.time() && t <= b.time()) {
                double span = b.time() - a.time();
                double localT = span <= 0.0 ? 0.0 : (t - a.time()) / span;
                return new KeyframePair(a, b, localT);
            }
        }
        return new KeyframePair(last, last, 0.0);
    }

    /** Sampled keyframe pair + local interpolation parameter. */
    public record KeyframePair(@NotNull Keyframe a, @NotNull Keyframe b, double t) {

        public KeyframePair {
            Objects.requireNonNull(a, "a");
            Objects.requireNonNull(b, "b");
        }

        public static @NotNull KeyframePair empty() {
            return new KeyframePair(
                    Keyframe.linear(0, Vec3.ZERO), Keyframe.linear(0, Vec3.ZERO), 0.0);
        }

        /** Resolve to a single interpolated value. */
        public @NotNull Vec3 resolve() {
            return switch (a.interpolation()) {
                case STEP -> a.value();
                // BEZIER / CATMULL_ROM fall back to LINEAR in the foundation —
                // see docs/V3_ROADMAP.md.
                default -> a.value().lerp(b.value(), t);
            };
        }
    }
}
