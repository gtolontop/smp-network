package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.BountyManager;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class StatGUI extends GUIHolder {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final int SLOT_HEAD     = 4;
    private static final int SLOT_COMBAT   = 19;
    private static final int SLOT_TIME     = 21;
    private static final int SLOT_ECONOMY  = 23;
    private static final int SLOT_TEAM     = 25;
    private static final int SLOT_BOUNTY   = 30;
    private static final int SLOT_MISC     = 32;
    private static final int SLOT_BACK     = 40;

    private final SMPCore plugin;

    public StatGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, UUID target, String fallbackName) {
        PlayerData d = plugin.players().loadOffline(target);
        if (d == null) {
            viewer.sendMessage(Msg.err("Aucune donnée pour ce joueur."));
            return;
        }

        Player online = Bukkit.getPlayer(target);
        String name = online != null ? online.getName()
                : (d.name() != null ? d.name() : fallbackName);
        String displayName = d.nickname() != null ? d.nickname() : name;

        Inventory inv = Bukkit.createInventory(this, 45,
                GUIUtil.title("<gradient:#a8edea:#fed6e3><bold>✨ Stats</bold></gradient> <dark_gray>»</dark_gray> <aqua>" + displayName + "</aqua>"));
        GUIUtil.fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        inv.setItem(SLOT_HEAD, renderHead(target, name, displayName, online, d));
        inv.setItem(SLOT_COMBAT, renderCombat(d));
        inv.setItem(SLOT_TIME, renderTime(d, online));
        inv.setItem(SLOT_ECONOMY, renderEconomy(d));
        inv.setItem(SLOT_TEAM, renderTeam(d));
        inv.setItem(SLOT_BOUNTY, renderBounty(target));
        inv.setItem(SLOT_MISC, renderMisc(d, online));

        inv.setItem(SLOT_BACK, GUIUtil.item(Material.BARRIER,
                "<red><bold>Fermer</bold></red>",
                "",
                "<gray>▶ Clic pour fermer</gray>"));

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private ItemStack renderHead(UUID target, String name, String displayName, Player online, PlayerData d) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        meta.setOwningPlayer(op);

        boolean isOnline = online != null;
        String onlineLine;
        if (isOnline) {
            onlineLine = "<green>● En ligne</green>";
        } else {
            NetworkRoster.Entry entry = plugin.roster() != null ? plugin.roster().get(name) : null;
            if (entry != null) {
                onlineLine = "<green>● En ligne</green> <dark_gray>(" + entry.server() + ")</dark_gray>";
            } else {
                onlineLine = "<red>● Hors-ligne</red>";
            }
        }

        meta.displayName(MM.deserialize("<!italic><gradient:#a8edea:#fed6e3><bold>" + displayName + "</bold></gradient>"));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic> "));
        if (d.nickname() != null && !d.nickname().equalsIgnoreCase(name)) {
            lore.add(MM.deserialize("<!italic><gray>Pseudo: <white>" + name + "</white></gray>"));
        }
        lore.add(MM.deserialize("<!italic><gray>UUID: <dark_gray>" + target.toString().substring(0, 13) + "…</dark_gray></gray>"));
        lore.add(MM.deserialize("<!italic><gray>Statut: " + onlineLine + "</gray>"));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack renderCombat(PlayerData d) {
        double kdr = d.deaths() > 0 ? (double) d.kills() / d.deaths() : d.kills();
        return GUIUtil.item(Material.DIAMOND_SWORD,
                "<gradient:#ff9966:#ff5e62><bold>⚔ Combat</bold></gradient>",
                "",
                "<gray>Kills: <green>" + d.kills() + "</green></gray>",
                "<gray>Morts: <red>" + d.deaths() + "</red></gray>",
                "<gray>KDR: <white>" + String.format("%.2f", kdr) + "</white></gray>",
                "",
                "<gray>Kills du jour: <yellow>" + d.dailyKills() + "</yellow></gray>");
    }

    private ItemStack renderTime(PlayerData d, Player online) {
        long totalSec = d.playtimeSec();
        long afkSec = 0;
        if (online != null && plugin.afk() != null) afkSec = plugin.afk().accumulatedAfkSec(online);
        long activeSec = Math.max(0, totalSec - afkSec);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Total: <white>" + Msg.duration(totalSec) + "</white></gray>");
        lore.add("<gray>Actif: <green>" + Msg.duration(activeSec) + "</green></gray>");
        if (afkSec > 0) {
            lore.add("<gray>AFK: <yellow>" + Msg.duration(afkSec) + "</yellow></gray>");
        }
        if (online != null && plugin.afk() != null && plugin.afk().isAfk(online)) {
            lore.add("");
            lore.add("<yellow>⚠ Actuellement AFK</yellow>");
        }
        return GUIUtil.item(Material.CLOCK,
                "<gradient:#fceabb:#f8b500><bold>⏱ Temps de jeu</bold></gradient>",
                lore.toArray(new String[0]));
    }

    private ItemStack renderEconomy(PlayerData d) {
        return GUIUtil.item(Material.GOLD_INGOT,
                "<gradient:#f7ff00:#db36a4><bold>💰 Économie</bold></gradient>",
                "",
                "<gray>Argent: <green>$" + Msg.money(d.money()) + "</green></gray>",
                "<gray>Saphirs: <aqua>" + d.shards() + "</aqua></gray>");
    }

    private ItemStack renderTeam(PlayerData d) {
        if (d.teamId() == null) {
            return GUIUtil.item(Material.GRAY_BANNER,
                    "<gray><bold>🏷 Team</bold></gray>",
                    "",
                    "<dark_gray>Aucune team.</dark_gray>",
                    "",
                    "<dark_gray>/team pour en créer ou rejoindre.</dark_gray>");
        }
        TeamManager.Team team = plugin.teams().get(d.teamId());
        if (team == null) {
            return GUIUtil.item(Material.GRAY_BANNER,
                    "<gray><bold>🏷 Team</bold></gray>",
                    "",
                    "<dark_gray>Team introuvable.</dark_gray>");
        }
        String color = team.color() != null ? team.color() : "<white>";
        return GUIUtil.item(Material.WHITE_BANNER,
                "<gradient:#43cea2:#185a9d><bold>🏷 Team</bold></gradient>",
                "",
                "<gray>Nom: " + color + team.name() + "</gray>",
                "<gray>Tag: " + color + "[" + team.tag() + "]</gray>",
                "<gray>Membres: <white>" + plugin.teams().memberCount(d.teamId()) + "</white></gray>");
    }

    private ItemStack renderBounty(UUID target) {
        BountyManager mgr = plugin.bounties();
        if (mgr == null) {
            return GUIUtil.item(Material.SHIELD,
                    "<green><bold>🎯 Prime</bold></green>",
                    "",
                    "<dark_gray>Système indisponible.</dark_gray>");
        }
        BountyManager.Bounty b = mgr.get(target);
        if (b == null) {
            return GUIUtil.item(Material.SHIELD,
                    "<green><bold>🎯 Prime</bold></green>",
                    "",
                    "<gray>Aucune prime sur sa tête.</gray>");
        }
        return GUIUtil.item(Material.SKELETON_SKULL,
                "<gradient:#f85032:#e73827><bold>🎯 Prime</bold></gradient>",
                "",
                "<gray>Montant: <gold>$" + Msg.money(b.amount()) + "</gold></gray>",
                "<gray>Contributeurs: <white>" + b.contributors() + "</white></gray>",
                "<gray>Dernière pose: <aqua>" + b.lastIssuerName() + "</aqua></gray>");
    }

    private ItemStack renderMisc(PlayerData d, Player online) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Première connexion:</gray>");
        lore.add("  <white>" + DATE_FMT.format(new Date(d.firstJoin() * 1000L)) + "</white>");
        lore.add("<gray>Dernière activité:</gray>");
        lore.add("  <white>" + DATE_FMT.format(new Date(d.lastSeen() * 1000L)) + "</white>");
        if (online != null) {
            int ping = online.getPing();
            String color = ping < 80 ? "<green>" : ping < 180 ? "<yellow>" : "<red>";
            lore.add("");
            lore.add("<gray>Ping: " + color + ping + "ms</></gray>");
        }
        return GUIUtil.item(Material.PAPER,
                "<gradient:#c2e9fb:#a1c4fd><bold>📅 Divers</bold></gradient>",
                lore.toArray(new String[0]));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getRawSlot() == SLOT_BACK) {
            p.closeInventory();
        }
    }
}
