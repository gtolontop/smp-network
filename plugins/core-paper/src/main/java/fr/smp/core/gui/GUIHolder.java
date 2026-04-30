package fr.smp.core.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Base holder: any custom GUI extends this so clicks can be routed.
 * The inventory field is lazily set by subclasses right after creation.
 */
public abstract class GUIHolder implements InventoryHolder {

    protected Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Called when a viewer clicks inside this GUI. Event is cancelled before this call. */
    public abstract void onClick(InventoryClickEvent event);

    /**
     * Called when a viewer drags items inside this GUI. Event is cancelled
     * before this call. Default keeps it cancelled — only GUIs that allow
     * free item placement (e.g. SellGUI) should override and re-enable it
     * when the dragged slots are safe.
     */
    public void onDrag(InventoryDragEvent event) {}

    /** Called when a viewer closes the GUI. Default no-op. */
    public void onClose(HumanEntity who) {}
}
