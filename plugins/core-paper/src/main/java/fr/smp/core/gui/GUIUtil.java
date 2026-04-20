package fr.smp.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GUIUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GUIUtil() {}

    public static Component title(String mini) {
        return MM.deserialize(mini);
    }

    public static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (name != null) meta.displayName(MM.deserialize("<!italic>" + name));
        if (lore != null && lore.length > 0) {
            List<Component> comp = new ArrayList<>();
            for (String l : lore) comp.add(l.isEmpty()
                    ? MM.deserialize("<!italic> ")
                    : MM.deserialize("<!italic>" + l));
            meta.lore(comp);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack filler(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize("<!italic><dark_gray> </dark_gray>"));
        it.setItemMeta(meta);
        return it;
    }

    public static void fillBorder(Inventory inv, Material m) {
        ItemStack filler = filler(m);
        int rows = inv.getSize() / 9;
        for (int c = 0; c < 9; c++) {
            inv.setItem(c, filler);
            inv.setItem((rows - 1) * 9 + c, filler);
        }
        for (int r = 1; r < rows - 1; r++) {
            inv.setItem(r * 9, filler);
            inv.setItem(r * 9 + 8, filler);
        }
    }

    public static void fillEmpty(Inventory inv, Material m) {
        ItemStack filler = filler(m);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType().isAir()) inv.setItem(i, filler);
        }
    }
}
