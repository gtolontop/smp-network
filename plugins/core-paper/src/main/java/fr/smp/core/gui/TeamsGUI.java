package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /team GUI — personal team management. Opens on the user's own team panel
 * (or the "no team" landing if they aren't in one). The "browse all teams"
 * list is intentionally removed — the user complained that it was noisy and
 * not how they use the menu day-to-day.
 */
public class TeamsGUI extends GUIHolder {

    private enum View { LANDING, MY_PANEL, COLORS, INVITE, KICK }

    private static final Map<String, Material> COLOR_BANNERS = new java.util.LinkedHashMap<>();
    static {
        COLOR_BANNERS.put("<white>",       Material.WHITE_BANNER);
        COLOR_BANNERS.put("<gray>",        Material.LIGHT_GRAY_BANNER);
        COLOR_BANNERS.put("<dark_gray>",   Material.GRAY_BANNER);
        COLOR_BANNERS.put("<black>",       Material.BLACK_BANNER);
        COLOR_BANNERS.put("<red>",         Material.RED_BANNER);
        COLOR_BANNERS.put("<dark_red>",    Material.BROWN_BANNER);
        COLOR_BANNERS.put("<gold>",        Material.ORANGE_BANNER);
        COLOR_BANNERS.put("<yellow>",      Material.YELLOW_BANNER);
        COLOR_BANNERS.put("<green>",       Material.LIME_BANNER);
        COLOR_BANNERS.put("<dark_green>",  Material.GREEN_BANNER);
        COLOR_BANNERS.put("<aqua>",        Material.LIGHT_BLUE_BANNER);
        COLOR_BANNERS.put("<dark_aqua>",   Material.CYAN_BANNER);
        COLOR_BANNERS.put("<blue>",        Material.BLUE_BANNER);
        COLOR_BANNERS.put("<light_purple>",Material.MAGENTA_BANNER);
        COLOR_BANNERS.put("<dark_purple>", Material.PURPLE_BANNER);
        COLOR_BANNERS.put("<pink>",        Material.PINK_BANNER);
    }

    private final SMPCore plugin;
    private final Map<Integer, UUID> slotUuid = new HashMap<>();
    private final Map<Integer, String> slotColor = new HashMap<>();
    private View view = View.LANDING;
    private long pendingDisband = 0;

    public TeamsGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        PlayerData d = plugin.players().get(p);
        if (d != null && d.teamId() != null && plugin.teams().get(d.teamId()) != null) {
            openPanel(p);
        } else {
            openLanding(p);
        }
    }

    private void openLanding(Player p) {
        view = View.LANDING;
        Inventory inv = Bukkit.createInventory(this, 27,
                GUIUtil.title("<gradient:#a1c4fd:#c2e9fb><bold>Ma Team</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        inv.setItem(11, GUIUtil.item(Material.EMERALD,
                "<green><bold>Créer une team</bold></green>",
                "<gray>Coût: <yellow>$" + Msg.money(plugin.teams().creationCost()) + "</yellow></gray>",
                "",
                "<yellow>▶ Clic pour créer</yellow>"));
        inv.setItem(15, GUIUtil.item(Material.WRITABLE_BOOK,
                "<aqua><bold>Rejoindre une team</bold></aqua>",
                "<gray>Accepte une invitation avec</gray>",
                "<white>/team join <tag></white>"));

        this.inventory = inv;
        p.openInventory(inv);
    }

    private void openPanel(Player p) {
        PlayerData d = plugin.players().get(p);
        if (d == null || d.teamId() == null) { openLanding(p); return; }
        TeamManager.Team t = plugin.teams().get(d.teamId());
        if (t == null) { openLanding(p); return; }
        view = View.MY_PANEL;

        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title(t.color() + "<bold>[" + t.tag() + "] " + t.name() + "</bold><reset>"));
        GUIUtil.fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        boolean owner = t.owner().equals(p.getUniqueId().toString());

        inv.setItem(10, GUIUtil.item(Material.ENDER_PEARL,
                "<aqua><bold>Team Home</bold></aqua>",
                t.home() != null ? "<gray>Défini.</gray>" : "<red>Non défini.</red>",
                "",
                "<green>▶ Clic gauche: TP</green>",
                owner ? "<yellow>▶ Clic droit: Définir ici</yellow>" : "<dark_gray>(owner uniquement)</dark_gray>"));

        if (owner) {
            inv.setItem(12, GUIUtil.item(Material.WRITABLE_BOOK,
                    "<gold><bold>Inviter un joueur</bold></gold>",
                    "<gray>Choisir parmi les joueurs en ligne.</gray>"));
            inv.setItem(13, GUIUtil.item(Material.REDSTONE,
                    "<red><bold>Kick un membre</bold></red>",
                    "<gray>Choisir parmi les membres.</gray>"));
            inv.setItem(14, GUIUtil.item(Material.BLUE_DYE,
                    "<aqua><bold>Changer la couleur</bold></aqua>",
                    "<gray>Actuelle: " + t.color() + "exemple<reset></gray>"));
            boolean confirmingDisband = pendingDisband > System.currentTimeMillis();
            inv.setItem(16, GUIUtil.item(confirmingDisband ? Material.TNT : Material.BARRIER,
                    confirmingDisband ? "<red><bold>⚠ Confirmer dissolution</bold></red>"
                            : "<red><bold>Dissoudre la team</bold></red>",
                    confirmingDisband ? "<red>Clic: Confirmer</red>" : "<gray>Cliquer puis reconfirmer.</gray>"));
        } else {
            inv.setItem(16, GUIUtil.item(Material.BARRIER,
                    "<red><bold>Quitter la team</bold></red>",
                    "<gray>Clic pour sortir.</gray>"));
        }

        slotUuid.clear();
        int[] row = {28, 29, 30, 31, 32, 33, 34};
        List<TeamManager.Member> members = plugin.teams().members(t.id());
        for (int i = 0; i < members.size() && i < row.length; i++) {
            TeamManager.Member m = members.get(i);
            var offline = Bukkit.getOfflinePlayer(m.uuid());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(offline);
                sm.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<!italic><white><bold>" + offline.getName() + "</bold></white>"));
                sm.lore(List.of(
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<!italic><gray>" + m.role() + "</gray>")
                ));
                head.setItemMeta(sm);
            }
            inv.setItem(row[i], head);
            slotUuid.put(row[i], m.uuid());
        }

        inv.setItem(49, GUIUtil.item(Material.BOOK,
                "<gray><bold>Solde de team</bold></gray>",
                "<green>$" + Msg.money(t.balance()) + "</green>",
                "<gray>Membres: <white>" + members.size() + "</white></gray>"));
        this.inventory = inv;
        p.openInventory(inv);
    }

    private void openColors(Player p) {
        PlayerData d = plugin.players().get(p);
        if (d == null || d.teamId() == null) { open(p); return; }
        TeamManager.Team t = plugin.teams().get(d.teamId());
        if (t == null || !t.owner().equals(p.getUniqueId().toString())) { openPanel(p); return; }
        view = View.COLORS;
        slotColor.clear();

        Inventory inv = Bukkit.createInventory(this, 36,
                GUIUtil.title("<gold><bold>Couleur de team</bold></gold>"));
        GUIUtil.fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
        int i = 0;
        for (var e : COLOR_BANNERS.entrySet()) {
            if (i >= slots.length) break;
            inv.setItem(slots[i], GUIUtil.item(e.getValue(),
                    e.getKey() + "<bold>" + t.name() + "</bold><reset>",
                    "<gray>Clic pour choisir.</gray>"));
            slotColor.put(slots[i], e.getKey());
            i++;
        }
        inv.setItem(31, GUIUtil.item(Material.ARROW, "<yellow>◀ Retour</yellow>"));
        this.inventory = inv;
        p.openInventory(inv);
    }

    private void openInvite(Player p) {
        PlayerData d = plugin.players().get(p);
        if (d == null || d.teamId() == null) { open(p); return; }
        TeamManager.Team t = plugin.teams().get(d.teamId());
        if (t == null || !t.owner().equals(p.getUniqueId().toString())) { openPanel(p); return; }
        view = View.INVITE;
        slotUuid.clear();

        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gold><bold>Inviter un joueur</bold></gold>"));
        GUIUtil.fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        int[] inner = innerSlots(6);
        int i = 0;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (i >= inner.length) break;
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            PlayerData od = plugin.players().get(other);
            if (od != null && od.teamId() != null) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(other);
                sm.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<!italic><white><bold>" + other.getName() + "</bold></white>"));
                sm.lore(List.of(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<!italic><green>▶ Clic pour inviter</green>")));
                head.setItemMeta(sm);
            }
            inv.setItem(inner[i], head);
            slotUuid.put(inner[i], other.getUniqueId());
            i++;
        }
        inv.setItem(49, GUIUtil.item(Material.ARROW, "<yellow>◀ Retour</yellow>"));
        this.inventory = inv;
        p.openInventory(inv);
    }

    private void openKick(Player p) {
        PlayerData d = plugin.players().get(p);
        if (d == null || d.teamId() == null) { open(p); return; }
        TeamManager.Team t = plugin.teams().get(d.teamId());
        if (t == null || !t.owner().equals(p.getUniqueId().toString())) { openPanel(p); return; }
        view = View.KICK;
        slotUuid.clear();

        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<red><bold>Kick un membre</bold></red>"));
        GUIUtil.fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        int[] inner = innerSlots(6);
        int i = 0;
        for (TeamManager.Member m : plugin.teams().members(t.id())) {
            if (i >= inner.length) break;
            if (m.uuid().equals(p.getUniqueId())) continue;
            var offline = Bukkit.getOfflinePlayer(m.uuid());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(offline);
                sm.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<!italic><white><bold>" + offline.getName() + "</bold></white>"));
                sm.lore(List.of(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<!italic><red>▶ Clic pour kick</red>")));
                head.setItemMeta(sm);
            }
            inv.setItem(inner[i], head);
            slotUuid.put(inner[i], m.uuid());
            i++;
        }
        inv.setItem(49, GUIUtil.item(Material.ARROW, "<yellow>◀ Retour</yellow>"));
        this.inventory = inv;
        p.openInventory(inv);
    }

    private int[] innerSlots(int rows) {
        int[] inner = new int[(rows - 2) * 7];
        int idx = 0;
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < 8; c++) inner[idx++] = r * 9 + c;
        }
        return inner;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        switch (view) {
            case LANDING -> onLandingClick(p, raw);
            case MY_PANEL -> onPanelClick(p, raw, event);
            case COLORS -> onColorsClick(p, raw);
            case INVITE -> onInviteClick(p, raw);
            case KICK -> onKickClick(p, raw);
        }
    }

    private void onLandingClick(Player p, int raw) {
        if (raw == 11) { startCreateFlow(p); return; }
        // slot 15 is an info tile (no action needed).
    }

    private void onPanelClick(Player p, int raw, InventoryClickEvent event) {
        PlayerData d = plugin.players().get(p);
        if (d == null || d.teamId() == null) { openLanding(p); return; }
        TeamManager.Team t = plugin.teams().get(d.teamId());
        if (t == null) { openLanding(p); return; }
        boolean owner = t.owner().equals(p.getUniqueId().toString());

        if (raw == 10) {
            if (event.getClick() == ClickType.RIGHT && owner) {
                plugin.teams().setHome(t.id(), p.getLocation());
                p.sendMessage(Msg.ok("<green>Home de team défini.</green>"));
                openPanel(p);
                return;
            }
            if (t.home() == null) { p.sendMessage(Msg.err("Aucun home défini.")); return; }
            p.closeInventory();
            p.teleportAsync(t.home());
            p.sendMessage(Msg.ok("<aqua>Téléporté au home de team.</aqua>"));
            return;
        }
        if (!owner) {
            if (raw == 16) {
                plugin.teams().removeMember(t.id(), p.getUniqueId());
                p.sendMessage(Msg.ok("<red>Team quittée.</red>"));
                openLanding(p);
            }
            return;
        }
        if (raw == 12) openInvite(p);
        else if (raw == 13) openKick(p);
        else if (raw == 14) openColors(p);
        else if (raw == 16) {
            long now = System.currentTimeMillis();
            if (pendingDisband > now) {
                pendingDisband = 0;
                for (TeamManager.Member m : plugin.teams().members(t.id())) {
                    plugin.teams().removeMember(t.id(), m.uuid());
                }
                plugin.teams().disband(t.id());
                p.sendMessage(Msg.ok("<red>Team dissoute.</red>"));
                openLanding(p);
            } else {
                pendingDisband = now + 5000;
                openPanel(p);
            }
        }
    }

    private void onColorsClick(Player p, int raw) {
        if (raw == 31) { openPanel(p); return; }
        String color = slotColor.get(raw);
        if (color == null) return;
        PlayerData d = plugin.players().get(p);
        if (d == null || d.teamId() == null) return;
        TeamManager.Team t = plugin.teams().get(d.teamId());
        if (t == null || !t.owner().equals(p.getUniqueId().toString())) return;
        plugin.teams().setColor(t.id(), color);
        p.sendMessage(Msg.ok("<green>Couleur mise à jour.</green>"));
        if (plugin.nametags() != null) plugin.nametags().refreshAll();
        openPanel(p);
    }

    private void onInviteClick(Player p, int raw) {
        if (raw == 49) { openPanel(p); return; }
        UUID target = slotUuid.get(raw);
        if (target == null) return;
        Player tp = Bukkit.getPlayer(target);
        if (tp == null) { p.sendMessage(Msg.err("Joueur hors-ligne.")); return; }
        PlayerData d = plugin.players().get(p);
        TeamManager.Team t = d != null ? plugin.teams().get(d.teamId()) : null;
        if (t == null) return;
        if (plugin.teams().isFull(t.id())) {
            p.sendMessage(Msg.err("Team pleine (" + plugin.teams().maxMembers() + " max).")); return;
        }
        plugin.teamInvites().invite(target, t.id());
        tp.sendMessage(Msg.info("<aqua>" + p.getName() + "</aqua> t'invite dans <white>" +
                t.color() + "[" + t.tag() + "] " + t.name() + "<reset></white>. <green>/team join " + t.tag() + "</green>"));
        p.sendMessage(Msg.ok("<green>Invitation envoyée à " + tp.getName() + ".</green>"));
        openPanel(p);
    }

    private void onKickClick(Player p, int raw) {
        if (raw == 49) { openPanel(p); return; }
        UUID target = slotUuid.get(raw);
        if (target == null) return;
        PlayerData d = plugin.players().get(p);
        TeamManager.Team t = d != null ? plugin.teams().get(d.teamId()) : null;
        if (t == null || !t.owner().equals(p.getUniqueId().toString())) return;
        plugin.teams().removeMember(t.id(), target);
        p.sendMessage(Msg.ok("<red>Membre kické.</red>"));
        if (plugin.nametags() != null) plugin.nametags().refreshAll();
        openKick(p);
    }

    private void startCreateFlow(Player p) {
        p.closeInventory();
        plugin.chatPrompt().ask(p,
                "<aqua>Tape le tag de ta team (2-5 alphanumériques) :</aqua>",
                30, tag -> {
            if (!tag.matches("[A-Za-z0-9]{2,5}")) {
                p.sendMessage(Msg.err("Tag invalide.")); return;
            }
            if (plugin.teams().byTag(tag) != null) {
                p.sendMessage(Msg.err("Tag déjà pris.")); return;
            }
            plugin.chatPrompt().ask(p,
                    "<aqua>Tape le nom de la team :</aqua>", 30, name -> {
                if (name.isBlank() || name.length() > 32) {
                    p.sendMessage(Msg.err("Nom invalide.")); return;
                }
                PlayerData d = plugin.players().get(p);
                if (d != null && d.teamId() != null) {
                    p.sendMessage(Msg.err("Tu es déjà dans une team.")); return;
                }
                double cost = plugin.teams().creationCost();
                if (!plugin.economy().has(p.getUniqueId(), cost)) {
                    p.sendMessage(Msg.err("Il te faut $" + Msg.money(cost) + ".")); return;
                }
                plugin.economy().withdraw(p.getUniqueId(), cost, "team.create");
                TeamManager.Team t = plugin.teams().create(tag.toLowerCase(), tag, name, p.getUniqueId());
                if (t == null) {
                    plugin.economy().deposit(p.getUniqueId(), cost, "team.create.refund");
                    p.sendMessage(Msg.err("Création échouée.")); return;
                }
                p.sendMessage(Msg.ok("<green>Team <aqua>[" + tag + "] " + name + "</aqua> créée.</green>"));
                if (plugin.nametags() != null) plugin.nametags().refreshAll();
            });
        });
    }
}
