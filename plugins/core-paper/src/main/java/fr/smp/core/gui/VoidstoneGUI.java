package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import fr.smp.core.voidstone.VoidstoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VoidstoneGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 45;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 46;
    private static final int SLOT_DROP_PAGE = 47;
    private static final int SLOT_PAGE_LABEL = 49;
    private static final int SLOT_DROP_ALL = 51;
    private static final int SLOT_NEXT = 53;

    private final SMPCore plugin;
    private final String itemId;
    private int page;

    public VoidstoneGUI(SMPCore plugin, String itemId) {
        this.plugin = plugin;
        this.itemId = itemId;
    }

    public void open(Player player) {
        if (!render(player, true)) return;
        player.openInventory(inventory);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == SLOT_PREV) {
            page--;
            render(player, true);
            return;
        }
        if (slot == SLOT_NEXT) {
            page++;
            render(player, true);
            return;
        }
        if (slot == SLOT_DROP_PAGE) {
            dropPage(player);
            return;
        }
        if (slot == SLOT_DROP_ALL) {
            dropAll(player);
            return;
        }
        if (slot == SLOT_INFO || slot == SLOT_PAGE_LABEL || slot >= 45) return;

        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) return;

        VoidstoneManager.InventoryItemRef ref = requireItem(player);
        if (ref == null) return;

        List<Map.Entry<Material, Integer>> expanded = expandStacks(manager.snapshot(ref.item()));
        int index = page * PER_PAGE + slot;
        if (index >= expanded.size()) return;

        Map.Entry<Material, Integer> entry = expanded.get(index);
        Material material = entry.getKey();
        int stackAmount = entry.getValue();
        int totalAmount = manager.storedAmount(ref.item(), material);
        ClickType click = event.getClick();

        if (click == ClickType.SHIFT_LEFT) {
            giveToPlayer(player, material, totalAmount);
        } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            dropFromVoidstone(player, material, click == ClickType.SHIFT_RIGHT ? totalAmount : stackAmount);
        } else {
            giveToPlayer(player, material, stackAmount);
        }

        render(player, true);
    }

    private boolean render(Player player, boolean closeOnMissing) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) return false;

        VoidstoneManager.InventoryItemRef ref = resolveItem(player, closeOnMissing);
        if (ref == null) return false;

        List<Map.Entry<Material, Integer>> snapshot = manager.snapshot(ref.item());
        List<Map.Entry<Material, Integer>> expanded = expandStacks(snapshot);

        int pages = Math.max(1, (int) Math.ceil(expanded.size() / (double) PER_PAGE));
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;

        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 54, GUIUtil.title("<gradient:#0f2027:#203a43:#2c5364><bold>Voidstone</bold></gradient>"));
        }

        int offset = page * PER_PAGE;
        int end = Math.min(offset + PER_PAGE, expanded.size());
        int totalStored = manager.totalStored(ref.item());

        for (int slot = 0; slot < PER_PAGE; slot++) {
            int index = offset + slot;
            if (index >= end) {
                inventory.setItem(slot, null);
                continue;
            }

            Map.Entry<Material, Integer> entry = expanded.get(index);
            Material material = entry.getKey();
            int amount = entry.getValue();
            int totalMaterial = manager.storedAmount(ref.item(), material);

            ItemStack display = new ItemStack(material, Math.max(1, amount));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.displayName(MM.deserialize("<!italic><white>" + VoidstoneManager.prettyName(material)
                        + " <dark_gray>x</dark_gray> <yellow>" + format(amount) + "</yellow></white>"));
                List<Component> lore = new ArrayList<>();
                lore.add(MM.deserialize("<!italic><gray>This stack: <yellow>" + format(amount) + "</yellow></gray>"));
                lore.add(MM.deserialize("<!italic><gray>Total stored: <yellow>" + format(totalMaterial) + "</yellow></gray>"));
                lore.add(Component.empty());
                lore.add(MM.deserialize("<!italic><green>Left-click: take this stack</green>"));
                lore.add(MM.deserialize("<!italic><green>Shift-left: take all of this material</green>"));
                lore.add(MM.deserialize("<!italic><gray>Right-click: drop this stack</gray>"));
                lore.add(MM.deserialize("<!italic><gray>Shift-right: drop all of this material</gray>"));
                meta.lore(lore.stream()
                        .map(component -> component.decoration(TextDecoration.ITALIC, false))
                        .toList());
                display.setItemMeta(meta);
            }
            inventory.setItem(slot, display);
        }

        ItemStack fill = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, fill);

        if (page > 0) {
            inventory.setItem(SLOT_PREV, GUIUtil.item(Material.ARROW, "<yellow>◀ Previous page</yellow>"));
        }
        if (page + 1 < pages) {
            inventory.setItem(SLOT_NEXT, GUIUtil.item(Material.ARROW, "<yellow>Next page ▶</yellow>"));
        }

        inventory.setItem(SLOT_INFO, GUIUtil.item(Material.ECHO_SHARD,
                "<gradient:#0f2027:#203a43:#2c5364><bold>Voidstone</bold></gradient>",
                "<gray>Total stored: <yellow>" + format(totalStored) + "</yellow></gray>",
                "<gray>Material types: <yellow>" + snapshot.size() + "</yellow></gray>",
                "<gray>Capacity: <yellow>" + format(VoidstoneManager.MAX_PER_MATERIAL) + "</yellow> each</gray>",
                "",
                "<dark_gray>Put it in a hopper to extract automatically.</dark_gray>"));

        inventory.setItem(SLOT_PAGE_LABEL, GUIUtil.item(Material.PAPER,
                "<white><bold>Page " + (page + 1) + " / " + pages + "</bold></white>",
                "<gray>Material types: <yellow>" + snapshot.size() + "</yellow></gray>",
                "<gray>Physical stacks: <yellow>" + expanded.size() + "</yellow></gray>"));

        inventory.setItem(SLOT_DROP_PAGE, GUIUtil.item(Material.HOPPER,
                "<gold><bold>Drop this page</bold></gold>",
                "",
                "<gray>Throw every stack from the</gray>",
                "<gray>current page in front of you.</gray>",
                "",
                "<yellow>▶ Click to drop</yellow>"));

        inventory.setItem(SLOT_DROP_ALL, GUIUtil.item(Material.TNT,
                "<red><bold>Drop everything</bold></red>",
                "",
                "<gray>Throw the full content</gray>",
                "<gray>stored in this voidstone.</gray>",
                "",
                "<red>▶ Click to drop all</red>"));

        return true;
    }

    private void giveToPlayer(Player player, Material material, int requestedAmount) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) return;

        VoidstoneManager.InventoryItemRef ref = requireItem(player);
        if (ref == null) return;

        int available = manager.storedAmount(ref.item(), material);
        int remaining = Math.min(requestedAmount, available);
        int given = 0;

        while (remaining > 0) {
            int take = Math.min(remaining, Math.max(1, material.getMaxStackSize()));
            ItemStack stack = new ItemStack(material, take);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            int left = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            int inserted = take - left;
            if (inserted <= 0) break;
            given += inserted;
            remaining -= inserted;
            if (left > 0) break;
        }

        if (given > 0) {
            manager.removeStored(ref.item(), material, given);
            player.getInventory().setItem(ref.slot(), ref.item());
        }
        if (remaining > 0) {
            player.sendMessage(Msg.info("<gray>Inventory full. <yellow>" + format(remaining)
                    + "</yellow> stay in the voidstone.</gray>"));
        }
    }

    private void dropFromVoidstone(Player player, Material material, int requestedAmount) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) return;

        VoidstoneManager.InventoryItemRef ref = requireItem(player);
        if (ref == null) return;

        int stored = manager.storedAmount(ref.item(), material);
        int toDrop = Math.min(requestedAmount, stored);
        if (toDrop <= 0) return;

        manager.removeStored(ref.item(), material, toDrop);
        player.getInventory().setItem(ref.slot(), ref.item());

        int remaining = toDrop;
        while (remaining > 0) {
            int take = Math.min(remaining, Math.max(1, material.getMaxStackSize()));
            throwFromPlayer(player, new ItemStack(material, take));
            remaining -= take;
        }
    }

    private void dropPage(Player player) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) return;

        VoidstoneManager.InventoryItemRef ref = requireItem(player);
        if (ref == null) return;

        List<Map.Entry<Material, Integer>> expanded = expandStacks(manager.snapshot(ref.item()));
        int offset = page * PER_PAGE;
        int end = Math.min(offset + PER_PAGE, expanded.size());
        if (offset >= end) {
            player.sendMessage(Msg.info("<gray>Nothing to drop on this page.</gray>"));
            return;
        }

        int dropped = 0;
        for (int index = offset; index < end; index++) {
            Map.Entry<Material, Integer> entry = expanded.get(index);
            int actual = manager.removeStored(ref.item(), entry.getKey(), entry.getValue());
            if (actual <= 0) continue;
            int remaining = actual;
            while (remaining > 0) {
                int take = Math.min(remaining, Math.max(1, entry.getKey().getMaxStackSize()));
                throwFromPlayer(player, new ItemStack(entry.getKey(), take));
                remaining -= take;
            }
            dropped += actual;
        }

        player.getInventory().setItem(ref.slot(), ref.item());
        player.sendMessage(Msg.ok("<green>Page dropped: <yellow>" + format(dropped) + "</yellow> item(s).</green>"));
        render(player, true);
    }

    private void dropAll(Player player) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) return;

        VoidstoneManager.InventoryItemRef ref = requireItem(player);
        if (ref == null) return;

        List<Map.Entry<Material, Integer>> snapshot = new ArrayList<>(manager.snapshot(ref.item()));
        int total = snapshot.stream().mapToInt(Map.Entry::getValue).sum();
        if (total <= 0) {
            player.sendMessage(Msg.info("<gray>The voidstone is empty.</gray>"));
            return;
        }

        for (Map.Entry<Material, Integer> entry : snapshot) {
            int remaining = manager.removeStored(ref.item(), entry.getKey(), entry.getValue());
            while (remaining > 0) {
                int take = Math.min(remaining, Math.max(1, entry.getKey().getMaxStackSize()));
                throwFromPlayer(player, new ItemStack(entry.getKey(), take));
                remaining -= take;
            }
        }

        player.getInventory().setItem(ref.slot(), ref.item());
        player.sendMessage(Msg.ok("<green>Everything dropped: <yellow>" + format(total) + "</yellow> item(s).</green>"));
        render(player, true);
    }

    private VoidstoneManager.InventoryItemRef requireItem(Player player) {
        return resolveItem(player, true);
    }

    private VoidstoneManager.InventoryItemRef resolveItem(Player player, boolean closeOnMissing) {
        VoidstoneManager manager = plugin.voidstones();
        if (manager == null) return null;
        VoidstoneManager.InventoryItemRef ref = manager.find(player.getInventory(), itemId);
        if (ref == null && closeOnMissing) {
            player.closeInventory();
            player.sendMessage(Msg.err("This voidstone is no longer in your inventory."));
        }
        return ref;
    }

    private static List<Map.Entry<Material, Integer>> expandStacks(List<Map.Entry<Material, Integer>> snapshot) {
        List<Map.Entry<Material, Integer>> expanded = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : snapshot) {
            int remaining = entry.getValue();
            int maxStack = Math.max(1, entry.getKey().getMaxStackSize());
            while (remaining > 0) {
                int take = Math.min(remaining, maxStack);
                expanded.add(Map.entry(entry.getKey(), take));
                remaining -= take;
            }
        }
        return expanded;
    }

    private void throwFromPlayer(Player player, ItemStack stack) {
        Location eye = player.getEyeLocation();
        Item item = player.getWorld().dropItem(eye, stack);
        Vector direction = eye.getDirection().normalize().multiply(0.4);
        item.setVelocity(direction);
        item.setPickupDelay(10);
        item.setThrower(player.getUniqueId());
    }

    private String format(int amount) {
        return String.format(Locale.US, "%,d", amount);
    }
}
