package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.BountyManager;
import fr.smp.core.managers.ModerationManager;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class AdminStatGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private static final int MAX_HISTORY = 8;

    // Display slots
    private static final int SLOT_HEAD       = 4;
    private static final int SLOT_AUTH       = 19;
    private static final int SLOT_STATS      = 20;
    private static final int SLOT_ONLINE     = 21;
    private static final int SLOT_PERMS      = 22;
    private static final int SLOT_HOMES      = 23;
    private static final int SLOT_BOUNTY     = 24;
    private static final int SLOT_MODSTATUS  = 25;
    private static final int SLOT_HISTORY    = 31;

    // Action slots (bottom row)
    private static final int SLOT_INVSEE     = 47;
    private static final int SLOT_TP         = 48;
    private static final int SLOT_KICK       = 49;
    private static final int SLOT_MUTE       = 50;
    private static final int SLOT_BAN        = 51;
    private static final int SLOT_REFRESH    = 53;
    private static final int SLOT_CLOSE      = 45;

    private final SMPCore plugin;
    private UUID target;
    private String targetName;

    public AdminStatGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, UUID targetUuid) {
        this.target = targetUuid;
        Player online = Bukkit.getPlayer(targetUuid);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetUuid);
        boolean isOnline = online != null && online.isOnline();
        this.targetName = online != null ? online.getName()
                : (offline.getName() != null ? offline.getName() : targetUuid.toString().substring(0, 8));

        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#ff5e62:#ff9966><bold>🛡 Admin</bold></gradient> <dark_gray>»</dark_gray> <white>" + targetName + "</white>"));
        GUIUtil.fillBorder(inv, Material.RED_STAINED_GLASS_PANE);
        // Inner background
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                int s = row * 9 + col;
                if (inv.getItem(s) == null) inv.setItem(s, GUIUtil.filler(Material.BLACK_STAINED_GLASS_PANE));
            }
        }

        PlayerData data = plugin.players().loadOffline(targetUuid);
        AuthInfo authInfo = fetchAuthInfo(targetName);

        inv.setItem(SLOT_HEAD, renderHead(targetUuid, targetName, isOnline, online, data, authInfo));
        inv.setItem(SLOT_AUTH, renderAuth(authInfo));
        inv.setItem(SLOT_STATS, renderStats(data));
        inv.setItem(SLOT_ONLINE, renderOnline(online, isOnline));
        inv.setItem(SLOT_PERMS, renderPerms(targetUuid, online, isOnline));
        inv.setItem(SLOT_HOMES, renderHomes(targetUuid));
        inv.setItem(SLOT_BOUNTY, renderBounty(targetUuid));
        inv.setItem(SLOT_MODSTATUS, renderModStatus(targetUuid));
        inv.setItem(SLOT_HISTORY, renderHistory(targetUuid));

        // Action row
        inv.setItem(SLOT_INVSEE, GUIUtil.item(Material.CHEST,
                "<gradient:#fceabb:#f8b500><bold>👜 Inventaire</bold></gradient>",
                "",
                "<gray>Inspecter l'inventaire.</gray>",
                "",
                "<yellow>▶ Clic: /invsee " + targetName + "</yellow>"));

        inv.setItem(SLOT_TP, GUIUtil.item(Material.ENDER_PEARL,
                "<gradient:#b993d6:#8ca6db><bold>↗ Téléport</bold></gradient>",
                "",
                isOnline
                        ? "<gray>Te téléporter au joueur.</gray>"
                        : "<dark_gray>Joueur hors-ligne.</dark_gray>",
                "",
                isOnline
                        ? "<yellow>▶ Clic: /tp " + targetName + "</yellow>"
                        : "<dark_gray>Indisponible.</dark_gray>"));

        inv.setItem(SLOT_KICK, GUIUtil.item(Material.LEATHER_BOOTS,
                "<gradient:#ffb347:#ffcc33><bold>🥾 Kick</bold></gradient>",
                "",
                isOnline
                        ? "<gray>Expulser ce joueur.</gray>"
                        : "<dark_gray>Joueur hors-ligne.</dark_gray>",
                "",
                isOnline
                        ? "<yellow>▶ Clic: prépare /kick " + targetName + "</yellow>"
                        : "<dark_gray>Indisponible.</dark_gray>"));

        ModerationManager.Mute mute = plugin.moderation().activeMute(targetUuid);
        inv.setItem(SLOT_MUTE, GUIUtil.item(Material.WRITABLE_BOOK,
                mute != null
                        ? "<gradient:#43cea2:#185a9d><bold>🔊 Unmute</bold></gradient>"
                        : "<gradient:#ff9966:#ff5e62><bold>🔇 Mute</bold></gradient>",
                "",
                mute != null
                        ? "<gray>Joueur actuellement mute.</gray>"
                        : "<gray>Empêcher le joueur de parler.</gray>",
                "",
                mute != null
                        ? "<yellow>▶ Clic: prépare /unmute " + targetName + "</yellow>"
                        : "<yellow>▶ Clic: prépare /mute " + targetName + "</yellow>"));

        ModerationManager.Ban ban = plugin.moderation().activeBan(targetUuid);
        inv.setItem(SLOT_BAN, GUIUtil.item(Material.BARRIER,
                ban != null
                        ? "<gradient:#43cea2:#185a9d><bold>✔ Unban</bold></gradient>"
                        : "<gradient:#f85032:#e73827><bold>⛔ Ban</bold></gradient>",
                "",
                ban != null
                        ? "<gray>Joueur actuellement ban.</gray>"
                        : "<gray>Bannir ce joueur.</gray>",
                "",
                ban != null
                        ? "<yellow>▶ Clic: prépare /unban " + targetName + "</yellow>"
                        : "<yellow>▶ Clic: prépare /ban " + targetName + "</yellow>"));

        inv.setItem(SLOT_REFRESH, GUIUtil.item(Material.SUNFLOWER,
                "<gradient:#fceabb:#f8b500><bold>↻ Rafraîchir</bold></gradient>",
                "",
                "<gray>Recharger les infos.</gray>"));

        inv.setItem(SLOT_CLOSE, GUIUtil.item(Material.OAK_DOOR,
                "<red><bold>← Fermer</bold></red>"));

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private ItemStack renderHead(UUID uuid, String name, boolean isOnline, Player online,
                                 PlayerData data, AuthInfo authInfo) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(MM.deserialize("<!italic><gradient:#ff5e62:#ff9966><bold>" + name + "</bold></gradient>"));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic><gray>UUID: <dark_gray>" + uuid + "</dark_gray></gray>"));

        String ip = isOnline && online.getAddress() != null
                ? online.getAddress().getAddress().getHostAddress()
                : (authInfo != null ? authInfo.lastIp : null);
        if (ip != null) {
            lore.add(MM.deserialize("<!italic><gray>IP: <white>" + ip + "</white></gray>"));
        }

        if (isOnline) {
            lore.add(MM.deserialize("<!italic><gray>Statut: <green>● En ligne</green></gray>"));
        } else {
            NetworkRoster.Entry entry = plugin.roster() != null ? plugin.roster().get(name) : null;
            if (entry != null) {
                lore.add(MM.deserialize("<!italic><gray>Statut: <green>● En ligne</green> <dark_gray>(" + entry.server() + ")</dark_gray></gray>"));
            } else {
                lore.add(MM.deserialize("<!italic><gray>Statut: <red>● Hors-ligne</red></gray>"));
            }
        }

        if (data != null && data.nickname() != null && !data.nickname().equalsIgnoreCase(name)) {
            lore.add(MM.deserialize("<!italic><gray>Nickname: <aqua>" + data.nickname() + "</aqua></gray>"));
        }

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack renderAuth(AuthInfo info) {
        if (info == null) {
            return GUIUtil.item(Material.IRON_DOOR,
                    "<gradient:#a8edea:#fed6e3><bold>🔐 Auth</bold></gradient>",
                    "",
                    "<gray>Aucun compte auth trouvé.</gray>");
        }
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (info.registeredAt > 0)
            lore.add("<gray>Inscrit: <white>" + DATE_FMT.format(new Date(info.registeredAt * 1000L)) + "</white></gray>");
        if (info.lastLogin > 0)
            lore.add("<gray>Dernier login: <white>" + DATE_FMT.format(new Date(info.lastLogin * 1000L)) + "</white></gray>");
        if (info.lastIp != null)
            lore.add("<gray>Dernière IP: <white>" + info.lastIp + "</white></gray>");
        lore.add("<gray>Échecs: " + (info.failedAttempts > 0 ? "<red>" : "<white>") + info.failedAttempts + "</></gray>");
        if (info.lockedUntil > 0) {
            long now = System.currentTimeMillis() / 1000L;
            if (info.lockedUntil > now) {
                lore.add("<gray>Verrouillé jusqu'à: <red>" + DATE_FMT.format(new Date(info.lockedUntil * 1000L)) + "</red></gray>");
            }
        }
        lore.add("<gray>Premium UUID: " + (info.premiumUuid != null ? "<green>oui</green>" : "<dark_gray>non</dark_gray>") + "</gray>");
        lore.add("<gray>Cracked UUID: " + (info.crackedUuid != null ? "<yellow>oui</yellow>" : "<dark_gray>non</dark_gray>") + "</gray>");
        return GUIUtil.item(Material.IRON_DOOR,
                "<gradient:#a8edea:#fed6e3><bold>🔐 Auth</bold></gradient>",
                lore.toArray(new String[0]));
    }

    private ItemStack renderStats(PlayerData data) {
        if (data == null) {
            return GUIUtil.item(Material.BOOK,
                    "<gradient:#fceabb:#f8b500><bold>📊 Stats</bold></gradient>",
                    "",
                    "<dark_gray>Aucune donnée.</dark_gray>");
        }
        double kdr = data.deaths() > 0 ? (double) data.kills() / data.deaths() : data.kills();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Argent: <green>$" + Msg.money(data.money()) + "</green></gray>");
        lore.add("<gray>Saphirs: <aqua>" + data.shards() + "</aqua></gray>");
        lore.add("<gray>Kills: <white>" + data.kills() + "</white> <dark_gray>|</dark_gray> Morts: <white>" + data.deaths() + "</white></gray>");
        lore.add("<gray>KDR: <white>" + String.format("%.2f", kdr) + "</white> <dark_gray>|</dark_gray> Daily: <yellow>" + data.dailyKills() + "</yellow></gray>");
        lore.add("<gray>Playtime: <white>" + Msg.duration(data.playtimeSec()) + "</white></gray>");
        lore.add("<gray>Première: <white>" + DATE_FMT.format(new Date(data.firstJoin() * 1000L)) + "</white></gray>");
        lore.add("<gray>Dernière: <white>" + DATE_FMT.format(new Date(data.lastSeen() * 1000L)) + "</white></gray>");
        if (data.teamId() != null) {
            lore.add("<gray>Team: <white>" + data.teamId() + "</white></gray>");
        }
        if (data.hasLastLocation()) {
            lore.add("<gray>Dernière pos: <white>" + data.lastWorld() + " "
                    + String.format("%.0f %.0f %.0f", data.lastX(), data.lastY(), data.lastZ()) + "</white></gray>");
        }
        return GUIUtil.item(Material.WRITTEN_BOOK,
                "<gradient:#fceabb:#f8b500><bold>📊 Stats</bold></gradient>",
                lore.toArray(new String[0]));
    }

    private ItemStack renderOnline(Player online, boolean isOnline) {
        if (!isOnline) {
            return GUIUtil.item(Material.GRAY_DYE,
                    "<gradient:#bdc3c7:#2c3e50><bold>🌐 En ligne</bold></gradient>",
                    "",
                    "<dark_gray>Joueur hors-ligne.</dark_gray>");
        }
        Location loc = online.getLocation();
        double maxHp = online.getAttribute(Attribute.MAX_HEALTH) != null
                ? online.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20;
        List<String> lore = new ArrayList<>();
        lore.add("");
        int ping = online.getPing();
        String pingColor = ping < 80 ? "<green>" : ping < 180 ? "<yellow>" : "<red>";
        lore.add("<gray>Ping: " + pingColor + ping + "ms</></gray>");
        lore.add("<gray>GameMode: <white>" + online.getGameMode().name() + "</white></gray>");
        lore.add("<gray>Monde: <white>" + online.getWorld().getName() + "</white></gray>");
        lore.add("<gray>Position: <white>" + String.format("%.0f %.0f %.0f", loc.getX(), loc.getY(), loc.getZ()) + "</white></gray>");
        lore.add("<gray>Health: <red>" + String.format("%.1f", online.getHealth()) + "</red>/<red>" + String.format("%.1f", maxHp) + "</red></gray>");
        lore.add("<gray>Food: <gold>" + online.getFoodLevel() + "/20</gold></gray>");
        lore.add("<gray>XP: <white>" + String.format("%.0f", online.getExp() * 100) + "% (Lvl " + online.getLevel() + ")</white></gray>");
        lore.add("<gray>Client: <white>" + online.getClientBrandName() + "</white></gray>");
        lore.add("<gray>Locale: <white>" + online.locale() + "</white></gray>");
        return GUIUtil.item(Material.COMPASS,
                "<gradient:#43cea2:#185a9d><bold>🌐 En ligne</bold></gradient>",
                lore.toArray(new String[0]));
    }

    private ItemStack renderPerms(UUID uuid, Player online, boolean isOnline) {
        String group = plugin.permissions().primaryGroup(uuid);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Groupe: <white>" + group + "</white></gray>");
        if (isOnline) {
            lore.add("<gray>OP: " + (online.isOp() ? "<red>Oui</red>" : "<green>Non</green>") + "</gray>");
            lore.add("<gray>AFK: " + (plugin.afk() != null && plugin.afk().isAfk(online) ? "<yellow>Oui</yellow>" : "<green>Non</green>") + "</gray>");
            lore.add("<gray>Vanish: " + (plugin.vanish() != null && plugin.vanish().isVanished(online) ? "<dark_purple>Oui</dark_purple>" : "<green>Non</green>") + "</gray>");
            lore.add("<gray>Admin Mode: " + (plugin.adminMode() != null && plugin.adminMode().isAdmin(online) ? "<red>Oui</red>" : "<green>Non</green>") + "</gray>");
            lore.add("<gray>Combat Tag: " + (plugin.combat() != null && plugin.combat().isTagged(online) ? "<red>Oui</red>" : "<green>Non</green>") + "</gray>");
            lore.add("<gray>Fly: " + (online.isFlying() ? "<aqua>Oui</aqua>" : "<gray>Non</gray>") + "</gray>");
        }
        return GUIUtil.item(Material.NETHER_STAR,
                "<gradient:#c2e9fb:#a1c4fd><bold>⚙ Permissions</bold></gradient>",
                lore.toArray(new String[0]));
    }

    private ItemStack renderHomes(UUID uuid) {
        int count = countHomes(uuid);
        return GUIUtil.item(Material.RED_BED,
                "<gradient:#ff9a9e:#fad0c4><bold>🏠 Homes</bold></gradient>",
                "",
                "<gray>Nombre: <white>" + count + "</white></gray>");
    }

    private ItemStack renderBounty(UUID uuid) {
        BountyManager mgr = plugin.bounties();
        if (mgr == null) {
            return GUIUtil.item(Material.SHIELD,
                    "<gradient:#f85032:#e73827><bold>💰 Prime</bold></gradient>",
                    "",
                    "<dark_gray>Système indisponible.</dark_gray>");
        }
        BountyManager.Bounty b = mgr.get(uuid);
        if (b == null) {
            return GUIUtil.item(Material.SHIELD,
                    "<gradient:#43cea2:#185a9d><bold>💰 Prime</bold></gradient>",
                    "",
                    "<gray>Aucune prime.</gray>");
        }
        return GUIUtil.item(Material.SKELETON_SKULL,
                "<gradient:#f85032:#e73827><bold>💰 Prime</bold></gradient>",
                "",
                "<gray>Montant: <gold>$" + Msg.money(b.amount()) + "</gold></gray>",
                "<gray>Contributeurs: <white>" + b.contributors() + "</white></gray>",
                "<gray>Dernière pose: <aqua>" + b.lastIssuerName() + "</aqua></gray>");
    }

    private ItemStack renderModStatus(UUID uuid) {
        ModerationManager.Ban ban = plugin.moderation().activeBan(uuid);
        ModerationManager.Mute mute = plugin.moderation().activeMute(uuid);
        boolean any = ban != null || mute != null;
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (ban != null) {
            lore.add("<red>● BAN actif</red>");
            lore.add("<gray>  Motif: <white>" + (ban.reason() != null ? ban.reason() : "non spécifié") + "</white></gray>");
            lore.add("<gray>  Par: <aqua>" + ban.issuer() + "</aqua></gray>");
            lore.add("<gray>  Le: <white>" + DATE_FMT.format(new Date(ban.issuedAt() * 1000L)) + "</white></gray>");
            if (ban.permanent()) {
                lore.add("<gray>  Durée: <dark_red>Permanent</dark_red></gray>");
            } else {
                long left = ban.expiresAt() - System.currentTimeMillis() / 1000L;
                lore.add("<gray>  Reste: <white>" + Msg.duration(Math.max(0, left)) + "</white></gray>");
            }
        } else {
            lore.add("<gray>Ban: <green>Non</green></gray>");
        }
        if (mute != null) {
            lore.add("");
            lore.add("<dark_red>● MUTE actif</dark_red>");
            lore.add("<gray>  Motif: <white>" + (mute.reason() != null ? mute.reason() : "non spécifié") + "</white></gray>");
            lore.add("<gray>  Par: <aqua>" + mute.issuer() + "</aqua></gray>");
            long left = mute.expiresAt() - System.currentTimeMillis() / 1000L;
            lore.add("<gray>  Reste: <white>" + Msg.duration(Math.max(0, left)) + "</white></gray>");
        } else {
            lore.add("<gray>Mute: <green>Non</green></gray>");
        }
        return GUIUtil.item(any ? Material.ANVIL : Material.IRON_BLOCK,
                any
                        ? "<gradient:#f85032:#e73827><bold>🔨 Modération</bold></gradient>"
                        : "<gradient:#43cea2:#185a9d><bold>🔨 Modération</bold></gradient>",
                lore.toArray(new String[0]));
    }

    private ItemStack renderHistory(UUID uuid) {
        List<HistoryEntry> history = fetchHistory(uuid);
        if (history.isEmpty()) {
            return GUIUtil.item(Material.WRITABLE_BOOK,
                    "<gradient:#c2e9fb:#a1c4fd><bold>📜 Historique</bold></gradient>",
                    "",
                    "<dark_gray>Aucun historique.</dark_gray>");
        }
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (HistoryEntry e : history) {
            String actionColor = switch (e.action) {
                case "ban" -> "<red>ban</red>";
                case "unban" -> "<green>unban</green>";
                case "kick" -> "<gold>kick</gold>";
                case "mute" -> "<dark_red>mute</dark_red>";
                case "unmute" -> "<green>unmute</green>";
                default -> "<white>" + e.action + "</white>";
            };
            String line = "<dark_gray>•</dark_gray> <white>" + DATE_FMT.format(new Date(e.createdAt * 1000L)) + "</white> "
                    + actionColor + " <gray>par</gray> <aqua>" + e.issuer + "</aqua>";
            lore.add(line);
            if (e.reason != null && !e.reason.isEmpty()) {
                lore.add("  <dark_gray>↳</dark_gray> <gray>" + e.reason + "</gray>");
            }
        }
        return GUIUtil.item(Material.WRITTEN_BOOK,
                "<gradient:#c2e9fb:#a1c4fd><bold>📜 Historique modération</bold></gradient> <gray>(" + history.size() + ")</gray>",
                lore.toArray(new String[0]));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!viewer.hasPermission("smp.admin")) return;
        int slot = event.getRawSlot();
        ClickType click = event.getClick();

        switch (slot) {
            case SLOT_CLOSE -> viewer.closeInventory();
            case SLOT_REFRESH -> open(viewer, target);
            case SLOT_INVSEE -> {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        viewer.performCommand("invsee " + targetName));
            }
            case SLOT_TP -> {
                Player online = Bukkit.getPlayer(target);
                if (online == null) {
                    viewer.sendMessage(Msg.err("Joueur hors-ligne."));
                    return;
                }
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        viewer.performCommand("tp " + targetName));
            }
            case SLOT_KICK -> {
                viewer.closeInventory();
                viewer.sendMessage(Msg.info("<yellow>Tape: <white>/kick " + targetName + " <motif></white></yellow>"));
            }
            case SLOT_MUTE -> {
                viewer.closeInventory();
                ModerationManager.Mute m = plugin.moderation().activeMute(target);
                if (m != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            viewer.performCommand("unmute " + targetName));
                } else {
                    viewer.sendMessage(Msg.info("<yellow>Tape: <white>/mute " + targetName + " <durée> <motif></white></yellow>"));
                }
            }
            case SLOT_BAN -> {
                viewer.closeInventory();
                ModerationManager.Ban b = plugin.moderation().activeBan(target);
                if (b != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            viewer.performCommand("unban " + targetName));
                } else {
                    viewer.sendMessage(Msg.info("<yellow>Tape: <white>/ban " + targetName + " <durée|perm> <motif></white></yellow>"));
                }
            }
            default -> {}
        }
    }

    // --- DB helpers (mirrored from InfoAdminCommand) ---

    private AuthInfo fetchAuthInfo(String name) {
        try (Connection c = plugin.database().get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT registered_at, last_login, last_ip, failed_attempts, locked_until, premium_uuid, cracked_uuid FROM auth_accounts WHERE name_lower=?")) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                AuthInfo info = new AuthInfo();
                info.registeredAt = rs.getLong(1);
                info.lastLogin = rs.getLong(2);
                info.lastIp = rs.getString(3);
                info.failedAttempts = rs.getInt(4);
                info.lockedUntil = rs.getLong(5);
                info.premiumUuid = rs.getString(6);
                info.crackedUuid = rs.getString(7);
                return info;
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private List<HistoryEntry> fetchHistory(UUID uuid) {
        List<HistoryEntry> list = new ArrayList<>();
        try (Connection c = plugin.database().get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT action, issuer, reason, duration, created_at FROM mod_history WHERE uuid=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, MAX_HISTORY);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HistoryEntry e = new HistoryEntry();
                    e.action = rs.getString(1);
                    e.issuer = rs.getString(2);
                    e.reason = rs.getString(3);
                    e.duration = rs.getString(4);
                    e.createdAt = rs.getLong(5);
                    list.add(e);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private int countHomes(UUID uuid) {
        try (Connection c = plugin.database().get();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM homes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    private static class AuthInfo {
        long registeredAt;
        long lastLogin;
        String lastIp;
        int failedAttempts;
        long lockedUntil;
        String premiumUuid;
        String crackedUuid;
    }

    private static class HistoryEntry {
        String action;
        String issuer;
        String reason;
        String duration;
        long createdAt;
    }
}
