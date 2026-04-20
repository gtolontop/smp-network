package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/** Direct-edit worldborder GUI: click bumps size; shift-click reduces. Chat prompt for exact size/center. */
public class WorldBorderGUI extends GUIHolder {

    private enum View { LIST, EDIT }

    private final SMPCore plugin;
    private final Map<Integer, String> slotWorld = new HashMap<>();
    private View view = View.LIST;
    private String editingWorld = null;

    public WorldBorderGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        view = View.LIST;
        editingWorld = null;
        Inventory inv = Bukkit.createInventory(this, 27,
                GUIUtil.title("<gradient:#89f7fe:#66a6ff><bold>WorldBorder</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        slotWorld.clear();
        int slot = 10;
        for (World w : Bukkit.getWorlds()) {
            if (slot > 16) break;
            Material icon = switch (w.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            inv.setItem(slot, GUIUtil.item(icon,
                    "<aqua><bold>" + w.getName() + "</bold></aqua>",
                    "",
                    "<gray>Taille: <white>" + (int) w.getWorldBorder().getSize() + "</white></gray>",
                    "<gray>Centre: <white>" +
                            (int) w.getWorldBorder().getCenter().getX() + ", " +
                            (int) w.getWorldBorder().getCenter().getZ() + "</white></gray>",
                    "",
                    "<green>▶ Clic pour éditer</green>"));
            slotWorld.put(slot, w.getName());
            slot++;
        }

        this.inventory = inv;
        p.openInventory(inv);
    }

    private void openEdit(Player p, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) { open(p); return; }
        view = View.EDIT;
        editingWorld = worldName;
        int size = (int) w.getWorldBorder().getSize();
        Inventory inv = Bukkit.createInventory(this, 27,
                GUIUtil.title("<aqua><bold>Éditer " + worldName + "</bold></aqua>"));
        GUIUtil.fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        inv.setItem(10, GUIUtil.item(Material.REDSTONE_BLOCK,
                "<red><bold>-1000</bold></red>",
                "<gray>Réduire la taille.</gray>"));
        inv.setItem(11, GUIUtil.item(Material.RED_CONCRETE,
                "<red><bold>-100</bold></red>",
                "<gray>Réduire un peu.</gray>"));
        inv.setItem(13, GUIUtil.item(Material.COMPASS,
                "<yellow><bold>Taille actuelle</bold></yellow>",
                "<white>" + size + "</white>",
                "",
                "<yellow>▶ Clic: Tape une taille</yellow>"));
        inv.setItem(15, GUIUtil.item(Material.LIME_CONCRETE,
                "<green><bold>+100</bold></green>",
                "<gray>Agrandir un peu.</gray>"));
        inv.setItem(16, GUIUtil.item(Material.EMERALD_BLOCK,
                "<green><bold>+1000</bold></green>",
                "<gray>Agrandir.</gray>"));

        inv.setItem(22, GUIUtil.item(Material.ARROW, "<yellow>◀ Retour</yellow>"));
        this.inventory = inv;
        p.openInventory(inv);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        if (view == View.LIST) {
            String name = slotWorld.get(raw);
            if (name == null) return;
            openEdit(p, name);
            return;
        }
        if (raw == 22) { open(p); return; }
        World w = Bukkit.getWorld(editingWorld);
        if (w == null) { open(p); return; }
        var border = w.getWorldBorder();
        double curr = border.getSize();
        double next = curr;
        switch (raw) {
            case 10 -> next = Math.max(1, curr - 1000);
            case 11 -> next = Math.max(1, curr - 100);
            case 15 -> next = curr + 100;
            case 16 -> next = curr + 1000;
            case 13 -> {
                p.closeInventory();
                plugin.chatPrompt().ask(p, "<aqua>Tape la nouvelle taille pour " + editingWorld + " :</aqua>", 30, txt -> {
                    double v;
                    try { v = Double.parseDouble(txt); } catch (NumberFormatException e) {
                        p.sendMessage(Msg.err("Nombre invalide.")); return;
                    }
                    if (v <= 0) { p.sendMessage(Msg.err("Taille > 0 requise.")); return; }
                    border.setSize(v);
                    p.sendMessage(Msg.ok("<green>Taille mise à <yellow>" + (int) v + "</yellow>.</green>"));
                    openEdit(p, editingWorld);
                });
                return;
            }
            default -> { return; }
        }
        border.setSize(next);
        p.sendMessage(Msg.ok("<green>Taille: <yellow>" + (int) next + "</yellow>.</green>"));
        openEdit(p, editingWorld);
    }
}
