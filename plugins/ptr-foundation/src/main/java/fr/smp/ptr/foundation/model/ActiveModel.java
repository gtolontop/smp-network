package fr.smp.ptr.foundation.model;

import fr.smp.ptr.foundation.platform.SchedulerService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Live instance of a {@link Blueprint} attached to a {@link LivingEntity}.
 *
 * <p>One {@link BoneInstance} (ItemDisplay) per bone. A region-scheduler
 * tick samples the {@link AnimationController}'s blended transforms and
 * pushes them onto the ItemDisplays.
 *
 * <p>Lifecycle:
 *
 * <ul>
 *   <li>{@link #spawn} — builds every bone's ItemDisplay at the host's
 *       location, sets the initial state to {@link ModelState#SPAWN}.
 *   <li>{@link #setState} — transitions the controller state.
 *   <li>{@link #destroy} — removes every ItemDisplay and cancels the tick.
 * </ul>
 */
public final class ActiveModel {

    private static final double TICK_SECONDS = 1.0 / 20.0;

    private final Blueprint blueprint;
    private final LivingEntity host;
    private final SchedulerService scheduler;
    private final AnimationController controller;
    private final Map<String, BoneInstance> bones = new LinkedHashMap<>();
    private @Nullable ScheduledTask tickTask;
    private boolean alive = false;

    public ActiveModel(
            @NotNull Blueprint blueprint,
            @NotNull LivingEntity host,
            @NotNull SchedulerService scheduler) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.host = Objects.requireNonNull(host, "host");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.controller = new AnimationController(blueprint);
    }

    public @NotNull AnimationController controller() {
        return controller;
    }

    public @NotNull Blueprint blueprint() {
        return blueprint;
    }

    public @NotNull LivingEntity host() {
        return host;
    }

    public @NotNull Map<String, BoneInstance> bones() {
        return Map.copyOf(bones);
    }

    /** Spawn every bone's ItemDisplay at the host's location and start ticking. */
    public void spawn(@NotNull Plugin plugin) {
        if (alive) {
            return;
        }
        alive = true;
        Location anchor = host.getLocation();
        for (BlueprintBone bone : blueprint.bones().values()) {
            ItemDisplay display =
                    anchor.getWorld()
                            .spawn(
                                    anchor,
                                    ItemDisplay.class,
                                    d -> {
                                        d.setItemStack(new ItemStack(Material.AIR));
                                        d.setBillboard(Display.Billboard.FIXED);
                                        d.setBrightness(new Display.Brightness(15, 15));
                                    });
            bones.put(bone.name(), new BoneInstance(bone, display));
        }
        // Wire parent references after every bone exists.
        for (BoneInstance b : bones.values()) {
            String parent = b.bone().parent();
            if (parent != null) {
                b.setParent(bones.get(parent));
            }
        }
        controller.setState(ModelState.SPAWN);
        tickTask = scheduler.runOnEntityTimer(host, this::tick, this::destroy, 1L, 1L);
    }

    /** High-level state transition. */
    public void setState(@NotNull ModelState state) {
        controller.setState(state);
    }

    /** Play a one-shot animation, returning to {@code revertTo} after it finishes. */
    public void playOneShot(@NotNull String animation, @NotNull ModelState revertTo) {
        controller.playOneShot(animation, revertTo);
    }

    /** Replace one bone's render item — e.g. swap a weapon. */
    public void setBoneItem(@NotNull String boneName, @NotNull ItemStack item) {
        BoneInstance bi = bones.get(boneName);
        if (bi != null) {
            bi.setItem(item);
        }
    }

    private void tick() {
        if (!host.isValid() || host.isDead()) {
            destroy();
            return;
        }
        Map<String, AnimationController.BoneTransform> transforms = controller.tick(TICK_SECONDS);
        Location anchor = host.getLocation();
        for (BoneInstance bi : bones.values()) {
            AnimationController.BoneTransform t = transforms.get(bi.bone().name());
            if (t == null) {
                bi.applyAnimation(Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1));
            } else {
                bi.applyAnimation(t.position(), t.rotation(), t.scale());
            }
            bi.refresh(anchor);
        }
    }

    /** Remove every spawned ItemDisplay and cancel the tick task. */
    public void destroy() {
        if (!alive) {
            return;
        }
        alive = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        List<BoneInstance> snapshot = new ArrayList<>(bones.values());
        bones.clear();
        for (BoneInstance bi : snapshot) {
            bi.destroy();
        }
    }

    public boolean isAlive() {
        return alive;
    }
}
