package fr.smp.core.listeners;

import com.google.common.collect.Multimap;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class AttributeSwapListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        for (Map.Entry<EquipmentSlot, EntityEquipmentChangedEvent.ItemSlotChange> entry : event.getChanges().entrySet()) {
            EquipmentSlot slot = entry.getKey();
            EntityEquipmentChangedEvent.ItemSlotChange change = entry.getValue();
            applyModifiers(player, change.oldItem(), slot, false);
            applyModifiers(player, change.newItem(), slot, true);
        }
    }

    private void applyModifiers(Player player, ItemStack item, EquipmentSlot slot, boolean add) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers()) return;

        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers(slot);
        if (modifiers == null || modifiers.isEmpty()) return;

        for (Map.Entry<Attribute, AttributeModifier> e : modifiers.entries()) {
            AttributeInstance inst = player.getAttribute(e.getKey());
            if (inst == null) continue;
            inst.removeModifier(e.getValue());
            if (add) inst.addModifier(e.getValue());
        }
    }
}
