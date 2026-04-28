package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public class WorldChangeModule implements Listener {

    private final SMPLogger plugin;

    public WorldChangeModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        StringBuilder sb = new StringBuilder();
        for (Component line : e.lines()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(PlainTextComponentSerializer.plainText().serialize(line));
        }
        EventBuilder.begin(plugin)
                .action(Action.SIGN_EDIT)
                .actor(e.getPlayer())
                .at(e.getBlock())
                .material(e.getBlock().getType())
                .text(sb.toString())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemFrame(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame fr)) return;
        ItemStack held = e.getPlayer().getInventory().getItemInMainHand();
        boolean hasItem = fr.getItem() != null && !fr.getItem().getType().isAir();

        if (!hasItem && held != null && !held.getType().isAir()) {
            EventBuilder.begin(plugin)
                    .action(Action.ITEMFRAME_PLACE)
                    .actor(e.getPlayer())
                    .at(fr)
                    .material(held.getType())
                    .item(held)
                    .submit();
        } else if (hasItem) {
            EventBuilder.begin(plugin)
                    .action(Action.ITEMFRAME_ROTATE)
                    .actor(e.getPlayer())
                    .at(fr)
                    .material(fr.getItem().getType())
                    .submit();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("removal") // AnvilInventory#getRenameText replacement is not yet stable in this Paper version.
    public void onAnvil(PrepareAnvilEvent e) {
        AnvilInventory anv = e.getInventory();
        String name = anv.getRenameText();
        if (name == null || name.isEmpty()) return;
        ItemStack result = e.getResult();
        ItemStack input = anv.getItem(0);
        if (input == null || input.getType().isAir()) return;
        Player viewer = e.getViewers().isEmpty() ? null : (Player) e.getViewers().get(0);
        if (viewer == null) return;
        EventBuilder.begin(plugin)
                .action(Action.ANVIL_RENAME)
                .actor(viewer)
                .at(viewer)
                .material(input.getType())
                .text(name)
                .item(result == null ? input : result)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        EventBuilder.begin(plugin)
                .action(Action.ARMOR_STAND_EQUIP)
                .actor(e.getPlayer())
                .at(e.getRightClicked())
                .material(e.getPlayerItem() != null ? e.getPlayerItem().getType() : org.bukkit.Material.AIR)
                .item(e.getPlayerItem())
                .submit();
    }
}
