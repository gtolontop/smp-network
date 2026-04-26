package fr.smp.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class InvseeGUI extends GUIHolder {

    private static final int SIZE = 54;
    private final Player target;
    private final Map<Integer, Integer> slotMap = new HashMap<>();
    private final Map<Integer, String> armorLabels = new HashMap<>();

    public InvseeGUI(Player target) {
        this.target = target;
    }

    public void open(Player viewer) {
        inventory = Bukkit.createInventory(this, SIZE,
                GUIUtil.title("<gradient:#a18cd1:#fbc2eb><bold>Invsee — " + target.getName() + "</bold></gradient>"));
        populate();
        viewer.openInventory(inventory);
    }

    private void populate() {
        inventory.clear();
        slotMap.clear();
        armorLabels.clear();

        ItemStack filler = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);

        inventory.setItem(0, filler);
        mapArmorSlot(1, 39, "Casque");
        mapArmorSlot(2, 38, "Plastron");
        mapArmorSlot(3, 37, "Jambières");
        mapArmorSlot(4, 36, "Bottes");
        inventory.setItem(5, filler);
        mapArmorSlot(6, 40, "Main secondaire");
        inventory.setItem(7, filler);
        inventory.setItem(8, filler);

        for (int i = 9; i <= 35; i++) mapSlot(i, i);

        for (int i = 36; i <= 44; i++) inventory.setItem(i, filler);

        for (int i = 0; i <= 8; i++) mapSlot(45 + i, i);
    }

    private void mapSlot(int guiSlot, int targetSlot) {
        slotMap.put(guiSlot, targetSlot);
        ItemStack item = target.getInventory().getItem(targetSlot);
        inventory.setItem(guiSlot, item != null && !item.getType().isAir() ? item.clone() : null);
    }

    private void mapArmorSlot(int guiSlot, int targetSlot, String label) {
        slotMap.put(guiSlot, targetSlot);
        armorLabels.put(guiSlot, label);
        ItemStack item = target.getInventory().getItem(targetSlot);
        if (item != null && !item.getType().isAir()) {
            inventory.setItem(guiSlot, item.clone());
        } else {
            inventory.setItem(guiSlot, GUIUtil.item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>" + label));
        }
    }

    private void refresh() {
        for (var entry : slotMap.entrySet()) {
            int guiSlot = entry.getKey();
            int targetSlot = entry.getValue();
            ItemStack item = target.getInventory().getItem(targetSlot);
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(guiSlot, item.clone());
            } else {
                String label = armorLabels.get(guiSlot);
                if (label != null) {
                    inventory.setItem(guiSlot, GUIUtil.item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>" + label));
                } else {
                    inventory.setItem(guiSlot, null);
                }
            }
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (rawSlot < 0) return;

        if (rawSlot >= SIZE) {
            if (event.isShiftClick()) return;
            event.setCancelled(false);
            return;
        }

        Integer targetSlot = slotMap.get(rawSlot);
        if (targetSlot == null) return;

        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        ItemStack inTarget = target.getInventory().getItem(targetSlot);
        boolean slotEmpty = inTarget == null || inTarget.getType().isAir();

        if (event.isShiftClick()) {
            if (!slotEmpty) {
                target.getInventory().setItem(targetSlot, null);
                var overflow = event.getWhoClicked().getInventory().addItem(inTarget.clone());
                overflow.values().forEach(it ->
                        event.getWhoClicked().getWorld().dropItemNaturally(event.getWhoClicked().getLocation(), it));
            }
        } else {
            target.getInventory().setItem(targetSlot, cursorEmpty ? null : cursor.clone());
            event.getWhoClicked().setItemOnCursor(slotEmpty ? null : inTarget.clone());
        }

        refresh();
    }
}
