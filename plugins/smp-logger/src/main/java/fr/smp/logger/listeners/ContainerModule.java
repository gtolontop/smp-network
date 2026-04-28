package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks every container interaction:
 *  - Open snapshot, close diff → CONTAINER_INSERT / CONTAINER_TAKE per item.
 *  - Hopper/dispenser/dropper auto-flow → INVENTORY_TRANSFER (sampled to avoid spam).
 *
 * Also publishes "container handoff" hints to TradeDetector so /trades can
 * resolve A-puts-then-B-takes-within-30s into a TRADE_CHEST_HANDOFF event.
 */
public class ContainerModule implements Listener {

    private final SMPLogger plugin;
    private final Map<UUID, Snapshot> openSnapshots = new HashMap<>();

    public ContainerModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        Location loc = locationOf(inv);
        if (loc == null) return; // ignore craft / player inv etc.

        EventBuilder.begin(plugin)
                .action(Action.CONTAINER_OPEN)
                .actor(p)
                .at(loc)
                .material(materialOf(inv))
                .submit();

        openSnapshots.put(p.getUniqueId(), Snapshot.of(p, inv, loc));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Snapshot snap = openSnapshots.remove(p.getUniqueId());
        if (snap == null) return;

        EventBuilder.begin(plugin)
                .action(Action.CONTAINER_CLOSE)
                .actor(p)
                .at(snap.loc)
                .material(snap.material)
                .submit();

        Inventory now = e.getInventory();
        Map<String, Integer> before = snap.contents;
        Map<String, Integer> after = countContents(now);

        for (var entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta > 0) {
                emitDiff(p, snap, entry.getKey(), delta, Action.CONTAINER_INSERT);
            }
        }
        for (var entry : before.entrySet()) {
            int delta = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (delta > 0) {
                emitDiff(p, snap, entry.getKey(), delta, Action.CONTAINER_TAKE);
            }
        }
        // Notify trade detector so handoff pairing can run.
        plugin.tradeDetector().recordContainerInteraction(p, snap.loc, before, after);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        // Sampled: log only ~1 in 100 to avoid choking the queue with redstone farms.
        if ((System.nanoTime() & 0x7F) != 0) return;
        Location loc = locationOf(e.getDestination());
        if (loc == null) return;
        ItemStack it = e.getItem();
        EventBuilder.begin(plugin)
                .action(Action.INVENTORY_TRANSFER)
                .at(loc)
                .material(it.getType())
                .amount(it.getAmount())
                .item(it)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(InventoryClickEvent e) {
        // Some bookkeeping for special slots (anvil rename + grindstone) is handled
        // by WorldChangeModule. Here we only care about cross-inventory shift-clicks
        // into the player's inv when source is a container — already covered by close-diff.
    }

    private void emitDiff(Player p, Snapshot snap, String matName, int delta, Action action) {
        EventBuilder.begin(plugin)
                .action(action)
                .actor(p)
                .at(snap.loc)
                .material(matName)
                .amount(delta)
                .submit();
    }

    private static Map<String, Integer> countContents(Inventory inv) {
        Map<String, Integer> m = new HashMap<>();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            // For non-precious: key by material name. For precious: distinct key per hash.
            // For now collapse by material — precious diffing is captured separately on
            // place/break of containers + snapshot of inv.
            m.merge(it.getType().name(), it.getAmount(), Integer::sum);
        }
        return m;
    }

    private static Location locationOf(Inventory inv) {
        if (inv.getLocation() != null) return inv.getLocation();
        InventoryHolder h = inv.getHolder();
        if (h instanceof org.bukkit.block.BlockState bs) return bs.getLocation();
        return null;
    }

    private static Material materialOf(Inventory inv) {
        Location l = locationOf(inv);
        if (l == null) return Material.AIR;
        Block b = l.getBlock();
        return b.getType();
    }

    /** Captured at open time — used to compute diffs at close. */
    public static final class Snapshot {
        public final Map<String, Integer> contents;
        public final Location loc;
        public final Material material;
        Snapshot(Map<String, Integer> contents, Location loc, Material material) {
            this.contents = contents; this.loc = loc; this.material = material;
        }
        static Snapshot of(HumanEntity p, Inventory inv, Location loc) {
            return new Snapshot(countContents(inv), loc, locForMaterial(loc));
        }
        private static Material locForMaterial(Location loc) {
            return loc == null ? Material.AIR : loc.getBlock().getType();
        }
    }
}
