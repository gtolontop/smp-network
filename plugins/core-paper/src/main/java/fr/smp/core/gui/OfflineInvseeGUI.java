package fr.smp.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class OfflineInvseeGUI extends GUIHolder {

    private static final int SIZE = 54;
    private final String targetName;

    public OfflineInvseeGUI(String targetName) {
        this.targetName = targetName;
    }

    public void open(Player viewer, YamlConfiguration yaml) {
        inventory = Bukkit.createInventory(this, SIZE,
                GUIUtil.title("<gradient:#a18cd1:#fbc2eb><bold>Invsee (hors-ligne) — " + targetName + "</bold></gradient>"));
        populate(yaml);
        viewer.openInventory(inventory);
    }

    private void populate(YamlConfiguration yaml) {
        inventory.clear();
        ItemStack filler = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);

        ItemStack[] contents = deserializeContents(yaml.getList("inventory.contents"));
        ItemStack[] armor = deserializeContents(yaml.getList("inventory.armor"));
        ItemStack offhand = yaml.getItemStack("inventory.offhand");

        inventory.setItem(0, filler);
        setSlot(1, armor.length > 3 ? armor[3] : null, "Casque");
        setSlot(2, armor.length > 2 ? armor[2] : null, "Plastron");
        setSlot(3, armor.length > 1 ? armor[1] : null, "Jambières");
        setSlot(4, armor.length > 0 ? armor[0] : null, "Bottes");
        inventory.setItem(5, filler);
        setSlot(6, offhand, "Main secondaire");
        inventory.setItem(7, filler);
        inventory.setItem(8, filler);

        for (int i = 9; i <= 35; i++) {
            inventory.setItem(i, i < contents.length ? contents[i] : null);
        }

        for (int i = 36; i <= 44; i++) inventory.setItem(i, filler);

        for (int i = 0; i <= 8; i++) {
            inventory.setItem(45 + i, i < contents.length ? contents[i] : null);
        }
    }

    private void setSlot(int guiSlot, ItemStack item, String label) {
        if (item != null && !item.getType().isAir()) {
            inventory.setItem(guiSlot, item);
        } else {
            inventory.setItem(guiSlot, GUIUtil.item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>" + label));
        }
    }

    private ItemStack[] deserializeContents(List<?> list) {
        if (list == null) return new ItemStack[0];
        return list.stream()
                .map(o -> o instanceof ItemStack i ? i : null)
                .toArray(ItemStack[]::new);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }
}
