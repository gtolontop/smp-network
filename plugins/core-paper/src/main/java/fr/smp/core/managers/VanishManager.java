package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanish complet : visibilité multi-niveaux + toggles + hotbar de service.
 *
 * <p>Niveaux :
 * <ul>
 *     <li><b>NORMAL</b> : caché aux joueurs sans {@code smp.vanish.see}.</li>
 *     <li><b>SUPER</b>  : caché à tous, y compris au staff.</li>
 * </ul>
 *
 * <p>Quand le hotbar-swap est actif, le hotbar « réel » est sérialisé sous
 * {@code plugins/SMPCore/vanish/&lt;uuid&gt;.yml}, ce qui permet de récupérer
 * proprement le stuff après un crash : à la connexion suivante, si un fichier
 * est trouvé, le hotbar est restauré et le fichier supprimé.
 */
public class VanishManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum Level {
        NORMAL("Normal", "<aqua>"),
        SUPER("Super", "<light_purple>");

        public final String display;
        public final String color;
        Level(String d, String c) { this.display = d; this.color = c; }
    }

    /** Tous les paramètres ajustables d'une session de vanish. */
    public static final class State {
        public Level level = Level.NORMAL;
        public boolean nightVision = true;
        public boolean noFall = true;
        public boolean noDamage = true;
        public boolean noPickup = true;
        public boolean noDrop = true;
        public boolean noTarget = true;
        public boolean noHunger = true;
        public boolean noBlockBreak = true;
        public boolean noBlockPlace = true;
        public boolean fly = true;
        public boolean seeOthers = true;
        public boolean hotbarSwap = true;
        /** 0 = vanilla, 1 = +1, 2 = +3, 3 = +5. */
        public int speedTier = 1;
        /** Hotbar (9 slots) sauvegardé tant que le swap est actif. */
        public ItemStack[] savedHotbar;
        /** Offhand sauvegardé tant que le swap est actif. */
        public ItemStack savedOffhand;
        public int savedHeldSlot;
        public GameMode savedGameMode;
        public boolean savedAllowFlight;
        public boolean savedFlying;
    }

    public static final String TOOL_LEVEL    = "level";
    public static final String TOOL_PICKER   = "picker";
    public static final String TOOL_SEE      = "see";
    public static final String TOOL_MENU     = "menu";
    public static final String TOOL_FLY      = "fly";
    public static final String TOOL_NOBREAK  = "nobreak";
    public static final String TOOL_SPEED    = "speed";
    public static final String TOOL_GOD      = "god";
    public static final String TOOL_EXIT     = "exit";

    private final SMPCore plugin;
    private final NamespacedKey toolKey;
    private final File dir;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    /** UUIDs dont le sync ne doit pas marquer dirty (hotbar swap actif). */
    private final Set<UUID> syncBypass = ConcurrentHashMap.newKeySet();

    public VanishManager(SMPCore plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "vanish_tool");
        this.dir = new File(plugin.getDataFolder(), "vanish");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create vanish dir: " + dir.getAbsolutePath());
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────

    public boolean isVanished(Player p) {
        return states.containsKey(p.getUniqueId());
    }

    public boolean isSuperVanished(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && s.level == Level.SUPER;
    }

    public boolean isHotbarSwapped(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && s.savedHotbar != null;
    }

    public boolean shouldSkipSync(Player p) {
        return syncBypass.contains(p.getUniqueId());
    }

    public State state(Player p) {
        return states.get(p.getUniqueId());
    }

    public Set<UUID> all() {
        return new HashSet<>(states.keySet());
    }

    /** Compat avec l'ancien code : alias de {@link State#noPickup}. */
    public boolean isPickupEnabled(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && !s.noPickup;
    }

    /** Compat : inverse {@link State#noPickup}. Renvoie true si pickup activé. */
    public boolean togglePickup(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return false;
        s.noPickup = !s.noPickup;
        return !s.noPickup;
    }

    /** Toggle vanish on/off. Renvoie true si activé. */
    public boolean toggle(Player p) {
        if (isVanished(p)) { disable(p); return false; }
        enable(p, Level.NORMAL); return true;
    }

    public void enable(Player p, Level level) {
        boolean isNew = !states.containsKey(p.getUniqueId());
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        if (isNew) {
            // On capture l'état pré-vanish AVANT d'appliquer quoi que ce soit
            // pour pouvoir le restaurer fidèlement à la désactivation.
            s.savedGameMode = p.getGameMode();
            s.savedAllowFlight = p.getAllowFlight();
            s.savedFlying = p.isFlying();
        }
        s.level = level;
        applyVisibility(p, s);
        applyFlight(p, s);
        applySpeed(p, s);
        applyNightVision(p, s);
        if (s.hotbarSwap && s.savedHotbar == null) {
            swapHotbarIn(p, s);
        }
        broadcastJoinQuit(p, s, false);
        String closeTag = "</" + s.level.color.substring(1);
        p.sendMessage(Msg.ok("<gray>Vanish " + s.level.color + s.level.display + closeTag
                + " <green>activé</green>."));
        p.sendMessage(Msg.info("<gray>Ouvre le menu : <white>/vanish menu</white>"));
    }

    public void disable(Player p) {
        State s = states.remove(p.getUniqueId());
        if (s == null) return;
        if (s.savedHotbar != null) {
            swapHotbarOut(p, s);
        }
        // Restore visibility
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, p);
            // restaure aussi la visibilité des autres vanish vers ce joueur
            if (states.containsKey(other.getUniqueId())) {
                applyVisibility(other, states.get(other.getUniqueId()));
            }
        }
        // Restaure les effets
        clearNightVision(p);
        if (s.savedGameMode != null) {
            p.setGameMode(s.savedGameMode);
        }
        p.setAllowFlight(s.savedAllowFlight);
        p.setFlying(s.savedFlying);
        p.setCollidable(true);
        p.setWalkSpeed(0.2f);
        p.setFlySpeed(0.1f);

        broadcastJoinQuit(p, null, true);
        p.sendMessage(Msg.ok("<gray>Vanish <red>désactivé</red>."));
    }

    public void setLevel(Player p, Level level) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.level = level;
        applyVisibility(p, s);
        // Re-eval visibilité pour tous les autres vanishés vers ce joueur (et inversement)
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            State os = states.get(other.getUniqueId());
            if (os != null) applyVisibility(other, os);
        }
        p.sendMessage(Msg.ok("<gray>Niveau de vanish : " + s.level.color + "<bold>"
                + s.level.display + "</bold>" + s.level.color.replace("<", "</")));
    }

    public void cycleLevel(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        Level next = s.level == Level.NORMAL ? Level.SUPER : Level.NORMAL;
        setLevel(p, next);
    }

    /** Re-applique tout le state au joueur (sans toucher aux toggles). */
    public void refresh(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        applyVisibility(p, s);
        applyFlight(p, s);
        applySpeed(p, s);
        applyNightVision(p, s);
        if (s.hotbarSwap) applyVanishHotbar(p);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            State os = states.get(other.getUniqueId());
            if (os != null) applyVisibilityFor(p, other, os);
        }
    }

    public void cycleSpeed(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.speedTier = (s.speedTier + 1) % 4;
        applySpeed(p, s);
        p.sendMessage(Msg.ok("<gray>Vitesse : <yellow>tier " + s.speedTier + "</yellow></gray>"));
    }

    public void toggleSeeOthers(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.seeOthers = !s.seeOthers;
        // Re-eval visibility de chaque autre vanishé pour ce joueur
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            if (states.containsKey(other.getUniqueId())) {
                State os = states.get(other.getUniqueId());
                applyVisibilityFor(p, other, os);
            }
        }
        p.sendMessage(Msg.ok("<gray>Voir les autres vanish : "
                + (s.seeOthers ? "<green>activé" : "<red>désactivé") + "</gray>"));
    }

    public void toggleFly(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.fly = !s.fly;
        applyFlight(p, s);
        p.sendMessage(Msg.ok("<gray>Vol : "
                + (s.fly ? "<green>activé" : "<red>désactivé") + "</gray>"));
    }

    public void toggleGod(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.noDamage = !s.noDamage;
        p.sendMessage(Msg.ok("<gray>God-mode : "
                + (s.noDamage ? "<green>activé" : "<red>désactivé") + "</gray>"));
    }

    public void toggleNoBreakPlace(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        boolean cur = s.noBlockBreak && s.noBlockPlace;
        s.noBlockBreak = !cur;
        s.noBlockPlace = !cur;
        p.sendMessage(Msg.ok("<gray>Interactions blocs : "
                + (cur ? "<green>autorisées" : "<red>bloquées") + "</gray>"));
    }

    public void toggleNightVision(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.nightVision = !s.nightVision;
        applyNightVision(p, s);
        p.sendMessage(Msg.ok("<gray>Vision nocturne : "
                + (s.nightVision ? "<green>activée" : "<red>désactivée") + "</gray>"));
    }

    public void toggleNoFall(Player p) { State s = states.get(p.getUniqueId()); if (s != null) s.noFall = !s.noFall; }
    public void toggleNoDrop(Player p) { State s = states.get(p.getUniqueId()); if (s != null) s.noDrop = !s.noDrop; }
    public void toggleNoTarget(Player p) { State s = states.get(p.getUniqueId()); if (s != null) s.noTarget = !s.noTarget; }
    public void toggleNoHunger(Player p) { State s = states.get(p.getUniqueId()); if (s != null) s.noHunger = !s.noHunger; }

    public void toggleHotbarSwap(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        s.hotbarSwap = !s.hotbarSwap;
        if (s.hotbarSwap && s.savedHotbar == null) swapHotbarIn(p, s);
        else if (!s.hotbarSwap && s.savedHotbar != null) swapHotbarOut(p, s);
        p.sendMessage(Msg.ok("<gray>Hotbar de service : "
                + (s.hotbarSwap ? "<green>activée" : "<red>désactivée") + "</gray>"));
    }

    public void applyPreset(Player p, String preset) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        switch (preset.toLowerCase()) {
            case "stealth" -> {
                s.level = Level.SUPER;
                s.nightVision = true; s.noFall = true; s.noDamage = true; s.noPickup = true;
                s.noDrop = true; s.noTarget = true; s.noHunger = true;
                s.noBlockBreak = true; s.noBlockPlace = true; s.fly = true; s.speedTier = 0;
                s.seeOthers = false;
            }
            case "investigator" -> {
                s.level = Level.NORMAL;
                s.nightVision = true; s.noFall = true; s.noDamage = true; s.noPickup = true;
                s.noDrop = true; s.noTarget = true; s.noHunger = true;
                s.noBlockBreak = true; s.noBlockPlace = true; s.fly = true; s.speedTier = 2;
                s.seeOthers = true;
            }
            case "patrol" -> {
                s.level = Level.NORMAL;
                s.nightVision = false; s.noFall = true; s.noDamage = true; s.noPickup = true;
                s.noDrop = true; s.noTarget = true; s.noHunger = true;
                s.noBlockBreak = true; s.noBlockPlace = true; s.fly = false; s.speedTier = 0;
                s.seeOthers = true;
            }
            case "build" -> {
                s.level = Level.NORMAL;
                s.nightVision = true; s.noFall = true; s.noDamage = true; s.noPickup = false;
                s.noDrop = false; s.noTarget = true; s.noHunger = true;
                s.noBlockBreak = false; s.noBlockPlace = false; s.fly = true; s.speedTier = 1;
                s.seeOthers = true;
            }
            default -> { return; }
        }
        applyVisibility(p, s);
        applyFlight(p, s);
        applySpeed(p, s);
        applyNightVision(p, s);
        // refresh visibilité d'autrui en cas de changement de level/seeOthers
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            State os = states.get(other.getUniqueId());
            if (os != null) {
                applyVisibility(other, os);
                applyVisibilityFor(p, other, os);
            }
        }
        p.sendMessage(Msg.ok("<gray>Preset <yellow>" + preset + "</yellow> appliqué.</gray>"));
    }

    // ─── Visibility ─────────────────────────────────────────────────────

    private void applyVisibility(Player p, State s) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            applyVisibilityFor(other, p, s);
        }
    }

    /**
     * Pour le viewer, applique la visibilité du target en vanish (state s).
     * Encapsule les règles : SUPER cache à tous, NORMAL cache aux non-staff,
     * et un staff peut ne pas voir les autres vanish via seeOthers.
     */
    private void applyVisibilityFor(Player viewer, Player target, State s) {
        boolean canSee;
        if (s.level == Level.SUPER) {
            canSee = false;
        } else {
            canSee = viewer.hasPermission("smp.vanish.see");
        }
        if (canSee) {
            // si le viewer est lui-même vanish et a désactivé seeOthers → masquer
            State viewerState = states.get(viewer.getUniqueId());
            if (viewerState != null && !viewerState.seeOthers) canSee = false;
        }
        if (canSee) viewer.showPlayer(plugin, target);
        else viewer.hidePlayer(plugin, target);
    }

    // ─── Effets ─────────────────────────────────────────────────────────

    private void applyFlight(Player p, State s) {
        if (s.fly) {
            p.setAllowFlight(true);
            p.setFlying(true);
        } else {
            // Garde le fly seulement si le gamemode l'autorise nativement
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
        p.setCollidable(false);
    }

    private void applySpeed(Player p, State s) {
        float walk = 0.2f;
        float fly = 0.1f;
        switch (s.speedTier) {
            case 1 -> { walk = 0.3f; fly = 0.2f; }
            case 2 -> { walk = 0.5f; fly = 0.4f; }
            case 3 -> { walk = 0.8f; fly = 0.7f; }
            default -> {}
        }
        p.setWalkSpeed(walk);
        p.setFlySpeed(Math.min(fly, 1f));
    }

    private static final PotionEffect NIGHT_VISION =
            new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false);

    private void applyNightVision(Player p, State s) {
        if (s.nightVision) {
            p.addPotionEffect(NIGHT_VISION);
        } else {
            clearNightVision(p);
        }
    }

    private void clearNightVision(Player p) {
        // ne supprime que si l'effet ressemble au notre (durée infinie + pas de particules)
        PotionEffect cur = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (cur != null && cur.getDuration() > 1_000_000_000 && !cur.hasParticles()) {
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    private void broadcastJoinQuit(Player p, State s, boolean rejoin) {
        if (rejoin) {
            Component fakeJoin = Msg.mm("<yellow>" + p.getName() + " a rejoint le jeu");
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(p.getUniqueId())) continue;
                if (!other.hasPermission("smp.vanish.see")) other.sendMessage(fakeJoin);
            }
            return;
        }
        // vanish enable → fake quit aux non-staff, info aux staff
        Component fakeQuit = Msg.mm("<yellow>" + p.getName() + " a quitté le jeu");
        Component adminMsg = Msg.info("<gray>" + p.getName()
                + " est maintenant en vanish " + s.level.color + s.level.display + "</gray>");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            if (!other.hasPermission("smp.vanish.see")) {
                other.sendMessage(fakeQuit);
            } else {
                other.sendMessage(adminMsg);
            }
        }
    }

    // ─── Hotbar swap ────────────────────────────────────────────────────

    private void swapHotbarIn(Player p, State s) {
        // savedGameMode / savedAllowFlight / savedFlying sont remplis par enable()
        // AVANT que le mode vol/vitesse soit modifié, pour préserver l'état réel.
        PlayerInventory inv = p.getInventory();
        ItemStack[] saved = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            saved[i] = it == null ? null : it.clone();
        }
        s.savedHotbar = saved;
        s.savedOffhand = inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone();
        s.savedHeldSlot = inv.getHeldItemSlot();

        persistSnapshot(p, s);
        syncBypass.add(p.getUniqueId());

        applyVanishHotbar(p);
        inv.setItemInOffHand(null);
        inv.setHeldItemSlot(4); // slot du menu (nether star)
    }

    private void swapHotbarOut(Player p, State s) {
        PlayerInventory inv = p.getInventory();
        if (s.savedHotbar != null) {
            for (int i = 0; i < 9; i++) inv.setItem(i, s.savedHotbar[i]);
            inv.setItemInOffHand(s.savedOffhand);
            inv.setHeldItemSlot(Math.max(0, Math.min(8, s.savedHeldSlot)));
        }
        s.savedHotbar = null;
        s.savedOffhand = null;
        deleteSnapshot(p.getUniqueId());
        syncBypass.remove(p.getUniqueId());
        if (plugin.getSyncManager() != null) plugin.getSyncManager().markDirty(p);
    }

    private void applyVanishHotbar(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, makeTool(Material.COMPASS, TOOL_LEVEL,
                "<aqua><bold>Niveau de vanish</bold></aqua>",
                "<gray>Cycle entre <aqua>Normal</aqua> et <light_purple>Super</light_purple>.</gray>",
                "",
                "<yellow>▶ Clic-droit pour cycler</yellow>"));
        inv.setItem(1, makeTool(Material.PLAYER_HEAD, TOOL_PICKER,
                "<gold><bold>Téléport joueur</bold></gold>",
                "<gray>Ouvre la liste des joueurs en ligne.</gray>",
                "",
                "<yellow>▶ Clic pour ouvrir</yellow>"));
        inv.setItem(2, makeTool(Material.ENDER_EYE, TOOL_SEE,
                "<dark_aqua><bold>Voir les autres vanish</bold></dark_aqua>",
                "<gray>Toggle l'affichage des collègues vanish.</gray>",
                "",
                "<yellow>▶ Clic pour toggler</yellow>"));
        inv.setItem(3, makeTool(Material.SPECTRAL_ARROW, TOOL_FLY,
                "<white><bold>Vol</bold></white>",
                "<gray>Active ou désactive le mode vol.</gray>",
                "",
                "<yellow>▶ Clic pour toggler</yellow>"));
        inv.setItem(4, makeTool(Material.NETHER_STAR, TOOL_MENU,
                "<gradient:#a8edea:#fed6e3><bold>Menu Vanish</bold></gradient>",
                "<gray>Ouvre le panel complet.</gray>",
                "",
                "<yellow>▶ Clic pour ouvrir</yellow>"));
        inv.setItem(5, makeTool(Material.IRON_PICKAXE, TOOL_NOBREAK,
                "<gray><bold>Bloc casser/poser</bold></gray>",
                "<gray>Toggle l'autorisation de modifier les blocs.</gray>",
                "",
                "<yellow>▶ Clic pour toggler</yellow>"));
        inv.setItem(6, makeTool(Material.SUGAR, TOOL_SPEED,
                "<yellow><bold>Vitesse</bold></yellow>",
                "<gray>Cycle 0 → 1 → 2 → 3.</gray>",
                "",
                "<yellow>▶ Clic pour cycler</yellow>"));
        inv.setItem(7, makeTool(Material.GOLDEN_APPLE, TOOL_GOD,
                "<gold><bold>God-mode</bold></gold>",
                "<gray>Toggle l'invincibilité totale.</gray>",
                "",
                "<yellow>▶ Clic pour toggler</yellow>"));
        inv.setItem(8, makeTool(Material.BARRIER, TOOL_EXIT,
                "<red><bold>Quitter le vanish</bold></red>",
                "<gray>Restaure ton inventaire et redeviens visible.</gray>",
                "",
                "<red>▶ Clic pour quitter</red>"));
    }

    private ItemStack makeTool(Material m, String id, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic>" + name)
                    .decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                List<Component> components = new ArrayList<>();
                for (String l : lore) {
                    components.add(MM.deserialize("<!italic>" + l)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(components);
            }
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, id);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Lit l'ID outil d'un item (ou null). */
    public String toolId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(toolKey, PersistentDataType.STRING);
    }

    private File snapshotFile(UUID id) { return new File(dir, id.toString() + ".yml"); }

    private void persistSnapshot(Player p, State s) {
        if (s.savedHotbar == null) return;
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("name", p.getName());
        cfg.set("hotbar", s.savedHotbar);
        cfg.set("offhand", s.savedOffhand);
        cfg.set("heldSlot", s.savedHeldSlot);
        cfg.set("gameMode", s.savedGameMode != null ? s.savedGameMode.name() : "SURVIVAL");
        cfg.set("allowFlight", s.savedAllowFlight);
        cfg.set("flying", s.savedFlying);
        try {
            cfg.save(snapshotFile(p.getUniqueId()));
        } catch (IOException e) {
            plugin.getLogger().warning("Vanish snapshot save failed: " + e.getMessage());
        }
    }

    private void deleteSnapshot(UUID id) {
        File f = snapshotFile(id);
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Could not delete vanish snapshot: " + f.getAbsolutePath());
        }
    }

    /** Restaure un hotbar orphelin (crash / disconnect) et retourne true si une restauration a eu lieu. */
    private boolean restoreOrphanSnapshot(Player p) {
        File f = snapshotFile(p.getUniqueId());
        if (!f.exists()) {
            // Pas de snapshot mais peut-être un bypass résiduel d'un précédent run.
            syncBypass.remove(p.getUniqueId());
            return false;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        @SuppressWarnings("unchecked")
        List<ItemStack> hotbar = (List<ItemStack>) cfg.getList("hotbar");
        ItemStack offhand = cfg.getItemStack("offhand");
        int heldSlot = cfg.getInt("heldSlot", 0);
        if (hotbar != null) {
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < 9 && i < hotbar.size(); i++) inv.setItem(i, hotbar.get(i));
            inv.setItemInOffHand(offhand);
            inv.setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));
        }
        try {
            GameMode gm = GameMode.valueOf(cfg.getString("gameMode", "SURVIVAL"));
            p.setGameMode(gm);
        } catch (IllegalArgumentException ignored) {}
        p.setAllowFlight(cfg.getBoolean("allowFlight", false));
        p.setFlying(cfg.getBoolean("flying", false));
        deleteSnapshot(p.getUniqueId());
        syncBypass.remove(p.getUniqueId());
        if (plugin.getSyncManager() != null) plugin.getSyncManager().markDirty(p);
        plugin.getLogger().info("Restored orphan vanish hotbar for " + p.getName());
        return true;
    }

    // ─── Listeners ──────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        // Tout joueur qui se reconnecte avec un snapshot orphelin (crash) → on
        // restaure son hotbar APRÈS que SyncListener ait chargé l'inventaire
        // synchronisé (qui s'exécute à tick+1). On vise donc tick+2.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joined.isOnline()) restoreOrphanSnapshot(joined);
        }, 2L);

        // Cache les vanish déjà en ligne au nouveau venu
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            Player v = Bukkit.getPlayer(entry.getKey());
            if (v == null || v.getUniqueId().equals(joined.getUniqueId())) continue;
            applyVisibilityFor(joined, v, entry.getValue());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        // Le snapshot reste sur disque pour le re-join. On supprime juste l'état mémoire.
        // Mais on retire d'abord le bypass sync : le hotbar visible n'est PAS le sien,
        // donc on ne veut SURTOUT pas que le hotbar de service soit synchronisé.
        // → On laisse le bypass actif jusqu'au join suivant ; il sera nettoyé par
        //   restoreOrphanSnapshot.
        states.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && s.noFall) {
            event.setCancelled(true);
            return;
        }
        if (s.noDamage) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p) {
            State s = states.get(p.getUniqueId());
            if (s != null && s.noTarget) event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p) {
            State s = states.get(p.getUniqueId());
            if (s != null && s.noHunger) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        State s = states.get(event.getPlayer().getUniqueId());
        if (s == null) return;
        // Tant qu'on a un hotbar swap actif, on bloque tout drop : sinon le
        // joueur jetterait une pioche en fer dont le tag PDC l'identifie comme
        // outil de vanish, ce qui tague des items rotables d'IDs internes.
        if (s.savedHotbar != null || s.noDrop) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) {
            State s = states.get(p.getUniqueId());
            if (s != null && s.noPickup) event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        State s = states.get(event.getPlayer().getUniqueId());
        if (s != null && s.noBlockBreak) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        State s = states.get(event.getPlayer().getUniqueId());
        if (s != null && s.noBlockPlace) event.setCancelled(true);
    }

    /** Empêche l'utilisateur de mettre les outils de vanish en offhand (F). */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        State s = states.get(event.getPlayer().getUniqueId());
        if (s == null || s.savedHotbar == null) return;
        ItemStack main = event.getMainHandItem();
        ItemStack off = event.getOffHandItem();
        if (toolId(main) != null || toolId(off) != null) event.setCancelled(true);
    }

    /** Empêche de bouger les outils de vanish via inventaire. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        State s = states.get(p.getUniqueId());
        if (s == null || s.savedHotbar == null) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (toolId(current) != null || toolId(cursor) != null) {
            event.setCancelled(true);
        }
    }

    /** Dispatch des clics sur les outils de vanish dans la hotbar. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        State s = states.get(p.getUniqueId());
        if (s == null) return;

        ItemStack hand = event.getItem();
        if (hand == null) return;
        String id = toolId(hand);
        if (id == null) return;

        // Toute interaction via outil vanish = cancel pour éviter pose/usage
        event.setCancelled(true);

        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;

        switch (id) {
            case TOOL_LEVEL  -> cycleLevel(p);
            case TOOL_SEE    -> toggleSeeOthers(p);
            case TOOL_FLY    -> toggleFly(p);
            case TOOL_NOBREAK-> toggleNoBreakPlace(p);
            case TOOL_SPEED  -> cycleSpeed(p);
            case TOOL_GOD    -> toggleGod(p);
            case TOOL_EXIT   -> disable(p);
            case TOOL_MENU   -> {
                fr.smp.core.gui.VanishMenuGUI gui = new fr.smp.core.gui.VanishMenuGUI(plugin);
                gui.open(p);
            }
            case TOOL_PICKER -> {
                fr.smp.core.gui.VanishPickerGUI gui = new fr.smp.core.gui.VanishPickerGUI(plugin);
                gui.open(p);
            }
            default -> {}
        }
    }

    /**
     * À la sortie de vanish, on a déjà restauré la hotbar via {@link #disable(Player)}.
     * Cette méthode garantit que l'état du joueur revient à un état sain même si
     * la déconnexion a interrompu la séquence (kick / crash).
     */
    public void emergencyRestoreOnShutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null) continue;
            if (s.savedHotbar != null) {
                // On laisse le snapshot sur disque ; il sera consommé au prochain join.
                plugin.getLogger().info("Vanish snapshot kept on disk for " + p.getName());
            }
        }
    }

    /** Pour le menu : un dump lisible du state. */
    public List<String> describe(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return Collections.singletonList("Pas en vanish.");
        List<String> out = new ArrayList<>();
        out.add("Niveau: " + s.level.display);
        out.add("Vol: " + bool(s.fly));
        out.add("God: " + bool(s.noDamage));
        out.add("No-fall: " + bool(s.noFall));
        out.add("No-pickup: " + bool(s.noPickup));
        out.add("No-drop: " + bool(s.noDrop));
        out.add("No-target: " + bool(s.noTarget));
        out.add("No-hunger: " + bool(s.noHunger));
        out.add("Bloc break: " + bool(!s.noBlockBreak));
        out.add("Bloc place: " + bool(!s.noBlockPlace));
        out.add("Vision nocturne: " + bool(s.nightVision));
        out.add("Voir autres vanish: " + bool(s.seeOthers));
        out.add("Hotbar swap: " + bool(s.hotbarSwap));
        out.add("Speed tier: " + s.speedTier);
        return out;
    }

    private static String bool(boolean b) { return b ? "ON" : "OFF"; }
}
