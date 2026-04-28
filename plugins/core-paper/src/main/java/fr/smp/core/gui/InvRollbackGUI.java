package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.sync.InventoryHistoryManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * GUI admin pour /invrollback — trois vues imbriquées :
 *   LIST    : liste paginée des snapshots d'un joueur (28 par page)
 *   PEEK    : aperçu read-only de l'inventaire + ender chest d'un snapshot
 *   CONFIRM : confirmation avant d'appliquer un rollback
 */
public final class InvRollbackGUI {

    private static final int PER_PAGE = 28;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT);

    private InvRollbackGUI() {}

    // ------------------------------------------------------------------ //
    //  Entrée publique — async load → main thread open                    //
    // ------------------------------------------------------------------ //

    public static void open(SMPCore plugin, Player admin, UUID targetUuid, String targetName) {
        if (plugin.invHistory() == null || !plugin.invHistory().isEnabled()) {
            admin.sendMessage(Msg.err("Historique d'inventaires désactivé sur ce serveur."));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<InventoryHistoryManager.Entry> entries =
                    plugin.invHistory().list(targetUuid, 500);
            Bukkit.getScheduler().runTask(plugin, () ->
                    new ListHolder(plugin, targetUuid, targetName, entries).open(admin, 0));
        });
    }

    // ================================================================== //
    //  VUE 1 — LISTE                                                      //
    // ================================================================== //

    public static final class ListHolder extends GUIHolder {

        private final SMPCore plugin;
        private final UUID targetUuid;
        private final String targetName;
        private final List<InventoryHistoryManager.Entry> entries;
        private int page;

        ListHolder(SMPCore plugin, UUID targetUuid, String targetName,
                   List<InventoryHistoryManager.Entry> entries) {
            this.plugin = plugin;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.entries = entries;
        }

        void open(Player admin, int newPage) {
            this.page = Math.max(0, newPage);
            int total = entries.size();
            int totalPages = Math.max(1, (int) Math.ceil(total / (double) PER_PAGE));
            if (this.page >= totalPages) this.page = totalPages - 1;

            String title = "<gradient:#67e8f9:#a78bfa><bold>Rollback</bold></gradient>"
                    + " <dark_gray>»</dark_gray> <white>" + targetName + "</white>"
                    + " <dark_gray>(" + total + ")</dark_gray>";
            Inventory inv = Bukkit.createInventory(this, 54, GUIUtil.title(title));
            GUIUtil.fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);

            int[] inner = innerSlots();
            int offset = this.page * PER_PAGE;
            for (int i = 0; i < PER_PAGE; i++) {
                int idx = offset + i;
                if (idx >= total) break;
                inv.setItem(inner[i], renderEntry(entries.get(idx), idx));
            }

            if (total == 0) {
                inv.setItem(22, GUIUtil.item(Material.BARRIER,
                        "<red>Aucun snapshot</red>",
                        "<gray>Ce joueur n'a aucun snapshot enregistré.</gray>"));
            }

            // Navigation bas
            if (this.page > 0)
                inv.setItem(45, GUIUtil.item(Material.ARROW, "<yellow>◀ Page précédente</yellow>",
                        "<dark_gray>Page " + this.page + "/" + totalPages + "</dark_gray>"));
            if ((this.page + 1) * PER_PAGE < total)
                inv.setItem(53, GUIUtil.item(Material.ARROW, "<yellow>Page suivante ▶</yellow>",
                        "<dark_gray>Page " + (this.page + 2) + "/" + totalPages + "</dark_gray>"));

            inv.setItem(47, GUIUtil.item(Material.RECOVERY_COMPASS,
                    "<gold><bold>Snapshot maintenant</bold></gold>",
                    "<gray>Snapshot manuel immédiat</gray>",
                    "<gray>(joueur doit être en ligne)</gray>"));

            inv.setItem(49, GUIUtil.item(Material.PLAYER_HEAD,
                    "<white><bold>" + targetName + "</bold></white>",
                    "<gray>Snapshots: <white>" + total + "</white></gray>",
                    "<gray>Page <white>" + (this.page + 1) + "</white>/<white>" + totalPages + "</white></gray>",
                    "",
                    "<dark_gray>[Gauche] aperçu</dark_gray>",
                    "<dark_gray>[Droite] appliquer rollback</dark_gray>"));

            this.inventory = inv;
            admin.openInventory(inv);
        }

        private static int[] innerSlots() {
            int[] s = new int[PER_PAGE];
            int k = 0;
            for (int r = 1; r <= 4; r++)
                for (int c = 1; c < 8; c++) s[k++] = r * 9 + c;
            return s;
        }

        private static ItemStack renderEntry(InventoryHistoryManager.Entry e, int idx) {
            Material mat = sourceMat(e.source());
            String date = FMT.format(new Date(e.createdAt()));
            String age  = formatAge(System.currentTimeMillis() - e.createdAt());
            String col  = sourceColor(e.source());
            return GUIUtil.item(mat,
                    "<white>#" + idx + "</white>  <yellow>" + date + "</yellow>",
                    "<dark_gray>il y a " + age + "</dark_gray>",
                    "",
                    "<gray>Source:  " + col + e.source() + "</gray>",
                    "<gray>Serveur: <white>" + e.server() + "</white></gray>",
                    "",
                    "<dark_gray>[Gauche]  <aqua>Aperçu inventaire</aqua></dark_gray>",
                    "<dark_gray>[Droite]  <red>Appliquer rollback</red></dark_gray>");
        }

        @Override
        public void onClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            int slot = event.getRawSlot();

            if (slot == 45) { open(admin, page - 1); return; }
            if (slot == 53) { open(admin, page + 1); return; }
            if (slot == 47) { handleSnap(admin); return; }

            int[] inner = innerSlots();
            for (int i = 0; i < inner.length; i++) {
                if (inner[i] != slot) continue;
                int idx = page * PER_PAGE + i;
                if (idx >= entries.size()) return;
                InventoryHistoryManager.Entry e = entries.get(idx);
                ClickType ct = event.getClick();
                if (ct == ClickType.RIGHT || ct == ClickType.SHIFT_RIGHT) {
                    new ConfirmHolder(plugin, this, e).open(admin);
                } else {
                    openPeek(admin, e, false);
                }
                return;
            }
        }

        void openPeek(Player admin, InventoryHistoryManager.Entry e, boolean showEnder) {
            admin.closeInventory();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                YamlConfiguration yaml = plugin.invHistory().load(e.id());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (yaml == null) {
                        admin.sendMessage(Msg.err("Snapshot illisible (id=" + e.id() + ")."));
                        return;
                    }
                    new PeekHolder(plugin, this, e, yaml, showEnder).open(admin);
                });
            });
        }

        private void handleSnap(Player admin) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target == null) {
                admin.sendMessage(Msg.err("Le joueur n'est pas en ligne sur ce serveur."));
                return;
            }
            plugin.invHistory().snapshotManual(target, admin.getName());
            admin.sendMessage(Msg.ok("<green>Snapshot manuel de <yellow>" + targetName + "</yellow> pris.</green>"));
            admin.closeInventory();
            // Rouvre le GUI avec la liste fraîche
            InvRollbackGUI.open(plugin, admin, targetUuid, targetName);
        }
    }

    // ================================================================== //
    //  VUE 2 — APERÇU INVENTAIRE (read-only, layout identique à InvseeGUI) //
    // ================================================================== //

    public static final class PeekHolder extends GUIHolder {

        private final SMPCore plugin;
        private final ListHolder parent;
        private final InventoryHistoryManager.Entry entry;
        private final YamlConfiguration yaml;
        private final boolean showEnder;

        PeekHolder(SMPCore plugin, ListHolder parent, InventoryHistoryManager.Entry entry,
                   YamlConfiguration yaml, boolean showEnder) {
            this.plugin = plugin;
            this.parent = parent;
            this.entry = entry;
            this.yaml  = yaml;
            this.showEnder = showEnder;
        }

        void open(Player admin) {
            String date   = FMT.format(new Date(entry.createdAt()));
            String age    = formatAge(System.currentTimeMillis() - entry.createdAt());
            String tab    = showEnder ? "Ender Chest" : "Inventaire";
            String title  = "<gradient:#67e8f9:#a78bfa><bold>Peek</bold></gradient>"
                    + " <dark_gray>» " + tab + " — </dark_gray>"
                    + "<gray>" + date + " (il y a " + age + ")</gray>";
            Inventory inv = Bukkit.createInventory(this, 54, GUIUtil.title(title));

            if (showEnder) buildEnder(inv);
            else           buildInventory(inv);

            // Ligne du bas — actions communes
            ItemStack filler = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);
            for (int s = 36; s <= 44; s++) {
                if (inv.getItem(s) == null || inv.getItem(s).getType().isAir())
                    inv.setItem(s, filler);
            }

            inv.setItem(36, GUIUtil.item(Material.ARROW,
                    "<yellow>◀ Retour à la liste</yellow>"));

            int xpLvl  = yaml.getInt("xp.level", 0);
            double hp  = yaml.getDouble("stats.health", 20.0);
            int food   = yaml.getInt("stats.food", 20);
            String gm  = yaml.getString("gamemode", "?");
            inv.setItem(40, GUIUtil.item(Material.BOOK,
                    "<white><bold>Statistiques</bold></white>",
                    "<gray>XP:   <green>" + xpLvl + " niveaux</green></gray>",
                    "<gray>HP:   <red>" + String.format("%.1f", hp) + " / 20 ❤</red></gray>",
                    "<gray>Food: <yellow>" + food + " / 20 🍗</yellow></gray>",
                    "<gray>Mode: <white>" + gm + "</white></gray>",
                    "<gray>Source: <white>" + entry.source() + "</white></gray>",
                    "<gray>Serveur: <white>" + entry.server() + "</white></gray>"));

            // Toggle ender / inventaire
            if (showEnder) {
                inv.setItem(42, GUIUtil.item(Material.CHEST,
                        "<aqua>Voir Inventaire</aqua>",
                        "<dark_gray>Clic pour basculer</dark_gray>"));
            } else {
                inv.setItem(42, GUIUtil.item(Material.ENDER_CHEST,
                        "<aqua>Voir Ender Chest</aqua>",
                        "<dark_gray>Clic pour basculer</dark_gray>"));
            }

            inv.setItem(44, GUIUtil.item(Material.LIME_TERRACOTTA,
                    "<green><bold>↩ Appliquer ce rollback</bold></green>",
                    "<gray>Restaure cet inventaire</gray>",
                    "<gray>au joueur cible.</gray>",
                    "",
                    "<dark_gray>id=" + entry.id() + " — " + date + "</dark_gray>"));

            this.inventory = inv;
            admin.openInventory(inv);
        }

        private void buildInventory(Inventory inv) {
            ItemStack dark = GUIUtil.filler(Material.BLACK_STAINED_GLASS_PANE);
            ItemStack gray = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);

            // Ligne 0 : armure + offhand (même disposition qu'InvseeGUI)
            inv.setItem(0, gray);
            placeArmor(inv, 1, 3, "Casque");
            placeArmor(inv, 2, 2, "Plastron");
            placeArmor(inv, 3, 1, "Jambières");
            placeArmor(inv, 4, 0, "Bottes");
            inv.setItem(5, gray);
            placeOffhand(inv, 6);
            inv.setItem(7, gray);
            inv.setItem(8, gray);

            // Lignes 1-3 : inventaire principal (indices 9-35)
            List<ItemStack> contents = getList("inventory.contents");
            for (int i = 9; i <= 35; i++) {
                ItemStack it = i < contents.size() ? contents.get(i) : null;
                inv.setItem(i, nonEmpty(it) ? it.clone() : null);
            }

            // Ligne 4 : séparateur (géré après buildX)

            // Ligne 5 : hotbar (indices 0-8)
            for (int i = 0; i <= 8; i++) {
                ItemStack it = i < contents.size() ? contents.get(i) : null;
                inv.setItem(45 + i, nonEmpty(it) ? it.clone() : null);
            }
        }

        private void buildEnder(Inventory inv) {
            ItemStack gray = GUIUtil.filler(Material.GRAY_STAINED_GLASS_PANE);

            // Lignes 0-2 : ender chest (27 slots)
            List<ItemStack> ec = getList("enderchest");
            for (int i = 0; i < 27; i++) {
                ItemStack it = i < ec.size() ? ec.get(i) : null;
                inv.setItem(i, nonEmpty(it) ? it.clone() : null);
            }

            // Lignes 3-5 : filler (la ligne d'actions est injectée après)
            for (int i = 27; i < 54; i++) inv.setItem(i, gray);
        }

        private void placeArmor(Inventory inv, int guiSlot, int armorIdx, String label) {
            List<ItemStack> armor = getList("inventory.armor");
            ItemStack it = armorIdx < armor.size() ? armor.get(armorIdx) : null;
            if (nonEmpty(it)) inv.setItem(guiSlot, it.clone());
            else inv.setItem(guiSlot, GUIUtil.item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>" + label));
        }

        private void placeOffhand(Inventory inv, int guiSlot) {
            ItemStack oh = null;
            Object raw = yaml.get("inventory.offhand");
            if (raw instanceof ItemStack it) oh = it;
            if (nonEmpty(oh)) inv.setItem(guiSlot, oh.clone());
            else inv.setItem(guiSlot, GUIUtil.item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>Main secondaire"));
        }

        @SuppressWarnings("unchecked")
        private List<ItemStack> getList(String key) {
            List<?> raw = yaml.getList(key);
            if (raw == null) return List.of();
            return (List<ItemStack>) raw;
        }

        @Override
        public void onClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            int slot = event.getRawSlot();
            if (slot == 36) {
                parent.open(admin, parent.page);
            } else if (slot == 42) {
                new PeekHolder(plugin, parent, entry, yaml, !showEnder).open(admin);
            } else if (slot == 44) {
                new ConfirmHolder(plugin, parent, entry).open(admin);
            }
        }
    }

    // ================================================================== //
    //  VUE 3 — CONFIRMATION ROLLBACK                                      //
    // ================================================================== //

    public static final class ConfirmHolder extends GUIHolder {

        private final SMPCore plugin;
        private final ListHolder parent;
        private final InventoryHistoryManager.Entry entry;

        ConfirmHolder(SMPCore plugin, ListHolder parent, InventoryHistoryManager.Entry entry) {
            this.plugin = plugin;
            this.parent = parent;
            this.entry  = entry;
        }

        void open(Player admin) {
            String date = FMT.format(new Date(entry.createdAt()));
            String age  = formatAge(System.currentTimeMillis() - entry.createdAt());
            Inventory inv = Bukkit.createInventory(this, 27,
                    GUIUtil.title("<red><bold>⚠ Confirmer le rollback ?</bold></red>"));

            GUIUtil.fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

            inv.setItem(11, GUIUtil.item(Material.RED_WOOL,
                    "<red><bold>✗ Annuler</bold></red>",
                    "<gray>Retour à la liste.</gray>"));

            inv.setItem(13, GUIUtil.item(Material.PAPER,
                    "<white><bold>Snapshot #" + entry.id() + "</bold></white>",
                    "",
                    "<gray>Joueur:  <white>" + parent.targetName + "</white></gray>",
                    "<gray>Date:    <white>" + date + "</white></gray>",
                    "<gray>Âge:     <yellow>il y a " + age + "</yellow></gray>",
                    "<gray>Source:  <white>" + entry.source() + "</white></gray>",
                    "<gray>Serveur: <white>" + entry.server() + "</white></gray>",
                    "",
                    "<dark_red>⚠ Cette action est réversible :</dark_red>",
                    "<dark_gray>un snapshot PREAPPLY est pris avant.</dark_gray>"));

            inv.setItem(15, GUIUtil.item(Material.LIME_WOOL,
                    "<green><bold>✓ Confirmer</bold></green>",
                    "<gray>Applique ce snapshot à <white>" + parent.targetName + "</white>.</gray>",
                    "<gray>Le joueur doit être en ligne.</gray>"));

            this.inventory = inv;
            admin.openInventory(inv);
        }

        @Override
        public void onClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            int slot = event.getRawSlot();

            if (slot == 11) {
                parent.open(admin, parent.page);
                return;
            }
            if (slot == 15) {
                applyRollback(admin);
            }
        }

        private void applyRollback(Player admin) {
            Player target = Bukkit.getPlayer(parent.targetUuid);
            if (target == null) {
                admin.closeInventory();
                admin.sendMessage(Msg.err("Le joueur <white>" + parent.targetName
                        + "</white> n'est pas en ligne sur ce serveur."));
                return;
            }
            boolean ok = plugin.invHistory().applyTo(entry.id(), target, admin.getName());
            admin.closeInventory();
            if (ok) {
                admin.sendMessage(Msg.ok("<green>Rollback appliqué à <yellow>" + target.getName()
                        + "</yellow> (snapshot id=" + entry.id() + ", " + FMT.format(new Date(entry.createdAt()))
                        + "). Snapshot PREAPPLY sauvegardé.</green>"));
                target.sendMessage(Msg.info("<yellow>Ton inventaire a été restauré par un administrateur.</yellow>"));
            } else {
                admin.sendMessage(Msg.err("Échec du rollback (snapshot illisible, corrompu, ou erreur DB)."));
            }
        }
    }

    // ================================================================== //
    //  Helpers partagés                                                    //
    // ================================================================== //

    private static boolean nonEmpty(ItemStack it) {
        return it != null && !it.getType().isAir();
    }

    private static Material sourceMat(String source) {
        return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
            case "periodic"  -> Material.PAPER;
            case "quit"      -> Material.RED_BED;
            case "manual"    -> Material.GOLDEN_CARROT;
            case "preapply"  -> Material.RECOVERY_COMPASS;
            case "shutdown"  -> Material.OAK_DOOR;
            default          -> Material.MAP;
        };
    }

    private static String sourceColor(String source) {
        return switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
            case "periodic" -> "<gray>";
            case "quit"     -> "<yellow>";
            case "manual"   -> "<gold>";
            case "preapply" -> "<aqua>";
            case "shutdown" -> "<red>";
            default         -> "<white>";
        };
    }

    static String formatAge(long ms) {
        if (ms < 0) ms = 0;
        long sec  = ms / 1000;
        long min  = sec  / 60;
        long h    = min  / 60;
        long days = h    / 24;
        if (days > 0)    return days + "j " + (h % 24) + "h";
        if (h > 0)       return h    + "h " + (min % 60) + "min";
        if (min > 0)     return min  + "min";
        return sec + "s";
    }
}
