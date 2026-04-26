package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.SpawnerGUI;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.SpawnerManager;
import fr.smp.core.managers.SpawnerType;
import fr.smp.core.utils.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class SpawnerListener implements Listener {

    private final SMPCore plugin;

    public SpawnerListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        SpawnerManager mgr = plugin.spawners();
        if (mgr == null) return;
        if (hand == null || hand.getType() != Material.SPAWNER) return;

        Player p = event.getPlayer();
        Block placed = event.getBlockPlaced();
        Location loc = placed.getLocation();

        SpawnerType type = mgr.readType(hand);
        int perItemStack = mgr.readStack(hand);

        // Items SPAWNER vanilla (sans PDC) → interdits en survival pour éviter
        // qu'un joueur pose un spawner natif non-tracké. Admins autorisés.
        if (type == null) {
            if (!p.hasPermission("smp.admin")) {
                event.setCancelled(true);
                p.sendMessage(Msg.err("Les spawners vanilla sont désactivés. Utilise un spawner custom."));
            }
            return;
        }

        // Sneak-place = bulk: consomme jusqu'à 64 items identiques de la main
        // pour poser un seul spawner avec un gros stack.
        int consume = 1;
        if (p.isSneaking() && p.getGameMode() != GameMode.CREATIVE
                && hand.getAmount() > 1) {
            consume = Math.min(hand.getAmount(), 64);
        }
        int totalStack = (int) Math.min(SpawnerManager.MAX_STACK,
                (long) perItemStack * consume);

        boolean merged = mgr.placeOrMerge(loc, type, totalStack);
        if (merged) {
            event.setCancelled(true);
            if (p.getGameMode() != GameMode.CREATIVE) {
                consumeFromHand(p, event.getHand(), consume);
            }
            // Le merge a pu s'appliquer au bloc du dessous OU du dessus
            SpawnerManager.Spawner merged2 = mgr.at(loc.clone().subtract(0, 1, 0));
            if (merged2 == null) merged2 = mgr.at(loc.clone().add(0, 1, 0));
            int newStack = merged2 != null ? merged2.stack : totalStack;
            p.sendMessage(Msg.ok("<green>Spawner fusionné (+<yellow>×" + totalStack
                    + "</yellow>). Stack: <yellow>×" + newStack + "</yellow>.</green>"));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            plugin.logs().log(LogCategory.SPAWNER, p,
                    "spawner merge type=" + type.name() + " +" + totalStack + " at " + coords(loc));
            return;
        }

        // Pose simple (pas de fusion). Minecraft ne consomme qu'un item lui-même;
        // on retire les extras pour que le bulk reflète le stack total posé.
        if (consume > 1 && p.getGameMode() != GameMode.CREATIVE) {
            consumeFromHand(p, event.getHand(), consume - 1);
        }
        p.sendMessage(Msg.ok("<green>Spawner " + type.colorTag() + type.display()
                + "<green> posé (stack <yellow>×" + totalStack + "</yellow>).</green>"));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.3f);
        plugin.logs().log(LogCategory.SPAWNER, p,
                "spawner place type=" + type.name() + " stack=" + totalStack + " at " + coords(loc));
    }

    private static void consumeFromHand(Player p, EquipmentSlot slot, int amount) {
        ItemStack current = (slot == EquipmentSlot.OFF_HAND)
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
        if (current == null) return;
        int remaining = current.getAmount() - amount;
        ItemStack next;
        if (remaining <= 0) {
            next = null;
        } else {
            next = current.clone();
            next.setAmount(remaining);
        }
        if (slot == EquipmentSlot.OFF_HAND) {
            p.getInventory().setItemInOffHand(next);
        } else {
            p.getInventory().setItemInMainHand(next);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerManager mgr = plugin.spawners();
        if (mgr == null) return;
        SpawnerManager.Spawner s = mgr.at(b.getLocation());
        Player p = event.getPlayer();
        if (s == null) {
            // Spawner non-tracké (donjon / vanilla). Empêche les drops XP natifs,
            // et si le joueur casse avec une pioche, lui donne un spawner
            // custom du type correspondant (récupérable).
            event.setExpToDrop(0);
            event.setDropItems(false);
            if (p.getGameMode() == GameMode.CREATIVE) return;
            ItemStack tool = p.getInventory().getItemInMainHand();
            if (tool == null || !isPickaxe(tool.getType())) return;
            EntityType et = detectSpawnerEntity(b);
            SpawnerType type = et != null ? SpawnerType.fromId(et.name()) : null;
            if (type == null) {
                p.sendMessage(Msg.err("Spawner " + (et != null ? et.name().toLowerCase() : "inconnu")
                        + " non récupérable (type non supporté)."));
                return;
            }
            ItemStack drop = mgr.makeSpawnerItem(type, 1);
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(drop);
            int droppedPickup = 0;
            Location pickupDrop = b.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack it : overflow.values()) {
                b.getWorld().dropItemNaturally(pickupDrop, it);
                droppedPickup += it.getAmount();
            }
            p.sendMessage(Msg.ok("<green>Spawner " + type.colorTag() + type.display()
                    + "<green> récupéré."
                    + (droppedPickup > 0 ? " <gray>(inventaire plein, lâché au sol)</gray>" : "")
                    + "</green>"));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.8f);
            plugin.logs().log(LogCategory.SPAWNER, p,
                    "spawner pickup type=" + type.name()
                            + " dropped=" + droppedPickup + " at " + coords(b.getLocation()));
            return;
        }
        event.setExpToDrop(0);
        event.setDropItems(false);

        Location loc = b.getLocation().add(0.5, 0.5, 0.5);

        // Shift+casse: retire 64 du stack sans détruire le bloc (si stack > 64).
        if (p.isSneaking() && s.stack > 64) {
            event.setCancelled(true);
            s.stack -= 64;
            s.markDirty();
            ItemStack chunk = mgr.makeSpawnerItem(s.type, 64);
            if (p.getGameMode() != GameMode.CREATIVE) {
                Map<Integer, ItemStack> overflow = p.getInventory().addItem(chunk);
                overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            }
            p.sendMessage(Msg.ok("<green>-64 spawners. Restant: <yellow>×"
                    + s.stack + "</yellow>.</green>"));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1f);
            plugin.logs().log(LogCategory.SPAWNER, p,
                    "spawner split type=" + s.type.name() + " -64 remain=" + s.stack
                            + " at " + coords(b.getLocation()));
            return;
        }

        // Le contenu accumulé est effacé (pas drop) — le joueur récupère juste le stack.
        s.loot.clear();

        // Donne un item spawner au joueur (avec stack PDC)
        ItemStack spawnerItem = mgr.makeSpawnerItem(s.type, s.stack);
        SpawnerType type = s.type;
        int stack = s.stack;
        mgr.remove(b.getLocation());

        int dropped = 0;
        if (p.getGameMode() != GameMode.CREATIVE) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(spawnerItem);
            for (ItemStack it : overflow.values()) {
                b.getWorld().dropItemNaturally(loc, it);
                dropped += it.getAmount();
            }
        }
        p.sendMessage(Msg.ok("<green>Spawner récupéré (<yellow>×" + stack + "</yellow>)."
                + (dropped > 0 ? " <gray>(inventaire plein, lâché au sol)</gray>" : "")
                + "</green>"));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.8f);
        plugin.logs().log(LogCategory.SPAWNER, p,
                "spawner break type=" + type.name() + " stack=" + stack
                        + " dropped=" + dropped + " at " + coords(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;
        Player p = event.getPlayer();

        // Bloque le comportement vanilla (changer le type avec un spawn egg)
        ItemStack hand = event.getItem();
        if (hand != null && hand.getType().name().endsWith("_SPAWN_EGG")) {
            event.setCancelled(true);
            p.sendMessage(Msg.err("Utilise un spawner custom pour placer un type."));
            return;
        }

        SpawnerManager mgr = plugin.spawners();
        if (mgr == null) return;
        SpawnerManager.Spawner s = mgr.at(b.getLocation());
        if (s == null) return;

        // Spawner custom en main → stack si même type; sinon on laisse la
        // pose vanilla se faire (le bloc ira au-dessus comme bloc normal).
        if (hand != null && mgr.isSpawnerItem(hand)) {
            SpawnerType handType = mgr.readType(hand);
            if (handType != s.type) {
                // Type différent: ne pas bloquer, laisser placer normalement.
                return;
            }
            event.setCancelled(true);
            int perItem = Math.max(1, mgr.readStack(hand));
            int capacity = SpawnerManager.MAX_STACK - s.stack;
            if (capacity < perItem) {
                p.sendMessage(Msg.err("Stack max atteint (×" + SpawnerManager.MAX_STACK + ")."));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                return;
            }
            int maxItems = capacity / perItem;
            int itemsConsumed = Math.min(hand.getAmount(), maxItems);
            int added = itemsConsumed * perItem;
            s.stack += added;
            s.markDirty();
            if (p.getGameMode() != GameMode.CREATIVE) {
                int remaining = hand.getAmount() - itemsConsumed;
                if (remaining <= 0) {
                    p.getInventory().setItemInMainHand(null);
                } else {
                    ItemStack next = hand.clone();
                    next.setAmount(remaining);
                    p.getInventory().setItemInMainHand(next);
                }
            }
            p.sendMessage(Msg.ok("<green>Spawner fusionné (+<yellow>×" + added
                    + "</yellow>). Stack: <yellow>×" + s.stack + "</yellow>.</green>"));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            plugin.logs().log(LogCategory.SPAWNER, p,
                    "spawner merge (click) type=" + s.type.name() + " +" + added
                            + " at " + coords(b.getLocation()));
            return;
        }

        // Bloc plaçable en main (pas un spawner) → laisser la pose vanilla:
        // le bloc va au-dessus, le spawner reste intact. Pas d'ouverture de GUI.
        if (hand != null && hand.getType().isBlock() && !hand.getType().isAir()) {
            return;
        }

        if (p.isSneaking()) return; // laisse passer le sneaking
        event.setCancelled(true);

        new SpawnerGUI(plugin, s).open(p);
    }

    private static String coords(Location l) {
        return l.getWorld().getName() + "(" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
    }

    private static boolean isPickaxe(Material m) {
        return m == Material.WOODEN_PICKAXE || m == Material.STONE_PICKAXE
                || m == Material.IRON_PICKAXE || m == Material.GOLDEN_PICKAXE
                || m == Material.DIAMOND_PICKAXE || m == Material.NETHERITE_PICKAXE;
    }

    /**
     * Détecte le type de mob d'un spawner naturel. Paper 1.21+ n'alimente plus
     * toujours le champ legacy `spawnedType` pour les spawners générés par
     * worldgen — on lit {@code getPotentialSpawns()} en fallback.
     */
    private static EntityType detectSpawnerEntity(Block b) {
        if (!(b.getState() instanceof CreatureSpawner cs)) return null;
        EntityType et = cs.getSpawnedType();
        if (et != null) return et;
        try {
            var potentials = cs.getPotentialSpawns();
            if (potentials != null && !potentials.isEmpty()) {
                var entry = potentials.iterator().next();
                var snapshot = entry.getSnapshot();
                if (snapshot != null) return snapshot.getEntityType();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
