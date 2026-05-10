package fr.smp.ptr.foundation.disguise;

import fr.smp.ptr.foundation.platform.RegionLocator;
import fr.smp.ptr.foundation.storage.PtrPdcCodec;
import fr.smp.ptr.foundation.storage.PtrPdcKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Mob + ItemDisplay rig — used for bosses whose silhouette diverges too far
 * from any vanilla mob.
 *
 * <p>Capacity: <b>unbounded</b> (PDC-keyed instances). Cost: an invisible
 * base mob plus one or more {@link ItemDisplay} entities for the visual.
 *
 * <p>Leaks:
 *
 * <ul>
 *   <li>The base mob's AI still drives pathfinding — pick a base whose AI
 *       silhouette matches your boss (IronGolem ≠ Pillager).
 *   <li>Spectator camera locks onto the invisible base mob, not the
 *       rig. Looks fine, but the camera position is the mob's, not the
 *       visual centre.
 *   <li>Damage indicators show the base mob's name unless overridden via
 *       custom name component.
 * </ul>
 */
public final class DisplayMobCarrier implements DisguiseCarrier<DisplayMobCarrier.State> {

    /** Per-placement state: ({@code baseType}, {@code visual}, {@code displayId}). */
    public record State(
            @NotNull EntityType baseType, @NotNull ItemStack visual, @NotNull NamespacedKey displayId) {

        public State {
            if (!baseType.getEntityClass().isAssignableFrom(Mob.class)
                    && !Mob.class.isAssignableFrom(baseType.getEntityClass())) {
                throw new IllegalArgumentException(
                        "DisplayMobCarrier needs a Mob baseType, got " + baseType);
            }
        }
    }

    @Override
    public @NotNull String kind() {
        return "display_mob";
    }

    @Override
    public int capacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public @NotNull List<String> knownLeaks() {
        return List.of(
                "base mob AI drives pathfinding — pick a base whose silhouette matches",
                "spectator camera follows the invisible base mob, not the rig",
                "damage indicators show base mob name unless overridden with custom name");
    }

    /**
     * Spawns the rig at {@code loc}. Returns the {@link UUID} of the
     * invisible base mob so the caller can re-acquire it.
     */
    @SuppressWarnings("unchecked")
    public @NotNull UUID spawn(@NotNull Location loc, @NotNull State state) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        LivingEntity base =
                (LivingEntity)
                        loc.getWorld()
                                .spawn(
                                        loc,
                                        (Class<? extends LivingEntity>)
                                                state.baseType().getEntityClass(),
                                        e -> {
                                            e.setInvisible(true);
                                            e.setSilent(false);
                                            e.addPotionEffect(
                                                    new PotionEffect(
                                                            PotionEffectType.INVISIBILITY,
                                                            Integer.MAX_VALUE,
                                                            1,
                                                            false,
                                                            false,
                                                            false));
                                            PtrPdcCodec.NAMESPACED_KEY.write(
                                                    e.getPersistentDataContainer(),
                                                    PtrPdcKeys.MOB_ID,
                                                    state.displayId());
                                        });
        ItemDisplay visual =
                loc.getWorld()
                        .spawn(
                                loc,
                                ItemDisplay.class,
                                e -> {
                                    e.setItemStack(state.visual());
                                    PtrPdcCodec.NAMESPACED_KEY.write(
                                            e.getPersistentDataContainer(),
                                            PtrPdcKeys.MOB_ID,
                                            state.displayId());
                                });
        base.addPassenger(visual);
        return base.getUniqueId();
    }

    @Override
    public void place(@NotNull Location loc, @NotNull State state) {
        spawn(loc, state);
    }

    @Override
    public @NotNull Optional<State> readState(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        for (var entity : loc.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
            if (entity instanceof LivingEntity living
                    && living.getPersistentDataContainer().has(PtrPdcKeys.MOB_ID)) {
                NamespacedKey id =
                        PtrPdcCodec.NAMESPACED_KEY.read(
                                living.getPersistentDataContainer(), PtrPdcKeys.MOB_ID);
                ItemStack visual =
                        living.getPassengers().stream()
                                .filter(p -> p instanceof ItemDisplay)
                                .map(p -> ((ItemDisplay) p).getItemStack())
                                .filter(it -> it != null)
                                .findFirst()
                                .orElse(new ItemStack(org.bukkit.Material.BARRIER));
                return Optional.of(new State(living.getType(), visual, id));
            }
        }
        return Optional.empty();
    }

    @Override
    public void clear(@NotNull Location loc) {
        RegionLocator.assertOwnedByCurrentRegion(loc);
        for (var entity : loc.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
            if (entity.getPersistentDataContainer().has(PtrPdcKeys.MOB_ID)) {
                for (var passenger : entity.getPassengers()) {
                    passenger.remove();
                }
                entity.remove();
            }
        }
    }
}
