package fr.smp.ptr.item;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.item.impl.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;

public class ItemRegistry {

    private static NamespacedKey ID_KEY;
    private final PtrShowcase plugin;
    private final Map<String, PtrItem> items = new LinkedHashMap<>();

    public ItemRegistry(PtrShowcase plugin) {
        this.plugin = plugin;
        ID_KEY = plugin.key("item_id");
    }

    public void registerAll() {
        register(new ForeuseItem(plugin));
        register(new TronconneuseItem(plugin));
        register(new GrappinItem(plugin));
        register(new ScannerItem(plugin));
        register(new VoidstoneItem(plugin));
        register(new BoussoleBossItem(plugin));
        register(new MarteauBuildItem(plugin));
        register(new TotemAlarmeItem(plugin));
    }

    public void register(PtrItem item) {
        items.put(item.id(), item);
    }

    public PtrItem get(String id) { return items.get(id); }
    public Map<String, PtrItem> all() { return items; }
    public int size() { return items.size(); }

    public static NamespacedKey idKey() { return ID_KEY; }

    public static String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || ID_KEY == null) return null;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(ID_KEY, PersistentDataType.STRING)
                ? pdc.get(ID_KEY, PersistentDataType.STRING) : null;
    }

    public PtrItem fromStack(ItemStack stack) {
        String id = idOf(stack);
        return id == null ? null : items.get(id);
    }
}
