package fr.smp.ptr.foundation.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.jetbrains.annotations.NotNull;

/**
 * Dispatches loot when a boss dies.
 *
 * <p>Resolves the vanilla {@link LootTable} bound to the boss's drop id and
 * appends custom drops added by content layers. The foundation does not own
 * any loot tables — content registers per-boss tables in datapacks and
 * passes their ids via {@link BossDefinition#dropTableId()}.
 */
public final class LootDispatcher {

    /** Drop {@code table}'s loot at {@code center}, attributing to {@code killer} if any. */
    public void dispatch(
            @NotNull LivingEntity boss,
            @org.jetbrains.annotations.Nullable Player killer,
            @NotNull Location center,
            @NotNull NamespacedKey tableId,
            @NotNull List<ItemStack> extraDrops) {
        Objects.requireNonNull(boss, "boss");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(extraDrops, "extraDrops");

        LootTable table = Bukkit.getLootTable(tableId);
        List<ItemStack> all = new ArrayList<>(extraDrops);
        if (table != null) {
            float luck = 0.0f;
            if (killer != null) {
                AttributeInstance attr = killer.getAttribute(Attribute.LUCK);
                if (attr != null) {
                    luck = (float) attr.getValue();
                }
            }
            LootContext context =
                    new LootContext.Builder(center)
                            .killer(killer)
                            .lootedEntity(boss)
                            .luck(luck)
                            .build();
            all.addAll(table.populateLoot(java.util.concurrent.ThreadLocalRandom.current(), context));
        }
        for (ItemStack item : all) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                center.getWorld().dropItemNaturally(center, item);
            }
        }
    }
}
