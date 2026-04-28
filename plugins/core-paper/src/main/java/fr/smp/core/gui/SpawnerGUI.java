package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.SpawnerManager;
import fr.smp.core.managers.SpawnerManager.SpawnerMode;
import fr.smp.core.managers.SpawnerType;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI d'un spawner custom. 54 slots:
 *  - slots 0-44 (5 lignes) : affichage loot paginé (45 items/page)
 *  - slot 45  : page précédente
 *  - slot 46  : info (type, stack, total)
 *  - slot 47  : drop page actuelle
 *  - slot 49  : indicateur de page
 *  - slot 51  : drop tout
 *  - slot 53  : page suivante
 *
 * Le loot est découpé en stacks physiques (64 max) → un slot par stack,
 * pas un slot par matériau. Clic gauche: prend ce stack. Shift+clic gauche:
 * prend tout le matériau. Clic droit: drop ce stack. Shift+clic droit: drop
 * tout le matériau.
 */
public class SpawnerGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 45;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 46;
    private static final int SLOT_DROP_PAGE = 47;
    private static final int SLOT_MODE = 48;
    private static final int SLOT_PAGE_LABEL = 49;
    private static final int SLOT_DROP_ALL = 51;
    private static final int SLOT_NEXT = 53;

    private final SMPCore plugin;
    private final SpawnerManager.Spawner spawner;
    private int page;

    public SpawnerGUI(SMPCore plugin, SpawnerManager.Spawner spawner) {
        this.plugin = plugin;
        this.spawner = spawner;
        this.page = 0;
    }

    public void open(Player p) {
        render();
        p.openInventory(this.inventory);
    }

    public SpawnerManager.Spawner spawner() { return spawner; }

    private void render() {
        SpawnerType type = spawner.type;
        Inventory inv = this.inventory;
        if (inv == null) {
            String title = type.colorTag() + "<bold>Spawner " + type.display() + "</bold>"
                    + (spawner.stack > 1 ? " <yellow><bold>×" + spawner.stack + "</bold></yellow>" : "");
            inv = Bukkit.createInventory(this, 54, GUIUtil.title(title));
            this.inventory = inv;
        }

        List<Map.Entry<Material, Integer>> snapshot = spawner.snapshot();
        List<Map.Entry<Material, Integer>> expanded = expandStacks(snapshot);
        int pages = Math.max(1, (int) Math.ceil(expanded.size() / (double) PER_PAGE));
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        int offset = page * PER_PAGE;
        int end = Math.min(offset + PER_PAGE, expanded.size());
        int totalItems = 0;
        for (Map.Entry<Material, Integer> e : snapshot) totalItems += e.getValue();

        // Rows 0-4 (slots 0..44) = loot, on ne touche pas à la row 5 (controls)
        for (int slot = 0; slot < PER_PAGE; slot++) {
            int idx = offset + slot;
            if (idx >= end) { inv.setItem(slot, null); continue; }
            Map.Entry<Material, Integer> entry = expanded.get(idx);
            Material mat = entry.getKey();
            int amount = entry.getValue();
            int totalMat = spawner.loot.getOrDefault(mat, 0);
            ItemStack display = new ItemStack(mat, Math.max(1, amount));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.displayName(MM.deserialize(
                        "<white>" + prettyName(mat) + " <dark_gray>× <yellow>" + amount + "</yellow></white>")
                        .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(MM.deserialize("<gray>Ce stack: <yellow>" + amount + "</yellow></gray>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(MM.deserialize("<gray>Total stocké: <yellow>" + totalMat + "</yellow></gray>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(MM.deserialize("<green>▶ Clic: prendre ce stack</green>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(MM.deserialize("<green>▶ Shift-clic: prendre tout</green>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(MM.deserialize("<gray>Clic-droit: drop ce stack au sol</gray>")
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slot, display);
        }

        // Row 5 (controls)
        ItemStack fill = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 45; s < 54; s++) inv.setItem(s, fill);

        if (page > 0) {
            inv.setItem(SLOT_PREV, GUIUtil.item(Material.ARROW,
                    "<yellow>◀ Page précédente</yellow>"));
        }
        if (page + 1 < pages) {
            inv.setItem(SLOT_NEXT, GUIUtil.item(Material.ARROW,
                    "<yellow>Page suivante ▶</yellow>"));
        }

        inv.setItem(SLOT_INFO, GUIUtil.item(type.icon(),
                type.colorTag() + "<bold>" + type.display() + "</bold>",
                "<gray>Stack: <yellow>×" + spawner.stack + "</yellow></gray>",
                "<gray>Loot total: <yellow>" + totalItems + "</yellow>"
                        + " / " + SpawnerManager.MAX_STORAGE_PER_SPAWNER + "</gray>",
                "",
                "<dark_gray>Un tick " + SpawnerManager.TICK_PERIOD_SEC + "s = "
                        + spawner.stack + " roll(s)</dark_gray>"));

        inv.setItem(SLOT_PAGE_LABEL, GUIUtil.item(Material.PAPER,
                "<white><bold>Page " + (page + 1) + " / " + pages + "</bold></white>",
                "<gray>Types d'items: <yellow>" + snapshot.size() + "</yellow></gray>",
                "<gray>Stacks affichés: <yellow>" + expanded.size() + "</yellow></gray>"));

        if (spawner.mode == SpawnerMode.XP) {
            inv.setItem(SLOT_MODE, GUIUtil.item(Material.EXPERIENCE_BOTTLE,
                    "<aqua><bold>Mode: XP</bold></aqua>",
                    "",
                    "<gray>Les mobs spawnent sur le spawner</gray>",
                    "<gray>avec 1 HP et sans IA.</gray>",
                    "",
                    "<yellow>▶ Clic pour passer en mode Loot</yellow>"));
        } else {
            inv.setItem(SLOT_MODE, GUIUtil.item(Material.CHEST,
                    "<gold><bold>Mode: Loot</bold></gold>",
                    "",
                    "<gray>Les items sont stockés dans le spawner.</gray>",
                    "",
                    "<yellow>▶ Clic pour passer en mode XP</yellow>"));
        }

        inv.setItem(SLOT_DROP_PAGE, GUIUtil.item(Material.HOPPER,
                "<gold><bold>Drop la page</bold></gold>",
                "",
                "<gray>Éjecte au sol tous les items</gray>",
                "<gray>de la page actuelle.</gray>",
                "",
                "<yellow>▶ Clic pour drop</yellow>"));

        inv.setItem(SLOT_DROP_ALL, GUIUtil.item(Material.TNT,
                "<red><bold>Drop tout</bold></red>",
                "",
                "<gray>Éjecte au sol <red>toute</red> la loot</gray>",
                "<gray>stockée dans le spawner.</gray>",
                "",
                "<red>▶ Clic pour tout drop</red>"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Controls
        if (slot == SLOT_PREV) { page--; render(); return; }
        if (slot == SLOT_NEXT) { page++; render(); return; }
        if (slot == SLOT_DROP_PAGE) { dropPage(p); return; }
        if (slot == SLOT_DROP_ALL) { dropAll(p); return; }
        if (slot == SLOT_MODE) { toggleMode(p); return; }
        if (slot == SLOT_INFO || slot == SLOT_PAGE_LABEL) return;
        if (slot >= 45) return;

        // Loot slot (liste "expanded" en stacks physiques)
        List<Map.Entry<Material, Integer>> expanded = expandStacks(spawner.snapshot());
        int idx = page * PER_PAGE + slot;
        if (idx >= expanded.size()) return;
        Map.Entry<Material, Integer> entry = expanded.get(idx);
        Material mat = entry.getKey();
        int stackAmount = entry.getValue();
        int totalMat = spawner.loot.getOrDefault(mat, 0);
        ClickType click = event.getClick();
        if (click == ClickType.SHIFT_LEFT) {
            giveStack(p, mat, totalMat);
        } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            dropStack(p, mat, click == ClickType.SHIFT_RIGHT ? totalMat : stackAmount);
        } else {
            giveStack(p, mat, stackAmount);
        }
        render();
    }

    private void giveStack(Player p, Material mat, int amount) {
        int given = 0;
        int remaining = amount;
        while (remaining > 0) {
            int take = Math.min(remaining, mat.getMaxStackSize());
            ItemStack s = new ItemStack(mat, take);
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(s);
            if (overflow.isEmpty()) {
                given += take;
                remaining -= take;
            } else {
                // inventaire plein — on remet ce qui n'a pas tenu
                int left = 0;
                for (ItemStack o : overflow.values()) left += o.getAmount();
                given += (take - left);
                remaining -= (take - left);
                break;
            }
        }
        if (given > 0) {
            spawner.loot.merge(mat, -given, Integer::sum);
            if (spawner.loot.get(mat) != null && spawner.loot.get(mat) <= 0) {
                spawner.loot.remove(mat);
            }
            spawner.markDirty();
        }
        if (remaining > 0) {
            p.sendMessage(Msg.info("<gray>Inventaire plein. <yellow>"
                    + remaining + "</yellow> restants dans le spawner.</gray>"));
        }
    }

    private void dropStack(Player p, Material mat, int amount) {
        int stored = spawner.loot.getOrDefault(mat, 0);
        int toDrop = Math.min(amount, stored);
        if (toDrop <= 0) return;
        int remaining = toDrop;
        while (remaining > 0) {
            int take = Math.min(remaining, mat.getMaxStackSize());
            throwFromPlayer(p, new ItemStack(mat, take));
            remaining -= take;
        }
        int left = stored - toDrop;
        if (left <= 0) spawner.loot.remove(mat);
        else spawner.loot.put(mat, left);
        spawner.markDirty();
    }

    /** Découpe chaque matériau en stacks physiques (64, 16, etc.) pour l'affichage. */
    private static List<Map.Entry<Material, Integer>> expandStacks(
            List<Map.Entry<Material, Integer>> snapshot) {
        List<Map.Entry<Material, Integer>> out = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : snapshot) {
            int remaining = e.getValue();
            int maxStack = Math.max(1, e.getKey().getMaxStackSize());
            while (remaining > 0) {
                int take = Math.min(remaining, maxStack);
                out.add(Map.entry(e.getKey(), take));
                remaining -= take;
            }
        }
        return out;
    }

    private void toggleMode(Player p) {
        if (spawner.mode == SpawnerMode.LOOT) {
            plugin.spawners().setMode(spawner, SpawnerMode.XP);
            p.sendMessage(Msg.ok("<aqua>Mode XP activé. Les mobs spawnent sur le spawner.</aqua>"));
        } else {
            int converted = plugin.spawners().setMode(spawner, SpawnerMode.LOOT);
            p.sendMessage(Msg.ok("<gold>Mode Loot activé. Les items sont stockés dans le spawner."
                    + (converted > 0 ? " <yellow>" + converted + "</yellow> mob(s) converti(s) en loot." : "")
                    + "</gold>"));
        }
        render();
    }

    private void dropPage(Player p) {
        List<Map.Entry<Material, Integer>> expanded = expandStacks(spawner.snapshot());
        int offset = page * PER_PAGE;
        int end = Math.min(offset + PER_PAGE, expanded.size());
        if (offset >= end) {
            p.sendMessage(Msg.info("<gray>Rien à drop sur cette page.</gray>"));
            return;
        }
        int dropped = 0;
        for (int i = offset; i < end; i++) {
            Map.Entry<Material, Integer> e = expanded.get(i);
            Material mat = e.getKey();
            int take = e.getValue();
            int stored = spawner.loot.getOrDefault(mat, 0);
            if (stored <= 0) continue;
            int actual = Math.min(take, stored);
            throwFromPlayer(p, new ItemStack(mat, actual));
            dropped += actual;
            int left = stored - actual;
            if (left <= 0) spawner.loot.remove(mat);
            else spawner.loot.put(mat, left);
        }
        spawner.markDirty();
        p.sendMessage(Msg.ok("<green>Page drop: <yellow>" + dropped + "</yellow> items.</green>"));
        render();
    }

    private void dropAll(Player p) {
        int total = 0;
        for (int v : spawner.loot.values()) total += v;
        if (total == 0) {
            p.sendMessage(Msg.info("<gray>Le spawner est vide.</gray>"));
            return;
        }
        for (Map.Entry<Material, Integer> e : new ArrayList<>(spawner.loot.entrySet())) {
            int remaining = e.getValue();
            while (remaining > 0) {
                int take = Math.min(remaining, e.getKey().getMaxStackSize());
                throwFromPlayer(p, new ItemStack(e.getKey(), take));
                remaining -= take;
            }
        }
        spawner.loot.clear();
        spawner.markDirty();
        p.sendMessage(Msg.ok("<green>Tout drop: <yellow>" + total + "</yellow> items.</green>"));
        render();
    }

    /** Jette un item devant le joueur, dans la direction où il regarde. */
    private void throwFromPlayer(Player p, ItemStack stack) {
        Location eye = p.getEyeLocation();
        Item item = p.getWorld().dropItem(eye, stack);
        Vector dir = eye.getDirection().normalize().multiply(0.4);
        item.setVelocity(dir);
        item.setPickupDelay(10);
        item.setThrower(p.getUniqueId());
    }

    private static String prettyName(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        StringBuilder b = new StringBuilder();
        boolean cap = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') { cap = true; b.append(c); continue; }
            b.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return b.toString();
    }
}
