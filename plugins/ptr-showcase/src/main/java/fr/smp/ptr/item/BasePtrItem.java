package fr.smp.ptr.item;

import fr.smp.ptr.PtrShowcase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public abstract class BasePtrItem implements PtrItem {

    protected final PtrShowcase plugin;

    protected BasePtrItem(PtrShowcase plugin) { this.plugin = plugin; }

    @Override public abstract String id();
    @Override public abstract String displayName();
    protected abstract Material material();
    protected int customModelData() { return 0; }
    protected List<String> lore() { return List.of(); }

    @Override
    public ItemStack create() {
        ItemStack stack = new ItemStack(material());
        stack.editMeta(meta -> {
            meta.displayName(Component.text(displayName(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            int cmd = customModelData();
            if (cmd > 0) {
                try {
                    var comp = meta.getCustomModelDataComponent();
                    comp.setFloats(java.util.List.of((float) cmd));
                    meta.setCustomModelDataComponent(comp);
                } catch (Throwable t) {
                    meta.setCustomModelData(cmd);
                }
            }
            List<String> raw = lore();
            if (!raw.isEmpty()) {
                List<Component> comps = new ArrayList<>();
                for (String line : raw) {
                    comps.add(Component.text(line, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(comps);
            }
            NamespacedKey k = ItemRegistry.idKey();
            if (k != null) meta.getPersistentDataContainer().set(k, PersistentDataType.STRING, id());
            meta.getPersistentDataContainer().set(plugin.key("energy"), PersistentDataType.INTEGER, 100);
        });
        return stack;
    }

    protected NamespacedKey pdc(String name) { return plugin.key(name); }
}
