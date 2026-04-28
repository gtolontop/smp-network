package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.VanishManager;
import fr.smp.core.managers.VanishManager.Level;
import fr.smp.core.managers.VanishManager.State;
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
import java.util.List;

/**
 * Menu vanish 6×9 :
 * <ul>
 *     <li>Ligne 0 : bordure + tête de profil au centre</li>
 *     <li>Ligne 1 : toggles principaux (level, see-others, NV, no-fall, god, no-pickup, no-drop)</li>
 *     <li>Ligne 2 : toggles secondaires (no-target, no-hunger, blocs, fly, vitesse, hotbar)</li>
 *     <li>Ligne 3 : actions (TP joueur, TP spawn, refresh)</li>
 *     <li>Ligne 4 : presets (stealth, investigator, patrol, build)</li>
 *     <li>Ligne 5 : fermeture / quitter vanish</li>
 * </ul>
 */
public class VanishMenuGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final int SLOT_PROFILE     = 4;

    private static final int SLOT_LEVEL       = 10;
    private static final int SLOT_SEE_OTHERS  = 11;
    private static final int SLOT_NV          = 12;
    private static final int SLOT_NO_FALL     = 13;
    private static final int SLOT_GOD         = 14;
    private static final int SLOT_NO_PICKUP   = 15;
    private static final int SLOT_NO_DROP     = 16;

    private static final int SLOT_NO_TARGET   = 19;
    private static final int SLOT_NO_HUNGER   = 20;
    private static final int SLOT_NO_BLOCKS   = 21;
    private static final int SLOT_FLY         = 22;
    private static final int SLOT_SPEED       = 23;
    private static final int SLOT_HOTBAR_SWAP = 24;
    private static final int SLOT_INFO_DUMP   = 25;

    private static final int SLOT_TP_PLAYER   = 28;
    private static final int SLOT_TP_SPAWN    = 30;
    private static final int SLOT_REFRESH     = 32;
    private static final int SLOT_DUMP_LIST   = 34;

    private static final int SLOT_PRESET_STEALTH      = 37;
    private static final int SLOT_PRESET_INVESTIGATOR = 39;
    private static final int SLOT_PRESET_PATROL       = 41;
    private static final int SLOT_PRESET_BUILD        = 43;

    private static final int SLOT_CLOSE       = 49;
    private static final int SLOT_EXIT_VANISH = 53;

    private final SMPCore plugin;

    public VanishMenuGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!plugin.vanish().isVanished(p)) {
            p.sendMessage(Msg.err("Tu dois être en vanish pour ouvrir le menu."));
            return;
        }
        render(p);
        p.openInventory(this.inventory);
    }

    private void render(Player p) {
        State s = plugin.vanish().state(p);
        if (s == null) return;

        if (this.inventory == null) {
            String title = "<gradient:#0f2027:#2c5364:#a8edea><bold>Menu Vanish</bold></gradient>";
            this.inventory = Bukkit.createInventory(this, 54, GUIUtil.title(title));
        }

        // Bordure
        ItemStack glass = GUIUtil.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inventory.setItem(i, glass);
        for (int i = 45; i < 54; i++) inventory.setItem(i, glass);
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, glass);
            inventory.setItem(row * 9 + 8, glass);
        }
        // Séparateurs intra-ligne pour aérer
        inventory.setItem(17, glass); inventory.setItem(26, glass);
        inventory.setItem(27, glass); inventory.setItem(35, glass);
        inventory.setItem(36, glass); inventory.setItem(44, glass);
        inventory.setItem(38, glass); inventory.setItem(40, glass); inventory.setItem(42, glass);
        inventory.setItem(29, glass); inventory.setItem(31, glass); inventory.setItem(33, glass);
        inventory.setItem(18, glass);
        inventory.setItem(9, glass);

        // Profil
        inventory.setItem(SLOT_PROFILE, profile(p, s));

        // Toggles ligne 1
        inventory.setItem(SLOT_LEVEL, levelItem(s));
        inventory.setItem(SLOT_SEE_OTHERS, toggle(Material.ENDER_EYE,
                "Voir les autres vanish", s.seeOthers,
                "Affiche ou cache les autres staff vanishés."));
        inventory.setItem(SLOT_NV, toggle(Material.GOLDEN_CARROT,
                "Vision nocturne", s.nightVision,
                "Effet permanent de night vision pendant le vanish."));
        inventory.setItem(SLOT_NO_FALL, toggle(Material.FEATHER,
                "Pas de dégâts de chute", s.noFall,
                "Annule uniquement les dégâts de chute."));
        inventory.setItem(SLOT_GOD, toggle(Material.GOLDEN_APPLE,
                "God-mode", s.noDamage,
                "Annule TOUS les dégâts subis."));
        inventory.setItem(SLOT_NO_PICKUP, toggle(Material.HOPPER,
                "Bloque le pickup", s.noPickup,
                "Empêche de ramasser les items au sol."));
        inventory.setItem(SLOT_NO_DROP, toggle(Material.DROPPER,
                "Bloque les drops", s.noDrop,
                "Empêche de jeter des items (touche Q)."));

        // Toggles ligne 2
        inventory.setItem(SLOT_NO_TARGET, toggle(Material.SKELETON_SKULL,
                "Pas de ciblage mob", s.noTarget,
                "Les mobs ignorent totalement le joueur."));
        inventory.setItem(SLOT_NO_HUNGER, toggle(Material.BREAD,
                "Pas de faim", s.noHunger,
                "La barre de faim ne diminue plus."));
        inventory.setItem(SLOT_NO_BLOCKS, toggle(Material.IRON_PICKAXE,
                "Bloquer break/place", s.noBlockBreak && s.noBlockPlace,
                "Empêche de casser ET poser des blocs."));
        inventory.setItem(SLOT_FLY, toggle(Material.FEATHER,
                "Vol", s.fly,
                "Active le vol même hors créatif."));
        inventory.setItem(SLOT_SPEED, speedItem(s));
        inventory.setItem(SLOT_HOTBAR_SWAP, toggle(Material.CHEST,
                "Hotbar de service", s.hotbarSwap,
                "Remplace le hotbar par les outils vanish.",
                "Le vrai hotbar est restauré à la sortie."));
        inventory.setItem(SLOT_INFO_DUMP, GUIUtil.item(Material.PAPER,
                "<white><bold>État courant</bold></white>",
                "<gray>Visualise tous les paramètres actifs.</gray>",
                "",
                "<yellow>▶ Clic pour afficher le résumé</yellow>"));

        // Actions ligne 3
        inventory.setItem(SLOT_TP_PLAYER, GUIUtil.item(Material.PLAYER_HEAD,
                "<gold><bold>Téléport vers un joueur</bold></gold>",
                "<gray>Ouvre la liste des joueurs en ligne.</gray>",
                "",
                "<yellow>▶ Clic pour ouvrir</yellow>"));
        inventory.setItem(SLOT_TP_SPAWN, GUIUtil.item(Material.RECOVERY_COMPASS,
                "<aqua><bold>Téléport au spawn</bold></aqua>",
                "<gray>Te téléporte au spawn principal.</gray>",
                "",
                "<yellow>▶ Clic pour téléporter</yellow>"));
        inventory.setItem(SLOT_REFRESH, GUIUtil.item(Material.CLOCK,
                "<yellow><bold>Rafraîchir</bold></yellow>",
                "<gray>Re-applique la visibilité et les effets.</gray>",
                "",
                "<yellow>▶ Clic pour rafraîchir</yellow>"));
        inventory.setItem(SLOT_DUMP_LIST, GUIUtil.item(Material.WRITABLE_BOOK,
                "<white><bold>Liste des vanish</bold></white>",
                "<gray>Affiche les staff vanishés actuellement.</gray>",
                "",
                "<yellow>▶ Clic pour la liste</yellow>"));

        // Presets ligne 4
        inventory.setItem(SLOT_PRESET_STEALTH, GUIUtil.item(Material.ENDER_PEARL,
                "<dark_purple><bold>Preset : Stealth</bold></dark_purple>",
                "<gray>Super-vanish, tout off pour les autres.</gray>",
                "<gray>Idéal pour observer sans être détecté.</gray>",
                "",
                "<yellow>▶ Clic pour appliquer</yellow>"));
        inventory.setItem(SLOT_PRESET_INVESTIGATOR, GUIUtil.item(Material.SPYGLASS,
                "<aqua><bold>Preset : Investigator</bold></aqua>",
                "<gray>Vanish normal, NV, fly, vitesse 2.</gray>",
                "<gray>Pour enquêter rapidement.</gray>",
                "",
                "<yellow>▶ Clic pour appliquer</yellow>"));
        inventory.setItem(SLOT_PRESET_PATROL, GUIUtil.item(Material.LEATHER_BOOTS,
                "<white><bold>Preset : Patrol</bold></white>",
                "<gray>Vanish normal, sans fly ni speed.</gray>",
                "<gray>Pour patrouiller à pied discrètement.</gray>",
                "",
                "<yellow>▶ Clic pour appliquer</yellow>"));
        inventory.setItem(SLOT_PRESET_BUILD, GUIUtil.item(Material.GOLDEN_PICKAXE,
                "<gold><bold>Preset : Build</bold></gold>",
                "<gray>Vanish normal, blocs/pickup/drop autorisés.</gray>",
                "<gray>Pour construire en mode invisible.</gray>",
                "",
                "<yellow>▶ Clic pour appliquer</yellow>"));

        // Bottom controls
        inventory.setItem(SLOT_CLOSE, GUIUtil.item(Material.ARROW,
                "<gray><bold>Fermer</bold></gray>",
                "",
                "<yellow>▶ Clic pour fermer le menu</yellow>"));
        inventory.setItem(SLOT_EXIT_VANISH, GUIUtil.item(Material.BARRIER,
                "<red><bold>Quitter le vanish</bold></red>",
                "<gray>Restaure ton inventaire et redeviens visible.</gray>",
                "",
                "<red>▶ Clic pour désactiver</red>"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        VanishManager v = plugin.vanish();
        if (!v.isVanished(p)) {
            p.closeInventory();
            return;
        }

        switch (slot) {
            case SLOT_LEVEL       -> v.cycleLevel(p);
            case SLOT_SEE_OTHERS  -> v.toggleSeeOthers(p);
            case SLOT_NV          -> v.toggleNightVision(p);
            case SLOT_NO_FALL     -> v.toggleNoFall(p);
            case SLOT_GOD         -> v.toggleGod(p);
            case SLOT_NO_PICKUP   -> v.togglePickup(p);
            case SLOT_NO_DROP     -> v.toggleNoDrop(p);
            case SLOT_NO_TARGET   -> v.toggleNoTarget(p);
            case SLOT_NO_HUNGER   -> v.toggleNoHunger(p);
            case SLOT_NO_BLOCKS   -> v.toggleNoBreakPlace(p);
            case SLOT_FLY         -> v.toggleFly(p);
            case SLOT_SPEED       -> v.cycleSpeed(p);
            case SLOT_HOTBAR_SWAP -> v.toggleHotbarSwap(p);

            case SLOT_INFO_DUMP   -> dumpStateToChat(p);
            case SLOT_DUMP_LIST   -> listVanished(p);
            case SLOT_TP_PLAYER   -> {
                p.closeInventory();
                new VanishPickerGUI(plugin).open(p);
                return;
            }
            case SLOT_TP_SPAWN    -> {
                p.closeInventory();
                if (plugin.spawns() != null) {
                    var loc = plugin.isLobby() ? plugin.spawns().hub() : plugin.spawns().spawn();
                    if (loc != null) {
                        p.teleport(loc);
                        p.sendMessage(Msg.ok("<gray>Téléporté au spawn.</gray>"));
                    } else {
                        p.sendMessage(Msg.err("Aucun spawn défini."));
                    }
                }
                return;
            }
            case SLOT_REFRESH     -> {
                v.refresh(p);
                p.sendMessage(Msg.ok("<gray>Vanish rafraîchi.</gray>"));
            }

            case SLOT_PRESET_STEALTH      -> v.applyPreset(p, "stealth");
            case SLOT_PRESET_INVESTIGATOR -> v.applyPreset(p, "investigator");
            case SLOT_PRESET_PATROL       -> v.applyPreset(p, "patrol");
            case SLOT_PRESET_BUILD        -> v.applyPreset(p, "build");

            case SLOT_CLOSE       -> { p.closeInventory(); return; }
            case SLOT_EXIT_VANISH -> { p.closeInventory(); v.disable(p); return; }
            default -> { return; }
        }
        render(p);
    }

    private ItemStack profile(Player p, State s) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(p);
        }
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic>" + s.level.color
                    + "<bold>" + p.getName() + "</bold> <dark_gray>—</dark_gray> "
                    + s.level.color + s.level.display)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(MM.deserialize("<!italic><gray>Joueurs cachés à : <yellow>"
                    + (s.level == Level.SUPER ? "tous" : "non-staff") + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MM.deserialize("<!italic><gray>Vol: <yellow>" + onOff(s.fly) + "</yellow>"
                    + " <dark_gray>·</dark_gray> God: <yellow>" + onOff(s.noDamage) + "</yellow>"
                    + " <dark_gray>·</dark_gray> NV: <yellow>" + onOff(s.nightVision) + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MM.deserialize("<!italic><gray>Speed tier: <yellow>" + s.speedTier + "</yellow>"
                    + " <dark_gray>·</dark_gray> Hotbar swap: <yellow>" + onOff(s.hotbarSwap) + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MM.deserialize("<!italic><dark_gray>Cliquer une option = toggle direct.</dark_gray>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack levelItem(State s) {
        Material mat = s.level == Level.SUPER ? Material.AMETHYST_SHARD : Material.PRISMARINE_SHARD;
        return GUIUtil.item(mat,
                "<bold>Niveau de vanish:</bold> " + s.level.color + s.level.display
                        + s.level.color.replace("<", "</"),
                "",
                "<gray>Normal — caché aux non-staff.</gray>",
                "<gray>Super  — caché à <red>tout le monde</red>, staff inclus.</gray>",
                "",
                "<yellow>▶ Clic pour cycler</yellow>");
    }

    private ItemStack speedItem(State s) {
        return GUIUtil.item(Material.SUGAR,
                "<yellow><bold>Vitesse:</bold></yellow> <white>tier " + s.speedTier + "</white>",
                "",
                "<gray>0 = vanilla, 3 = boost max.</gray>",
                "",
                "<yellow>▶ Clic pour cycler</yellow>");
    }

    private ItemStack toggle(Material m, String name, boolean enabled, String... description) {
        Material display = enabled ? m : Material.GRAY_DYE;
        String header = (enabled ? "<green>" : "<red>") + "<bold>" + name + "</bold> "
                + "<dark_gray>•</dark_gray> "
                + (enabled ? "<green>ON</green>" : "<red>OFF</red>");
        List<String> lines = new ArrayList<>();
        for (String d : description) {
            lines.add("<gray>" + d + "</gray>");
        }
        lines.add("");
        lines.add("<yellow>▶ Clic pour toggler</yellow>");
        return GUIUtil.item(display, header, lines.toArray(new String[0]));
    }

    private void dumpStateToChat(Player p) {
        p.sendMessage(Msg.info("<gray>État vanish :</gray>"));
        for (String line : plugin.vanish().describe(p)) {
            p.sendMessage(Msg.mm("<dark_gray>›</dark_gray> <gray>" + line + "</gray>"));
        }
    }

    private void listVanished(Player p) {
        var ids = plugin.vanish().all();
        if (ids.isEmpty()) {
            p.sendMessage(Msg.info("<gray>Aucun staff vanishé.</gray>"));
            return;
        }
        p.sendMessage(Msg.info("<gray>Staff vanishés (" + ids.size() + ") :</gray>"));
        for (var id : ids) {
            Player v = Bukkit.getPlayer(id);
            String name = v != null ? v.getName() : id.toString();
            String level = v != null && plugin.vanish().isSuperVanished(v) ? "Super" : "Normal";
            p.sendMessage(Msg.mm("<dark_gray>›</dark_gray> <yellow>" + name
                    + "</yellow> <dark_gray>(" + level + ")</dark_gray>"));
        }
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }
}
