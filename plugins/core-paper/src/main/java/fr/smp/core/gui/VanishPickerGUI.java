package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Picker simple : 6×9, liste les joueurs en ligne sur le serveur courant
 * (pas de cross-serveur), un clic = téléport instantané. Conçu pour les
 * besoins du vanish staff donc affiche TOUS les joueurs y compris les
 * autres vanishés.
 */
public class VanishPickerGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_NEXT = 53;

    private final SMPCore plugin;
    private final Map<Integer, UUID> slotMap = new HashMap<>();
    private int page = 0;

    public VanishPickerGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!plugin.vanish().isVanished(p)) {
            p.sendMessage(Msg.err("Tu dois être en vanish."));
            return;
        }
        render(p);
        p.openInventory(this.inventory);
    }

    private void render(Player viewer) {
        if (this.inventory == null) {
            this.inventory = Bukkit.createInventory(this, 54,
                    GUIUtil.title("<gold><bold>Téléport vanish — joueurs</bold></gold>"));
        }
        slotMap.clear();
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        // exclure soi-même
        online.removeIf(o -> o.getUniqueId().equals(viewer.getUniqueId()));
        online.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        int pages = Math.max(1, (int) Math.ceil(online.size() / (double) PER_PAGE));
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;

        int offset = page * PER_PAGE;
        int end = Math.min(offset + PER_PAGE, online.size());
        for (int slot = 0; slot < PER_PAGE; slot++) {
            int idx = offset + slot;
            if (idx >= end) continue;
            Player target = online.get(idx);
            inventory.setItem(slot, head(target));
            slotMap.put(slot, target.getUniqueId());
        }

        ItemStack glass = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, glass);

        if (page > 0) inventory.setItem(SLOT_PREV, GUIUtil.item(Material.ARROW,
                "<yellow>◀ Page précédente</yellow>"));
        if (page + 1 < pages) inventory.setItem(SLOT_NEXT, GUIUtil.item(Material.ARROW,
                "<yellow>Page suivante ▶</yellow>"));

        inventory.setItem(SLOT_INFO, GUIUtil.item(Material.PAPER,
                "<white><bold>Page " + (page + 1) + " / " + pages + "</bold></white>",
                "<gray>Joueurs en ligne sur ce serveur: <yellow>" + online.size() + "</yellow></gray>"));

        inventory.setItem(SLOT_BACK, GUIUtil.item(Material.OAK_DOOR,
                "<gray><bold>Retour menu</bold></gray>",
                "",
                "<yellow>▶ Clic pour revenir</yellow>"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == SLOT_PREV) { page--; render(viewer); return; }
        if (slot == SLOT_NEXT) { page++; render(viewer); return; }
        if (slot == SLOT_BACK) {
            viewer.closeInventory();
            new VanishMenuGUI(plugin).open(viewer);
            return;
        }
        if (slot == SLOT_INFO || slot >= 45) return;

        UUID id = slotMap.get(slot);
        if (id == null) return;
        Player target = Bukkit.getPlayer(id);
        if (target == null || !target.isOnline()) {
            viewer.sendMessage(Msg.err("Joueur hors ligne."));
            render(viewer);
            return;
        }
        viewer.closeInventory();
        viewer.teleport(target.getLocation());
        viewer.sendMessage(Msg.ok("<gray>Téléporté vers <yellow>" + target.getName() + "</yellow>.</gray>"));
    }

    private ItemStack head(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(target);
        if (meta != null) {
            boolean targetVanished = plugin.vanish().isVanished(target);
            String prefix = targetVanished ? "<light_purple>[V] " : "";
            meta.displayName(MM.deserialize("<!italic><yellow>" + prefix + target.getName() + "</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            String world = target.getWorld().getName();
            int x = target.getLocation().getBlockX();
            int y = target.getLocation().getBlockY();
            int z = target.getLocation().getBlockZ();
            lore.add(MM.deserialize("<!italic><gray>Monde: <yellow>" + world + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MM.deserialize("<!italic><gray>Position: <yellow>" + x + " " + y + " " + z + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MM.deserialize("<!italic><gray>Mode: <yellow>" + target.getGameMode().name() + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            if (targetVanished) {
                lore.add(MM.deserialize("<!italic><light_purple>(En vanish — invisible aux joueurs)</light_purple>")
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><yellow>▶ Clic pour téléporter</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }
}
