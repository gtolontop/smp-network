package fr.smp.ptr.util;

import fr.smp.ptr.PtrShowcase;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class Energy {

    private Energy() {}

    public static int get(ItemStack stack, PtrShowcase plugin) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        NamespacedKey k = plugin.key("energy");
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(k, PersistentDataType.INTEGER) ? pdc.get(k, PersistentDataType.INTEGER) : -1;
    }

    public static void set(ItemStack stack, PtrShowcase plugin, int value) {
        if (stack == null) return;
        stack.editMeta(meta -> meta.getPersistentDataContainer()
                .set(plugin.key("energy"), PersistentDataType.INTEGER, Math.max(0, value)));
    }

    public static boolean consume(ItemStack stack, PtrShowcase plugin, int amount) {
        int cur = get(stack, plugin);
        if (cur < 0) {
            // No energy field yet — initialize to 100 and consume
            set(stack, plugin, 100 - amount);
            return true;
        }
        if (cur < amount) return false;
        set(stack, plugin, cur - amount);
        return true;
    }
}
