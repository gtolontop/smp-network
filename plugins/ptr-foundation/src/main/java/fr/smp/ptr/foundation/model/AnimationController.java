package fr.smp.ptr.foundation.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * State machine that owns the active set of {@link AnimationProperty}s for
 * one {@link ActiveModel}.
 *
 * <p>Each {@link ModelState} maps to an animation name on the blueprint
 * (configurable per controller). Calling {@link #setState} fades the old
 * state's animation out over {@code lerpOut} seconds while fading the new
 * one in over {@code lerpIn} seconds.
 *
 * <p>One-shot animations (ATTACK / HURT) are played via {@link
 * #playOneShot} and revert to the previous state on completion.
 */
public final class AnimationController {

    private final Blueprint blueprint;
    private final Map<ModelState, String> stateMappings = new EnumMap<>(ModelState.class);
    private final Map<String, AnimationProperty> active = new LinkedHashMap<>();

    private ModelState currentState = ModelState.IDLE;
    private @Nullable ModelState revertState = null;
    private double defaultLerpIn = 0.2;
    private double defaultLerpOut = 0.2;
    private double defaultSpeed = 1.0;

    public AnimationController(@NotNull Blueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
    }

    /** Map a {@link ModelState} to a blueprint animation name. */
    public @NotNull AnimationController bind(@NotNull ModelState state, @NotNull String animation) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(animation, "animation");
        if (!blueprint.animations().containsKey(animation)) {
            throw new IllegalArgumentException(
                    "Blueprint " + blueprint.id() + " has no animation named '" + animation + "'");
        }
        stateMappings.put(state, animation);
        return this;
    }

    public @NotNull AnimationController defaultLerpIn(double seconds) {
        this.defaultLerpIn = Math.max(0.0, seconds);
        return this;
    }

    public @NotNull AnimationController defaultLerpOut(double seconds) {
        this.defaultLerpOut = Math.max(0.0, seconds);
        return this;
    }

    public @NotNull AnimationController defaultSpeed(double speed) {
        if (speed <= 0.0) {
            throw new IllegalArgumentException("speed must be > 0");
        }
        this.defaultSpeed = speed;
        return this;
    }

    public @NotNull ModelState currentState() {
        return currentState;
    }

    /**
     * Transition into {@code state}. If the new state matches the current
     * one AND its animation is already playing, no-op. Otherwise we (re-)
     * start the bound animation and fade out the old one.
     */
    public void setState(@NotNull ModelState state) {
        Objects.requireNonNull(state, "state");
        String newAnimName = stateMappings.get(state);
        boolean alreadyPlaying =
                state == currentState
                        && newAnimName != null
                        && active.containsKey(newAnimName);
        if (alreadyPlaying) {
            return;
        }
        String oldAnimName = stateMappings.get(currentState);
        if (oldAnimName != null && !oldAnimName.equals(newAnimName)) {
            AnimationProperty old = active.get(oldAnimName);
            if (old != null) {
                old.requestStop();
            }
        }
        currentState = state;
        if (newAnimName != null) {
            startAnimation(newAnimName);
        }
    }

    /**
     * Play a one-shot animation (e.g. ATTACK). When it finishes, the
     * controller automatically returns to {@code revertTo}.
     */
    public void playOneShot(@NotNull String animationName, @NotNull ModelState revertTo) {
        Objects.requireNonNull(animationName, "animationName");
        if (!blueprint.animations().containsKey(animationName)) {
            return;
        }
        revertState = revertTo;
        startAnimation(animationName);
    }

    private void startAnimation(String animationName) {
        BlueprintAnimation anim = blueprint.animations().get(animationName);
        if (anim == null) {
            return;
        }
        active.computeIfAbsent(
                animationName,
                n -> new AnimationProperty(anim, defaultLerpIn, defaultLerpOut, defaultSpeed));
    }

    /**
     * Advance every active animation by {@code dtSeconds} and compute the
     * per-bone blended transforms. Returns an immutable snapshot.
     */
    public @NotNull Map<String, BoneTransform> tick(double dtSeconds) {
        Map<String, BoneTransform> result = new HashMap<>();

        // Tick + remove finished
        active.values().forEach(p -> p.tick(dtSeconds));
        active.entrySet().removeIf(e -> e.getValue().isFinished());

        // If we just lost a one-shot and have a revertState, kick it on.
        if (revertState != null && active.isEmpty()) {
            ModelState target = revertState;
            revertState = null;
            setState(target);
        }

        // Blend by weight.
        for (AnimationProperty p : active.values()) {
            double t = p.sampleTime();
            double w = p.weight();
            if (w <= 0.0) {
                continue;
            }
            for (Map.Entry<String, Map<BoneChannel, java.util.List<Keyframe>>> lane :
                    p.animation().lanes().entrySet()) {
                String bone = lane.getKey();
                Vec3 pos = sample(p.animation(), bone, BoneChannel.POSITION, t);
                Vec3 rot = sample(p.animation(), bone, BoneChannel.ROTATION, t);
                Vec3 scl = sampleScale(p.animation(), bone, t);
                BoneTransform existing = result.get(bone);
                BoneTransform contribution = new BoneTransform(pos.scale(w), rot.scale(w), scl);
                result.put(bone, existing == null ? contribution : existing.add(contribution));
            }
        }
        return result;
    }

    private static Vec3 sample(BlueprintAnimation anim, String bone, BoneChannel ch, double t) {
        return anim.sample(bone, ch, t).resolve();
    }

    private static Vec3 sampleScale(BlueprintAnimation anim, String bone, double t) {
        BlueprintAnimation.KeyframePair p = anim.sample(bone, BoneChannel.SCALE, t);
        Vec3 v = p.resolve();
        // Scale defaults to (1,1,1) when no keyframe; the sample resolves to
        // (0,0,0) by default which would collapse the bone. Coalesce here.
        if (v.x() == 0.0 && v.y() == 0.0 && v.z() == 0.0) {
            return new Vec3(1, 1, 1);
        }
        return v;
    }

    /** Lookup the mapped animation name for {@code state}. */
    public @NotNull Optional<String> animationFor(@NotNull ModelState state) {
        return Optional.ofNullable(stateMappings.get(state));
    }

    /** Per-tick result for one bone. */
    public record BoneTransform(@NotNull Vec3 position, @NotNull Vec3 rotation, @NotNull Vec3 scale) {

        public BoneTransform {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(rotation, "rotation");
            Objects.requireNonNull(scale, "scale");
        }

        public @NotNull BoneTransform add(@NotNull BoneTransform other) {
            return new BoneTransform(
                    position.add(other.position),
                    rotation.add(other.rotation),
                    scale.lerp(other.scale, 0.5));
        }
    }
}
