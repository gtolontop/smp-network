package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.LeaderboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 17;

    private static final int SLOT_INFO = 0;
    private static final int SLOT_HELP = 8;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_HIGHLIGHT = 46;
    private static final int SLOT_SOLO = 47;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_TEAM = 51;
    private static final int SLOT_SUMMARY = 52;
    private static final int SLOT_NEXT = 53;

    private static final int[] TAB_SLOTS = {1, 2, 3, 4, 5};
    private static final LeaderboardManager.Category[] TABS = {
            LeaderboardManager.Category.MONEY,
            LeaderboardManager.Category.PLAYTIME,
            LeaderboardManager.Category.KILLS,
            LeaderboardManager.Category.DEATHS,
            LeaderboardManager.Category.DISTANCE
    };
    private static final int[] PODIUM_SLOTS = {13, 11, 15};
    private static final int[] LIST_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final SMPCore plugin;
    private LeaderboardManager.Category category = LeaderboardManager.Category.MONEY;
    private LeaderboardManager.Scope scope = LeaderboardManager.Scope.SOLO;
    private int page = 0;

    public LeaderboardGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        open(viewer, LeaderboardManager.Category.MONEY, LeaderboardManager.Scope.SOLO, 0);
    }

    public void open(Player viewer, LeaderboardManager.Category category, LeaderboardManager.Scope scope, int page) {
        this.category = category == null ? LeaderboardManager.Category.MONEY : category;
        this.scope = scope == null ? LeaderboardManager.Scope.SOLO : scope;
        this.page = Math.max(0, page);
        render(viewer);
    }

    private void render(Player viewer) {
        LeaderboardManager.Result result = plugin.leaderboards().ranking(category, scope, viewer.getUniqueId());
        List<LeaderboardManager.Entry> entries = result.entries();
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(this, 54, GUIUtil.title(category.titleMiniMessage()));
        GUIUtil.fillBorder(inv, category.border());

        inv.setItem(SLOT_INFO, GUIUtil.item(Material.BOOK,
                "<white><bold>Leaderboard</bold></white>",
                "<gray>Catégorie: <white>" + category.display() + "</white></gray>",
                "<gray>Mode: <white>" + scope.display() + "</white></gray>",
                "<gray>Commande: <white>/leaderboard</white></gray>"));
        inv.setItem(SLOT_HELP, GUIUtil.item(Material.COMPASS,
                "<yellow><bold>Navigation rapide</bold></yellow>",
                "<gray>Haut: change de classement</gray>",
                "<gray>Bas: swap solo / team</gray>",
                "<gray>Flèches: change de page</gray>"));

        for (int i = 0; i < TABS.length; i++) {
            LeaderboardManager.Category tab = TABS[i];
            boolean active = tab == category;
            inv.setItem(TAB_SLOTS[i], GUIUtil.item(tab.icon(),
                    (active ? "<yellow><bold>" : "<gray>") + tab.display() + (active ? "</bold></yellow>" : "</gray>"),
                    active ? "<gold>Tu es déjà ici.</gold>" : "<yellow>▶ Clic pour ouvrir</yellow>"));
        }

        int start = page * PER_PAGE;
        int onPage = Math.min(PER_PAGE, Math.max(0, entries.size() - start));

        for (int i = 0; i < Math.min(3, onPage); i++) {
            inv.setItem(PODIUM_SLOTS[i], renderEntry(entries.get(start + i), start + i + 1, true));
        }
        for (int i = 3; i < onPage; i++) {
            inv.setItem(LIST_SLOTS[i - 3], renderEntry(entries.get(start + i), start + i + 1, false));
        }

        if (entries.isEmpty()) {
            inv.setItem(31, GUIUtil.item(Material.BARRIER,
                    "<red>Aucune donnée</red>",
                    "<gray>Le classement est vide pour le moment.</gray>"));
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, GUIUtil.item(Material.ARROW, "<yellow>◀ Page précédente</yellow>"));
        }
        if (page + 1 < totalPages) {
            inv.setItem(SLOT_NEXT, GUIUtil.item(Material.ARROW, "<yellow>Page suivante ▶</yellow>"));
        }

        inv.setItem(SLOT_SOLO, GUIUtil.item(LeaderboardManager.Scope.SOLO.icon(),
                scope == LeaderboardManager.Scope.SOLO ? "<green><bold>Solo</bold></green>" : "<gray>Solo</gray>",
                "<gray>Voir les joueurs individuellement.</gray>"));
        inv.setItem(SLOT_TEAM, GUIUtil.item(LeaderboardManager.Scope.TEAM.icon(),
                scope == LeaderboardManager.Scope.TEAM ? "<aqua><bold>Team</bold></aqua>" : "<gray>Team</gray>",
                "<gray>Voir les équipes agrégées.</gray>"));
        inv.setItem(SLOT_PAGE, GUIUtil.item(Material.PAPER,
                "<white><bold>Page " + (page + 1) + " / " + totalPages + "</bold></white>",
                "<gray>Entrées: <white>" + entries.size() + "</white></gray>"));
        inv.setItem(SLOT_SUMMARY, GUIUtil.item(category.icon(),
                "<white><bold>" + category.display() + "</bold></white>",
                summaryLine(),
                scope == LeaderboardManager.Scope.TEAM
                        ? "<gray>Astuce: clique sur <white>Solo</white> pour comparer joueur par joueur.</gray>"
                        : "<gray>Astuce: clique sur <white>Team</white> pour voir le total d'équipe.</gray>"));

        if (result.highlight() != null) {
            String title = scope == LeaderboardManager.Scope.TEAM
                    ? "<aqua><bold>Rang de ta team</bold></aqua>"
                    : "<gold><bold>Ton rang</bold></gold>";
            inv.setItem(SLOT_HIGHLIGHT, GUIUtil.item(Material.NETHER_STAR,
                    title,
                    "<gray>Position: <white>#" + result.highlight().rank() + "</white></gray>",
                    "<gray>Valeur: " + result.highlight().valueDisplay() + "</gray>"));
        } else if (scope == LeaderboardManager.Scope.TEAM) {
            inv.setItem(SLOT_HIGHLIGHT, GUIUtil.item(Material.BARRIER,
                    "<gray>Pas de team</gray>",
                    "<gray>Rejoins une team pour voir ton rang.</gray>"));
        } else {
            inv.setItem(SLOT_HIGHLIGHT, GUIUtil.item(Material.BARRIER,
                    "<gray>Hors classement</gray>",
                    "<gray>Aucune donnée perso trouvée.</gray>"));
        }

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private ItemStack renderEntry(LeaderboardManager.Entry entry, int rank, boolean podium) {
        ItemStack item = baseIcon(entry);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String rankColor = switch (rank) {
            case 1 -> "<gold>";
            case 2 -> "<white>";
            case 3 -> "<#cd7f32>";
            default -> "<gray>";
        };

        meta.displayName(MM.deserialize("<!italic>" + rankColor
                + (podium ? "<bold>#" + rank + "</bold>" : "#" + rank)
                + " <reset>" + entry.displayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic>" + entry.valueDisplay()));
        if (!entry.detailLines().isEmpty()) {
            lore.add(MM.deserialize("<!italic> "));
            for (String line : entry.detailLines()) {
                lore.add(MM.deserialize("<!italic>" + line));
            }
        }
        if (podium) {
            lore.add(MM.deserialize("<!italic> "));
            lore.add(MM.deserialize("<!italic><yellow><bold>Podium</bold></yellow>"));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack baseIcon(LeaderboardManager.Entry entry) {
        ItemStack item = new ItemStack(entry.iconMaterial());
        if (entry.iconMaterial() == Material.PLAYER_HEAD && entry.iconPlayer() != null && item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(entry.iconPlayer()));
            item.setItemMeta(skull);
        }
        return item;
    }

    private String summaryLine() {
        return switch (category) {
            case MONEY -> scope == LeaderboardManager.Scope.TEAM
                    ? "<gray>Total team = <white>fortune des joueurs</white> + <white>banque</white>.</gray>"
                    : "<gray>Classement des joueurs les plus riches.</gray>";
            case PLAYTIME -> "<gray>Temps total cumulé sur le réseau.</gray>";
            case KILLS -> "<gray>Plus gros chasseurs du serveur.</gray>";
            case DEATHS -> "<gray>Les morts comptent aussi.</gray>";
            case DISTANCE -> "<gray>Lecture des stats vanilla de déplacement.</gray>";
        };
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;

        if (raw == SLOT_PREV) { open(player, category, scope, page - 1); return; }
        if (raw == SLOT_NEXT) { open(player, category, scope, page + 1); return; }
        if (raw == SLOT_SOLO && scope != LeaderboardManager.Scope.SOLO) {
            open(player, category, LeaderboardManager.Scope.SOLO, 0);
            return;
        }
        if (raw == SLOT_TEAM && scope != LeaderboardManager.Scope.TEAM) {
            open(player, category, LeaderboardManager.Scope.TEAM, 0);
            return;
        }

        for (int i = 0; i < TAB_SLOTS.length; i++) {
            if (raw == TAB_SLOTS[i] && category != TABS[i]) {
                open(player, TABS[i], scope, 0);
                return;
            }
        }
    }
}
