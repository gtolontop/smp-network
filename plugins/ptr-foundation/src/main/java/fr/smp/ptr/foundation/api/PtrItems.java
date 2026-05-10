package fr.smp.ptr.foundation.api;

import fr.smp.ptr.foundation.registry.PtrItemDef;
import fr.smp.ptr.foundation.registry.PtrItemRegistry;
import fr.smp.ptr.foundation.storage.PtrPdcCodec;
import fr.smp.ptr.foundation.storage.PtrPdcKeys;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/** Flat facade over {@link PtrItemRegistry} + PDC item-id lookups. */
public final class PtrItems {

    private PtrItems() {}

    public static @NotNull Map<NamespacedKey, PtrItemDef> all() {
        return PtrFoundationApi.services().get(PtrItemRegistry.class).view();
    }

    public static @NotNull Optional<PtrItemDef> find(@NotNull NamespacedKey id) {
        return PtrFoundationApi.services().get(PtrItemRegistry.class).get(id);
    }

    /** True if the stack carries a foundation item id in its PDC. */
    public static boolean isCustomItem(@NotNull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return PtrPdcCodec.STRING.has(meta.getPersistentDataContainer(), PtrPdcKeys.ITEM_ID);
    }

    /** Read the foundation item id from a stack's PDC, if any. */
    public static @NotNull Optional<NamespacedKey> readItemId(@NotNull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        return PtrPdcCodec.NAMESPACED_KEY.readOpt(
                meta.getPersistentDataContainer(), PtrPdcKeys.ITEM_ID);
    }

    /**
     * Resolve a stack to its {@link PtrItemDef}. Chains PDC lookup with the
     * registry — returns empty if the stack has no id or the id is not
     * registered.
     */
    public static @NotNull Optional<PtrItemDef> byItemStack(@NotNull ItemStack stack) {
        return readItemId(stack).flatMap(PtrItems::find);
    }
}
