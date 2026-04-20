package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.BountyManager;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.utils.Msg;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BountyGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 28;

    private final SMPCore plugin;
    private final Map<Integer, UUID> slotTarget = new HashMap<>();
    private int page = 0;

    public BountyGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, int page) {
        this.page = Math.max(0, page);
        List<BountyManager.Bounty> all = plugin.bounties().top(200);
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PER_PAGE));
        if (this.page >= totalPages) this.page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#f85032:#e73827><bold>\uD83D\uDCB0 Bounties</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.RED_STAINED_GLASS_PANE);
        slotTarget.clear();

        int[] inner = innerSlots();
        int offset = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = offset + i;
            if (idx >= all.size()) break;
            inv.setItem(inner[i], render(all.get(idx), idx + 1));
            slotTarget.put(inner[i], all.get(idx).target());
        }

        if (all.isEmpty()) {
            inv.setItem(22, GUIUtil.item(Material.PAPER, "<gray>Aucune bounty active.</gray>",
                    "<dark_gray>Clique sur l'émeraude pour en poser une.</dark_gray>"));
        }

        if (this.page > 0) {
            inv.setItem(45, GUIUtil.item(Material.ARROW, "<yellow>◀ Page précédente</yellow>"));
        }
        if ((this.page + 1) * PER_PAGE < all.size()) {
            inv.setItem(53, GUIUtil.item(Material.ARROW, "<yellow>Page suivante ▶</yellow>"));
        }

        double myBal = plugin.economy().balance(viewer.getUniqueId());
        inv.setItem(49, GUIUtil.item(Material.EMERALD,
                "<gradient:#a8ff78:#78ffd6><bold>Poser une bounty</bold></gradient>",
                "",
                "<gray>Solde: <yellow>$" + Msg.money(myBal) + "</yellow></gray>",
                "<gray>Min: <yellow>$" + Msg.money(plugin.bounties().minAmount()) + "</yellow></gray>",
                "<gray>Max: <yellow>$" + Msg.money(plugin.bounties().maxAmount()) + "</yellow></gray>",
                "",
                "<green>▶ Clic pour choisir une cible</green>"));

        BountyManager.Bounty mine = plugin.bounties().get(viewer.getUniqueId());
        if (mine != null) {
            List<String> selfLore = new ArrayList<>();
            selfLore.add("");
            selfLore.add("<gray>Montant: <gold>$" + Msg.money(mine.amount()) + "</gold></gray>");
            selfLore.add("<gray>Contributeurs: <white>" + mine.contributors() + "</white></gray>");
            List<BountyManager.Contribution> topMine = plugin.bounties().topContributors(viewer.getUniqueId(), 3);
            if (!topMine.isEmpty()) {
                selfLore.add("");
                selfLore.add("<dark_red><bold>Qui veut ta peau</bold></dark_red>");
                String[] medals = {"<gold>①", "<gray>②", "<#cd7f32>③"};
                for (int i = 0; i < topMine.size(); i++) {
                    BountyManager.Contribution co = topMine.get(i);
                    selfLore.add(medals[i] + " <white>" + co.issuerName() +
                            "</white> <dark_gray>→</dark_gray> <gold>$" + Msg.money(co.amount()) + "</gold>");
                }
            }
            selfLore.add("");
            selfLore.add("<dark_gray>Reste en vie...</dark_gray>");
            inv.setItem(4, GUIUtil.item(Material.SKELETON_SKULL,
                    "<red><bold>Prime sur ta tête</bold></red>",
                    selfLore.toArray(new String[0])));
        } else {
            inv.setItem(4, GUIUtil.item(Material.SHIELD,
                    "<green><bold>Pas de prime</bold></green>",
                    "",
                    "<gray>Personne ne te chasse.</gray>"));
        }

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

    private ItemStack render(BountyManager.Bounty b, int rank) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(b.target()));
        }
        String rankColor = switch (rank) {
            case 1 -> "<gold>";
            case 2 -> "<gray>";
            case 3 -> "<#cd7f32>";
            default -> "<white>";
        };
        meta.displayName(MM.deserialize("<!italic>" + rankColor + "<bold>#" + rank + " " + b.targetName() + "</bold>"));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic><gray>Prime: <gold><bold>$" + Msg.money(b.amount()) + "</bold></gold></gray>"));
        lore.add(MM.deserialize("<!italic><gray>Contributeurs: <white>" + b.contributors() + "</white></gray>"));
        if (b.lastIssuerName() != null) {
            lore.add(MM.deserialize("<!italic><gray>Dernière pose: <aqua>" + b.lastIssuerName() + "</aqua></gray>"));
        }
        List<BountyManager.Contribution> topContribs = plugin.bounties().topContributors(b.target(), 3);
        if (!topContribs.isEmpty()) {
            lore.add(MM.deserialize("<!italic> "));
            lore.add(MM.deserialize("<!italic><dark_red><bold>Top contributeurs</bold></dark_red>"));
            String[] medals = {"<gold>①", "<gray>②", "<#cd7f32>③"};
            for (int i = 0; i < topContribs.size(); i++) {
                BountyManager.Contribution co = topContribs.get(i);
                lore.add(MM.deserialize("<!italic>" + medals[i] + " <white>" + co.issuerName() +
                        "</white> <dark_gray>→</dark_gray> <gold>$" + Msg.money(co.amount()) + "</gold>"));
            }
        }
        NetworkRoster.Entry entry = plugin.roster() != null ? plugin.roster().get(b.targetName()) : null;
        boolean online = entry != null;
        if (online) {
            lore.add(MM.deserialize("<!italic><green>● En ligne</green> <dark_gray>(" + entry.server() + ")</dark_gray>"));
        } else {
            lore.add(MM.deserialize("<!italic><red>● Hors ligne</red>"));
        }
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic><yellow>▶ Clic gauche : ajouter à la prime</yellow>"));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();

        if (slot == 45) { open(p, page - 1); return; }
        if (slot == 53) { open(p, page + 1); return; }
        if (slot == 49) {
            p.closeInventory();
            promptTarget(p);
            return;
        }

        UUID target = slotTarget.get(slot);
        if (target == null) return;
        BountyManager.Bounty b = plugin.bounties().get(target);
        if (b == null) { open(p, page); return; }
        p.closeInventory();
        promptAmount(p, target, b.targetName());
    }

    private void promptTarget(Player p) {
        plugin.chatPrompt().ask(p,
                "<gold>Tape le <bold>pseudo</bold> du joueur sur qui poser une prime.</gold>",
                30,
                raw -> {
                    String name = raw.trim();
                    if (name.isEmpty()) { p.sendMessage(Msg.err("Pseudo vide.")); reopen(p); return; }
                    if (name.equalsIgnoreCase(p.getName())) {
                        p.sendMessage(Msg.err("Tu ne peux pas te mettre une prime à toi-même."));
                        reopen(p); return;
                    }
                    UUID target = plugin.players().resolveUuid(name);
                    if (target == null) {
                        p.sendMessage(Msg.err("Joueur <white>" + name + "</white> introuvable."));
                        reopen(p); return;
                    }
                    String resolvedName = name;
                    var pd = plugin.players().loadOffline(target);
                    if (pd != null) resolvedName = pd.name();
                    promptAmount(p, target, resolvedName);
                });
    }

    private void promptAmount(Player p, UUID target, String targetName) {
        plugin.chatPrompt().ask(p,
                "<gold>Montant à ajouter sur <white>" + targetName + "</white> ? (ex: <yellow>500</yellow>, <yellow>5k</yellow>, <yellow>1m</yellow>)</gold>",
                30,
                raw -> applyAdd(p, target, targetName, raw));
    }

    private void applyAdd(Player p, UUID target, String targetName, String raw) {
        double amount = Msg.parseAmount(raw);
        double min = plugin.bounties().minAmount();
        double max = plugin.bounties().maxAmount();
        if (amount <= 0) { p.sendMessage(Msg.err("Montant invalide.")); reopen(p); return; }
        if (amount < min) {
            p.sendMessage(Msg.err("Minimum <yellow>$" + Msg.money(min) + "</yellow>."));
            reopen(p); return;
        }
        if (amount > max) {
            p.sendMessage(Msg.err("Maximum <yellow>$" + Msg.money(max) + "</yellow>."));
            reopen(p); return;
        }
        if (!plugin.economy().has(p.getUniqueId(), amount)) {
            p.sendMessage(Msg.err("Fonds insuffisants."));
            reopen(p); return;
        }
        if (!plugin.economy().withdraw(p.getUniqueId(), amount, "bounty on " + targetName)) {
            p.sendMessage(Msg.err("Échec du paiement."));
            reopen(p); return;
        }
        double total = plugin.bounties().add(target, targetName, p.getUniqueId(), p.getName(), amount);
        if (total < 0) {
            plugin.economy().deposit(p.getUniqueId(), amount, "bounty refund");
            p.sendMessage(Msg.err("Erreur interne, montant remboursé."));
            reopen(p); return;
        }
        p.sendMessage(Msg.ok("<green>Prime de <yellow>$" + Msg.money(amount) + "</yellow> ajoutée sur <white>" +
                targetName + "</white>. Total: <gold>$" + Msg.money(total) + "</gold>.</green>"));
        Bukkit.broadcast(Msg.mm("<dark_red>\u2620 <red>Une prime de <gold>$" + Msg.money(total) +
                "</gold> pèse sur la tête de <white>" + targetName + "</white> !</red>"));
        Player targetOnline = Bukkit.getPlayerExact(targetName);
        if (targetOnline != null) {
            targetOnline.sendMessage(Msg.err("<red>Une prime de <gold>$" + Msg.money(amount) +
                    "</gold> vient d'être posée sur toi par <aqua>" + p.getName() + "</aqua>.</red>"));
        }
        reopen(p);
    }

    private void reopen(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) new BountyGUI(plugin).open(p, 0);
        }, 10L);
    }
}
