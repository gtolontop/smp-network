package fr.smp.core.order;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import fr.smp.core.utils.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrderManager {

    /** In-progress order creation state, wiped on confirm or escape. */
    public static class PendingCreation {
        public Material itemType;
        public int quantity = -1;
        public double pricePerUnit = -1;

        public boolean isReady() {
            return itemType != null && quantity > 0 && pricePerUnit > 0;
        }
    }

    public static final int    MAX_ORDERS_PER_PLAYER = 10;
    public static final double MAX_ORDER_TOTAL       = 50_000_000.0;

    private final SMPCore plugin;
    private final Database db;
    private final Map<UUID, PendingCreation> pendingCreations = new ConcurrentHashMap<>();

    public OrderManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ── Pending creation ──────────────────────────────────────────────────────

    public PendingCreation getOrCreatePending(UUID uuid) {
        return pendingCreations.computeIfAbsent(uuid, k -> new PendingCreation());
    }

    public void removePending(UUID uuid) {
        pendingCreations.remove(uuid);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Order> getActiveOrders(OrderSort sort) {
        return query("SELECT * FROM orders WHERE fulfilled_quantity < quantity", sort, ps -> {});
    }

    public List<Order> getByPlayer(String name, OrderSort sort) {
        return query(
                "SELECT * FROM orders WHERE LOWER(buyer_name)=LOWER(?) AND fulfilled_quantity < quantity",
                sort, ps -> ps.setString(1, name));
    }

    public List<Order> getByItem(Material item, OrderSort sort) {
        return query(
                "SELECT * FROM orders WHERE item_type=? AND fulfilled_quantity < quantity",
                sort, ps -> ps.setString(1, item.name()));
    }

    public Order getById(int id) {
        List<Order> r = query("SELECT * FROM orders WHERE id=?", null, ps -> ps.setInt(1, id));
        return r.isEmpty() ? null : r.get(0);
    }

    public int countActiveByPlayer(UUID uuid) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM orders WHERE buyer_uuid=? AND fulfilled_quantity < quantity")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Orders] count: " + e.getMessage());
        }
        return 0;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Places a buy order and withdraws the escrow (quantity × pricePerUnit) from the buyer.
     * Returns the new order id, or -1 on failure.
     */
    public int createOrder(Player buyer, Material itemType, int quantity, double pricePerUnit) {
        double total = quantity * pricePerUnit;

        if (countActiveByPlayer(buyer.getUniqueId()) >= MAX_ORDERS_PER_PLAYER) {
            buyer.sendMessage(Msg.err("Maximum " + MAX_ORDERS_PER_PLAYER + " commandes actives à la fois."));
            return -1;
        }
        if (total > MAX_ORDER_TOTAL) {
            buyer.sendMessage(Msg.err("Total max par commande: " + Msg.money(MAX_ORDER_TOTAL) + "$."));
            return -1;
        }
        if (!plugin.economy().withdraw(buyer.getUniqueId(), total, "Commande escrow")) {
            buyer.sendMessage(Msg.err("Fonds insuffisants. Il te faut " + Msg.money(total) + "$."));
            return -1;
        }

        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO orders (buyer_uuid,buyer_name,item_type,quantity,price_per_unit,created_at,fulfilled_quantity)"
                     + " VALUES (?,?,?,?,?,?,0)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, buyer.getUniqueId().toString());
            ps.setString(2, buyer.getName());
            ps.setString(3, itemType.name());
            ps.setInt(4, quantity);
            ps.setDouble(5, pricePerUnit);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Orders] createOrder: " + e.getMessage());
            plugin.economy().deposit(buyer.getUniqueId(), total, "Remboursement escrow (erreur DB)");
        }
        return -1;
    }

    /**
     * Delivers items from the seller's inventory to satisfy an order (partial allowed).
     * Pays the seller immediately. Returns the actual quantity delivered.
     */
    public int fulfill(Player seller, Order order, int requested) {
        if (order.getBuyerUuid().equals(seller.getUniqueId().toString())) {
            seller.sendMessage(Msg.err("Tu ne peux pas livrer ta propre commande."));
            return 0;
        }
        int inInv  = countInInv(seller, order.getItemType());
        int actual = Math.min(requested, Math.min(inInv, order.getRemainingQuantity()));
        if (actual <= 0) {
            seller.sendMessage(Msg.err("Tu n'as pas de " + prettyMat(order.getItemType()) + " dans l'inventaire."));
            return 0;
        }

        removeFromInv(seller, order.getItemType(), actual);
        double earned = actual * order.getPricePerUnit();
        plugin.economy().deposit(seller.getUniqueId(), earned, "Livraison commande #" + order.getId());

        int newFulfilled = order.getFulfilledQuantity() + actual;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE orders SET fulfilled_quantity=? WHERE id=?")) {
            ps.setInt(1, newFulfilled);
            ps.setInt(2, order.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Orders] fulfill update: " + e.getMessage());
        }
        order.setFulfilledQuantity(newFulfilled);

        seller.sendMessage(Msg.ok("Livré <white>" + actual + " " + prettyMat(order.getItemType())
                + "</white> → <green>" + Msg.money(earned) + "$</green>"));

        Player buyer = plugin.getServer().getPlayer(UUID.fromString(order.getBuyerUuid()));
        if (buyer != null) {
            String done = order.isFullyFulfilled() ? " <yellow>Commande complète !</yellow>" : "";
            buyer.sendMessage(Msg.info("<green>" + seller.getName() + "</green> a livré <white>"
                    + actual + " " + prettyMat(order.getItemType())
                    + "</white> pour ta commande <gray>#" + order.getId() + "</gray>." + done));
        }
        return actual;
    }

    /**
     * Cancels an order owned by the buyer and refunds the remaining escrow.
     */
    public boolean cancel(Player buyer, Order order) {
        if (!order.getBuyerUuid().equals(buyer.getUniqueId().toString())) {
            buyer.sendMessage(Msg.err("Ce n'est pas ta commande."));
            return false;
        }
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM orders WHERE id=?")) {
            ps.setInt(1, order.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Orders] cancel: " + e.getMessage());
            return false;
        }
        double refund = order.getRemainingValue();
        if (refund > 0) {
            plugin.economy().deposit(buyer.getUniqueId(), refund, "Remboursement commande #" + order.getId());
            buyer.sendMessage(Msg.ok("Commande annulée. Remboursé <green>" + Msg.money(refund) + "$</green>."));
        } else {
            buyer.sendMessage(Msg.ok("Commande annulée."));
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Order> query(String sql, OrderSort sort, PSetter setter) {
        List<Order> list = new ArrayList<>();
        try (Connection c = db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            setter.set(ps);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(fromRs(rs));
        } catch (SQLException e) {
            plugin.getLogger().warning("[Orders] query: " + e.getMessage());
        }
        if (sort != null) list.sort(sort.getComparator());
        return list;
    }

    @FunctionalInterface
    private interface PSetter { void set(PreparedStatement ps) throws SQLException; }

    private Order fromRs(ResultSet rs) throws SQLException {
        String matName = rs.getString("item_type");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.BARRIER;
        return new Order(
                rs.getInt("id"),
                rs.getString("buyer_uuid"),
                rs.getString("buyer_name"),
                mat,
                rs.getInt("quantity"),
                rs.getDouble("price_per_unit"),
                rs.getLong("created_at"),
                rs.getInt("fulfilled_quantity"));
    }

    private int countInInv(Player p, Material m) {
        int count = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m) count += it.getAmount();
        }
        return count;
    }

    private void removeFromInv(Player p, Material m, int amount) {
        int left = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != m) continue;
            if (it.getAmount() <= left) { left -= it.getAmount(); contents[i] = null; }
            else                        { it.setAmount(it.getAmount() - left); left = 0; }
        }
        p.getInventory().setContents(contents);
        p.updateInventory();
    }

    public static String prettyMat(Material m) {
        String[] words = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
