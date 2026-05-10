package fr.smp.ptr.foundation.drop;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.registry.PtrIdentified;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drops a {@link PtrDropTable} on a boss kill: world drops at the death
 * location, per-player drops either as glowing entity drops (visible only
 * to the recipient) or pushed directly into inventory.
 *
 * <p>Replaces the old {@code LootDispatcher} (vanilla LootTable only) once
 * boss content layers wire {@code PtrDropTable} into their definitions.
 */
public final class DropDispatcher {

    private DropDispatcher() {}

    /** Drop semantic. */
    public enum PerPlayerMode {
        /** Drop on the ground at the boss death location, glow color for the recipient. */
        WORLD_DROP,
        /** Push directly into the recipient's inventory (overflow falls on the ground). */
        DIRECT_INVENTORY
    }

    /**
     * Resolve the roll and place drops in the world / inventories.
     *
     * @param scheduler region scheduler for any async / delayed work
     * @param boss the boss identity (for hologram / audit)
     * @param deadEntity the boss entity (used for the drop centre)
     * @param table the drop table to roll
     * @param leaderboard sorted leaderboard from {@link DamageTracker}
     * @param mode per-player drop semantic
     * @param killer the killing player, may be {@code null}
     */
    public static void dispatch(
            @NotNull SchedulerService scheduler,
            @NotNull PtrIdentified boss,
            @NotNull LivingEntity deadEntity,
            @NotNull PtrDropTable table,
            @NotNull List<DamageTracker.Entry> leaderboard,
            @NotNull PerPlayerMode mode,
            @Nullable Player killer) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(boss, "boss");
        Objects.requireNonNull(deadEntity, "deadEntity");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(leaderboard, "leaderboard");
        Objects.requireNonNull(mode, "mode");

        Location at = deadEntity.getLocation();
        PtrDropTable.Roll roll = table.roll(leaderboard);

        for (ItemStack stack : roll.worldDrops()) {
            at.getWorld().dropItemNaturally(at, stack);
        }

        for (Map.Entry<UUID, List<ItemStack>> entry : roll.perPlayerDrops().entrySet()) {
            Player recipient = Bukkit.getPlayer(entry.getKey());
            for (ItemStack stack : entry.getValue()) {
                if (recipient != null && mode == PerPlayerMode.DIRECT_INVENTORY) {
                    Map<Integer, ItemStack> overflow =
                            recipient.getInventory().addItem(stack);
                    for (ItemStack leftover : overflow.values()) {
                        recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover);
                    }
                } else {
                    Item dropped = at.getWorld().dropItemNaturally(at, stack);
                    dropped.setGlowing(true);
                }
            }
        }

        if (killer != null && killer.isOnline()) {
            killer.sendMessage(boss.displayName());
        }
    }
}
