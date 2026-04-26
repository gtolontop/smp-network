package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.ModerationManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BanlistGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 28;

    private final SMPCore plugin;
    private final Map<Integer, UUID> slotTarget = new HashMap<>();
    private int page = 0;

    public BanlistGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, int page) {
        this.page = Math.max(0, page);
        List<ModerationManager.Ban> all = plugin.moderation().listBans();
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PER_PAGE));
        if (this.page >= totalPages) this.page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f85032:#e73827><bold>\u26D4 Banlist</bold></gradient> <gray>(" + all.size() + ")</gray>"));
        GUIUtil.fillBorder(inv, Material.RED_STAINED_GLASS_PANE);
        slotTarget.clear();

        int[] inner = innerSlots();
        int offset = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = offset + i;
            if (idx >= all.size()) break;
            inv.setItem(inner[i], render(all.get(idx)));
            slotTarget.put(inner[i], all.get(idx).uuid());
        }

        if (all.isEmpty()) {
            inv.setItem(22, GUIUtil.item(Material.BARRIER,
                    "<green><bold>Aucun ban actif</bold></green>",
                    "<gray>Aucun joueur n'est banni.</gray>"));
        }

        if (this.page > 0) {
            inv.setItem(45, GUIUtil.item(Material.ARROW, "<yellow>\u25C0 Page pr\u00e9c\u00e9dente</yellow>"));
        }
        if ((this.page + 1) * PER_PAGE < all.size()) {
            inv.setItem(53, GUIUtil.item(Material.ARROW, "<yellow>Page suivante \u25B6</yellow>"));
        }

        inv.setItem(49, GUIUtil.item(Material.PAPER,
                "<white><bold>Page " + (this.page + 1) + "/" + totalPages + "</bold></white>",
                "<gray>" + all.size() + " ban(s) actif(s)</gray>"));

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private int[] innerSlots() {
        int[] inner = new int[PER_PAGE];
        int idx = 0;
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c < 8; c++) inner[idx++] = r * 9 + c;
        }
        return inner;
    }

    private ItemStack render(ModerationManager.Ban b) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(b.uuid()));
        meta.displayName(MM.deserialize("<!italic><red><bold>" + b.name() + "</bold></red>"));

        String duration = b.permanent()
                ? "<dark_red>Permanent</dark_red>"
                : "<yellow>" + formatExpiry(b.expiresAt()) + "</yellow>";

        String remaining = "";
        if (!b.permanent()) {
            long left = b.expiresAt() - System.currentTimeMillis() / 1000L;
            if (left > 0) remaining = "<gray>Restant: <white>" + Msg.duration(left) + "</white></gray>";
            else remaining = "<gray>Expir\u00e9</gray>";
        }

        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic><gray>Motif: <white>" + (b.reason() != null ? b.reason() : "Non sp\u00e9cifi\u00e9") + "</white></gray>"));
        lore.add(MM.deserialize("<!italic><gray>Dur\u00e9e: " + duration + "</gray>"));
        if (!remaining.isEmpty()) lore.add(MM.deserialize("<!italic>" + remaining));
        lore.add(MM.deserialize("<!italic><gray>Par: <aqua>" + b.issuer() + "</aqua></gray>"));
        lore.add(MM.deserialize("<!italic><gray>Le: <white>" + formatDate(b.issuedAt()) + "</white></gray>"));
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic><green>▶ Clic: /unban " + b.name() + "</green>"));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private String formatDate(long epochSec) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(epochSec * 1000L));
    }

    private String formatExpiry(long epochSec) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(epochSec * 1000L));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();

        if (slot == 45) { open(p, page - 1); return; }
        if (slot == 53) { open(p, page + 1); return; }

        UUID target = slotTarget.get(slot);
        if (target == null) return;
        p.closeInventory();
        Bukkit.dispatchCommand(p, "unban " + Bukkit.getOfflinePlayer(target).getName());
    }
}
