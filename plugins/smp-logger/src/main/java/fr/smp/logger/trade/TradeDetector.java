package fr.smp.logger.trade;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detects player↔player item exchanges by correlating drops/pickups and chest
 * handoffs. Holds two short-lived ring buffers of pending events:
 *
 *  - DropBuffer: every drop within the last N seconds, keyed by world+entityId.
 *    On pickup by a different player within radius/time → emit TRADE_DROP_PICKUP.
 *
 *  - ContainerHandoffBuffer: per-location list of (player, item, +/-amount, t).
 *    When B opens a chest where A inserted item X within window → emit
 *    TRADE_CHEST_HANDOFF for each X taken from A's pile.
 *
 * Trades are persisted to the cross-day `trades` table (separate from event
 * partitions so they survive past 7 days for audit).
 */
public class TradeDetector {

    private final SMPLogger plugin;
    private final long dropWindowMs;
    private final double dropRadiusSq;
    private final long handoffWindowMs;
    private final boolean villager;

    /** itemEntityUUID -> pending drop. */
    private final Map<UUID, PendingDrop> drops = new HashMap<>();

    /** locationKey -> deque of pending handoffs (oldest first). */
    private final Map<Long, Deque<PendingHandoff>> handoffs = new HashMap<>();

    public TradeDetector(SMPLogger plugin) {
        this.plugin = plugin;
        long dropSec = Math.max(1L, plugin.getConfig().getLong("trade.drop-pickup-window-seconds", 8L));
        double dropRad = Math.max(1.0, plugin.getConfig().getDouble("trade.drop-pickup-radius-blocks", 8.0));
        long handoffSec = Math.max(5L, plugin.getConfig().getLong("trade.chest-handoff-window-seconds", 30L));
        this.dropWindowMs = dropSec * 1000L;
        this.dropRadiusSq = dropRad * dropRad;
        this.handoffWindowMs = handoffSec * 1000L;
        this.villager = plugin.getConfig().getBoolean("trade.villager", true);
    }

    /** Called from the drop listener. */
    public void recordDrop(Player p, Item dropped) {
        prune();
        drops.put(dropped.getUniqueId(), new PendingDrop(p.getUniqueId(),
                dropped.getItemStack().clone(), dropped.getLocation(), System.currentTimeMillis()));
    }

    /** Called from the pickup listener; returns true if a trade was detected. */
    public boolean tryPairPickup(Player picker, Item itemEntity) {
        prune();
        PendingDrop pd = drops.remove(itemEntity.getUniqueId());
        if (pd == null) return false;
        if (pd.player.equals(picker.getUniqueId())) return false;
        long now = System.currentTimeMillis();
        if (now - pd.tMs > dropWindowMs) return false;
        Location pl = picker.getLocation();
        if (pl.getWorld() != pd.where.getWorld()) return false;
        if (pl.distanceSquared(pd.where) > dropRadiusSq) return false;
        persistTrade(pd.player, picker.getUniqueId(), pd.item, Action.TRADE_DROP_PICKUP, pl, now);
        return true;
    }

    /** Called by ContainerModule on close — diff over the open snapshot. */
    public void recordContainerInteraction(Player who, Location loc,
                                           Map<String, Integer> before,
                                           Map<String, Integer> after) {
        if (loc == null) return;
        prune();
        long now = System.currentTimeMillis();
        long key = locKey(loc);

        // Inserts (after > before): record as pending handoff.
        for (var e : after.entrySet()) {
            int delta = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (delta > 0) {
                handoffs.computeIfAbsent(key, k -> new ArrayDeque<>())
                        .add(new PendingHandoff(who.getUniqueId(), Material.matchMaterial(e.getKey()),
                                delta, loc, now));
            }
        }
        // Takes (before > after): try to pair against pending inserts by another player.
        for (var e : before.entrySet()) {
            int delta = e.getValue() - after.getOrDefault(e.getKey(), 0);
            if (delta <= 0) continue;
            Deque<PendingHandoff> q = handoffs.get(key);
            if (q == null) continue;
            Iterator<PendingHandoff> it = q.iterator();
            while (it.hasNext() && delta > 0) {
                PendingHandoff h = it.next();
                if (now - h.tMs > handoffWindowMs) { it.remove(); continue; }
                if (h.player.equals(who.getUniqueId())) continue;
                if (h.material == null || !h.material.name().equals(e.getKey())) continue;
                int taken = Math.min(delta, h.amount);
                ItemStack stub = new ItemStack(h.material, taken);
                persistTrade(h.player, who.getUniqueId(), stub, Action.TRADE_CHEST_HANDOFF, loc, now);
                h.amount -= taken;
                delta -= taken;
                if (h.amount <= 0) it.remove();
            }
        }
    }

    /** Called by EntityModule when a villager trade resolves. */
    public void recordVillagerTrade(Player p, Location at, ItemStack received) {
        if (!villager) return;
        persistTrade(null, p.getUniqueId(), received, Action.TRADE_VILLAGER, at, System.currentTimeMillis());
    }

    public boolean villagerEnabled() { return villager; }

    private void persistTrade(UUID from, UUID to, ItemStack item, Action method, Location at, long t) {
        if (item == null || item.getType().isAir()) return;
        int fromId = from == null ? 0 : plugin.players().idOf(from, "?");
        int toId = to == null ? 0 : plugin.players().idOf(to, "?");
        int matId = plugin.materials().idOf(item.getType());
        int amount = item.getAmount();
        byte[] hash = plugin.preciousStore().storeIfPrecious(item);
        int worldId = plugin.worlds().idOf(at.getWorld());

        try (Connection c = plugin.db().writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO trades(t, from_player, to_player, method, material_id, amount, "
                             + "item_hash, world_id, x, y, z) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, t);
            ps.setInt(2, fromId);
            ps.setInt(3, toId);
            ps.setInt(4, method.id());
            ps.setInt(5, matId);
            ps.setInt(6, amount);
            if (hash != null) ps.setBytes(7, hash); else ps.setNull(7, java.sql.Types.BLOB);
            ps.setInt(8, worldId);
            ps.setInt(9, at.getBlockX());
            ps.setInt(10, at.getBlockY());
            ps.setInt(11, at.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Trade persist failed: " + e.getMessage());
        }
    }

    private void prune() {
        long now = System.currentTimeMillis();
        drops.entrySet().removeIf(en -> now - en.getValue().tMs > dropWindowMs);
        handoffs.values().forEach(q -> q.removeIf(h -> now - h.tMs > handoffWindowMs));
        handoffs.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static long locKey(Location l) {
        long w = l.getWorld() == null ? 0 : l.getWorld().getName().hashCode();
        return (w << 40) ^ ((long) l.getBlockX() << 20) ^ l.getBlockY() ^ ((long) l.getBlockZ() << 4);
    }

    private record PendingDrop(UUID player, ItemStack item, Location where, long tMs) {}

    private static final class PendingHandoff {
        final UUID player;
        final Material material;
        int amount;
        final Location loc;
        final long tMs;
        PendingHandoff(UUID player, Material material, int amount, Location loc, long tMs) {
            this.player = player; this.material = material; this.amount = amount;
            this.loc = loc; this.tMs = tMs;
        }
    }
}
