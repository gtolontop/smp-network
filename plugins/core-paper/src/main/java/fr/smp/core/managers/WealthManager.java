package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.storage.Database;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class WealthManager {

    public enum OwnerType {
        PERSONAL,
        TEAM
    }

    public enum Category {
        SERVICES("Services", Material.ANVIL),
        TOOLS("Outils", Material.ECHO_SHARD),
        UPGRADES("Upgrades", Material.NETHER_STAR),
        TEAM("Team", Material.BLUE_BANNER),
        SERVER("Accès perso", Material.BEACON);

        private final String display;
        private final Material icon;

        Category(String display, Material icon) {
            this.display = display;
            this.icon = icon;
        }

        public String display() { return display; }
        public Material icon() { return icon; }
    }

    public enum WealthItem {
        REPAIR_HAND("repair_hand", Category.SERVICES, OwnerType.PERSONAL, Material.IRON_INGOT,
                "Réparer l'item en main", 75_000, true,
                "Répare uniquement l'item que tu tiens."),
        REPAIR_ALL("repair_all", Category.SERVICES, OwnerType.PERSONAL, Material.ANVIL,
                "Réparer tout l'inventaire", 300_000, true,
                "Répare l'inventaire, l'armure et la seconde main."),
        RTP_RESET("rtp_reset", Category.SERVICES, OwnerType.PERSONAL, Material.ENDER_PEARL,
                "Reset cooldown RTP", 100_000, true,
                "Retire ton cooldown /rtp actuel."),
        HOME_RESET("home_reset", Category.SERVICES, OwnerType.PERSONAL, Material.BLUE_BED,
                "Reset cooldown home", 80_000, true,
                "Retire ton cooldown /home actuel."),
        HOME_SLOT_IV("home_slot_4", Category.UPGRADES, OwnerType.PERSONAL, Material.CYAN_BED,
                "Home slot 4", 5_000_000, false,
                "Débloque ton 4e home personnel."),
        HOME_SLOT_V("home_slot_5", Category.UPGRADES, OwnerType.PERSONAL, Material.LIME_BED,
                "Home slot 5", 20_000_000, false,
                "Débloque ton 5e home personnel."),
        VOIDSTONE("voidstone", Category.TOOLS, OwnerType.PERSONAL, Material.ECHO_SHARD,
                "Voidstone", 750_000, true,
                "Donne une Voidstone pour stocker les blocs répétitifs."),
        SELL_STICK_I("sellstick_1", Category.TOOLS, OwnerType.PERSONAL, Material.STICK,
                "Sell Stick I", 1_500_000, true,
                "Vend les coffres au multiplicateur x1.5."),
        SELL_STICK_II("sellstick_2", Category.TOOLS, OwnerType.PERSONAL, Material.BLAZE_ROD,
                "Sell Stick II", 6_000_000, true,
                "Vend les coffres au multiplicateur x2."),
        SELL_STICK_III("sellstick_3", Category.TOOLS, OwnerType.PERSONAL, Material.END_ROD,
                "Sell Stick III", 20_000_000, true,
                "Vend les coffres au multiplicateur x2.5."),
        SHOP_DISCOUNT_I("shop_discount_1", Category.UPGRADES, OwnerType.PERSONAL, Material.EMERALD,
                "Réduction shop I", 2_000_000, false,
                "-2% sur les achats en argent du /shop."),
        SHOP_DISCOUNT_II("shop_discount_2", Category.UPGRADES, OwnerType.PERSONAL, Material.EMERALD_BLOCK,
                "Réduction shop II", 10_000_000, false,
                "-5% sur les achats en argent du /shop."),
        AUCTION_SLOTS_I("auction_slots_1", Category.UPGRADES, OwnerType.PERSONAL, Material.CHEST,
                "Slots AH I", 2_000_000, false,
                "+25 annonces actives à l'hôtel des ventes."),
        AUCTION_SLOTS_II("auction_slots_2", Category.UPGRADES, OwnerType.PERSONAL, Material.ENDER_CHEST,
                "Slots AH II", 8_000_000, false,
                "+75 annonces actives au total."),
        BOUNTY_TOPUP("bounty_topup", Category.SERVICES, OwnerType.PERSONAL, Material.TARGET,
                "Rajouter à la top prime", 1_000_000, true,
                "Ajoute $1M à la plus grosse prime active."),
        TEAM_MEMBER_IV("team_member_4", Category.TEAM, OwnerType.TEAM, Material.PLAYER_HEAD,
                "Team 4 membres", 50_000_000, false,
                "Débloque un 4e membre dans ta team."),
        TEAM_MEMBER_V("team_member_5", Category.TEAM, OwnerType.TEAM, Material.PLAYER_HEAD,
                "Team 5 membres", 150_000_000, false,
                "Débloque un 5e membre dans ta team."),
        TEAM_MEMBER_VI("team_member_6", Category.TEAM, OwnerType.TEAM, Material.PLAYER_HEAD,
                "Team 6 membres", 400_000_000, false,
                "Débloque un 6e membre dans ta team."),
        TEAM_REPAIR_ONLINE("team_repair_online", Category.TEAM, OwnerType.TEAM, Material.SMITHING_TABLE,
                "Réparation de team", 25_000_000, true,
                "Répare tous les membres de ta team connectés."),
        TEAM_RTP_PARTY("team_rtp_party", Category.TEAM, OwnerType.TEAM, Material.COMPASS,
                "RTP party de team", 35_000_000, true,
                "Reset le cooldown RTP des membres connectés."),
        SERVER_RTP_PARTY("server_rtp_party", Category.SERVER, OwnerType.PERSONAL, Material.LODESTONE,
                "RTP reset premium", 25_000_000, true,
                "Reset ton cooldown RTP, version très chère pour sink."),
        SERVER_REPAIR_PARTY("server_repair_party", Category.SERVER, OwnerType.PERSONAL, Material.BEACON,
                "Réparation premium", 50_000_000, true,
                "Répare uniquement ton inventaire, avec un prix endgame."),
        RTP_NETHER("rtp_nether", Category.SERVER, OwnerType.PERSONAL, Material.NETHERRACK,
                "RTP Nether", 750_000_000, false,
                "Débloque le bouton RTP Nether pour ton compte."),
        RTP_END("rtp_end", Category.SERVER, OwnerType.PERSONAL, Material.END_STONE,
                "RTP End", 1_500_000_000, false,
                "Débloque le bouton RTP End pour ton compte.");

        private final String id;
        private final Category category;
        private final OwnerType ownerType;
        private final Material icon;
        private final String displayName;
        private final double price;
        private final boolean repeatable;
        private final String description;

        WealthItem(String id, Category category, OwnerType ownerType, Material icon, String displayName,
                   double price, boolean repeatable, String description) {
            this.id = id;
            this.category = category;
            this.ownerType = ownerType;
            this.icon = icon;
            this.displayName = displayName;
            this.price = price;
            this.repeatable = repeatable;
            this.description = description;
        }

        public String id() { return id; }
        public Category category() { return category; }
        public OwnerType ownerType() { return ownerType; }
        public Material icon() { return icon; }
        public String displayName() { return displayName; }
        public double price() { return price; }
        public boolean repeatable() { return repeatable; }
        public String description() { return description; }

        public static WealthItem byId(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String key = raw.toLowerCase(Locale.ROOT);
            for (WealthItem item : values()) {
                if (item.id.equals(key) || item.name().equalsIgnoreCase(raw)) return item;
            }
            return null;
        }
    }

    public record SpendingEntry(String name, double amount) {}

    private final SMPCore plugin;
    private final Database db;

    public WealthManager(SMPCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public boolean isUnlocked(OwnerType type, String ownerId, WealthItem item) {
        if (item == null || ownerId == null) return false;
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM wealth_unlocks WHERE owner_type=? AND owner_id=? AND item_id=?")) {
            ps.setString(1, type.name());
            ps.setString(2, ownerId);
            ps.setString(3, item.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("wealth.isUnlocked: " + e.getMessage());
            return false;
        }
    }

    public double shopDiscount(UUID uuid) {
        String owner = uuid.toString();
        if (isUnlocked(OwnerType.PERSONAL, owner, WealthItem.SHOP_DISCOUNT_II)) return 0.05;
        if (isUnlocked(OwnerType.PERSONAL, owner, WealthItem.SHOP_DISCOUNT_I)) return 0.02;
        return 0.0;
    }

    public int auctionExtraSlots(UUID uuid) {
        String owner = uuid.toString();
        if (isUnlocked(OwnerType.PERSONAL, owner, WealthItem.AUCTION_SLOTS_II)) return 75;
        if (isUnlocked(OwnerType.PERSONAL, owner, WealthItem.AUCTION_SLOTS_I)) return 25;
        return 0;
    }

    public int homeExtraSlots(UUID uuid) {
        String owner = uuid.toString();
        if (isUnlocked(OwnerType.PERSONAL, owner, WealthItem.HOME_SLOT_V)) return 2;
        if (isUnlocked(OwnerType.PERSONAL, owner, WealthItem.HOME_SLOT_IV)) return 1;
        return 0;
    }

    public boolean canRtpNether(Player player) {
        if (player == null) return false;
        if (player.hasPermission("smp.admin")) return true;
        return isUnlocked(OwnerType.PERSONAL, player.getUniqueId().toString(), WealthItem.RTP_NETHER);
    }

    public boolean canRtpEnd(Player player) {
        if (player == null) return false;
        if (player.hasPermission("smp.admin")) return true;
        return isUnlocked(OwnerType.PERSONAL, player.getUniqueId().toString(), WealthItem.RTP_END);
    }

    public int teamExtraMembers(String teamId) {
        int extra = 0;
        if (isUnlocked(OwnerType.TEAM, teamId, WealthItem.TEAM_MEMBER_IV)) extra = 1;
        if (isUnlocked(OwnerType.TEAM, teamId, WealthItem.TEAM_MEMBER_V)) extra = 2;
        if (isUnlocked(OwnerType.TEAM, teamId, WealthItem.TEAM_MEMBER_VI)) extra = 3;
        return extra;
    }

    public boolean purchase(Player player, WealthItem item) {
        if (player == null || item == null) return false;
        String ownerId = ownerId(player, item);
        if (ownerId == null) return false;
        if (!item.repeatable() && isUnlocked(item.ownerType(), ownerId, item)) {
            player.sendMessage(Msg.err("Déjà débloqué."));
            return false;
        }
        if (!checkPrerequisite(player, item, ownerId)) return false;
        if (!withdraw(player, item, ownerId)) return false;
        if (!item.repeatable()) unlock(item.ownerType(), ownerId, item);
        recordSpend(player, item.ownerType(), ownerId, item, item.price());
        applyEffect(player, item, ownerId);
        plugin.getLogger().info("[WEALTH] " + player.getName() + " purchased " + item.id()
                + " for $" + Msg.money(item.price()));
        return true;
    }

    private String ownerId(Player player, WealthItem item) {
        if (item.ownerType() == OwnerType.PERSONAL) return player.getUniqueId().toString();
        var data = plugin.players().get(player);
        if (data == null || data.teamId() == null) {
            player.sendMessage(Msg.err("Tu dois être dans une team."));
            return null;
        }
        var team = plugin.teams().get(data.teamId());
        if (team == null) {
            player.sendMessage(Msg.err("Team introuvable."));
            return null;
        }
        if (!team.owner().equals(player.getUniqueId().toString())) {
            player.sendMessage(Msg.err("Owner uniquement pour les achats de team."));
            return null;
        }
        return team.id();
    }

    private boolean checkPrerequisite(Player player, WealthItem item, String ownerId) {
        return switch (item) {
            case SHOP_DISCOUNT_II -> requireUnlocked(player, OwnerType.PERSONAL, ownerId, WealthItem.SHOP_DISCOUNT_I);
            case AUCTION_SLOTS_II -> requireUnlocked(player, OwnerType.PERSONAL, ownerId, WealthItem.AUCTION_SLOTS_I);
            case HOME_SLOT_V -> requireUnlocked(player, OwnerType.PERSONAL, ownerId, WealthItem.HOME_SLOT_IV);
            case TEAM_MEMBER_V -> requireUnlocked(player, OwnerType.TEAM, ownerId, WealthItem.TEAM_MEMBER_IV);
            case TEAM_MEMBER_VI -> requireUnlocked(player, OwnerType.TEAM, ownerId, WealthItem.TEAM_MEMBER_V);
            case BOUNTY_TOPUP -> {
                if (plugin.bounties() != null && !plugin.bounties().top(1).isEmpty()) yield true;
                player.sendMessage(Msg.err("Aucune prime active à financer."));
                yield false;
            }
            default -> true;
        };
    }

    private boolean requireUnlocked(Player player, OwnerType type, String ownerId, WealthItem required) {
        if (isUnlocked(type, ownerId, required)) return true;
        player.sendMessage(Msg.err("Débloque d'abord: <white>" + required.displayName() + "</white>."));
        return false;
    }

    private boolean withdraw(Player player, WealthItem item, String ownerId) {
        if (item.ownerType() == OwnerType.TEAM) {
            var team = plugin.teams().get(ownerId);
            if (team == null) {
                player.sendMessage(Msg.err("Team introuvable."));
                return false;
            }
            if (team.balance() < item.price()) {
                player.sendMessage(Msg.err("Banque de team insuffisante. Il faut $" + Msg.money(item.price()) + "."));
                return false;
            }
            plugin.teams().addBalance(ownerId, -item.price());
            return true;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), item.price(), "wealth." + item.id())) {
            player.sendMessage(Msg.err("Fonds insuffisants. Il faut $" + Msg.money(item.price()) + "."));
            return false;
        }
        return true;
    }

    private void unlock(OwnerType type, String ownerId, WealthItem item) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO wealth_unlocks(owner_type, owner_id, item_id, purchased_at) VALUES(?,?,?,?)")) {
            ps.setString(1, type.name());
            ps.setString(2, ownerId);
            ps.setString(3, item.id());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("wealth.unlock: " + e.getMessage());
        }
    }

    private void recordSpend(Player buyer, OwnerType type, String ownerId, WealthItem item, double amount) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO wealth_spending(buyer_uuid, buyer_name, owner_type, owner_id, item_id, amount, created_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, buyer.getUniqueId().toString());
            ps.setString(2, buyer.getName());
            ps.setString(3, type.name());
            ps.setString(4, ownerId);
            ps.setString(5, item.id());
            ps.setDouble(6, amount);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("wealth.recordSpend: " + e.getMessage());
        }
    }

    private void applyEffect(Player player, WealthItem item, String ownerId) {
        switch (item) {
            case REPAIR_HAND -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (repairItem(hand)) player.sendMessage(Msg.ok("<green>Item en main réparé.</green>"));
                else player.sendMessage(Msg.info("<gray>Aucun dégât à réparer, achat conservé comme sink.</gray>"));
            }
            case REPAIR_ALL -> {
                int repaired = repairAll(player);
                player.sendMessage(Msg.ok("<green>" + repaired + " item(s) réparé(s).</green>"));
            }
            case RTP_RESET -> {
                if (plugin.rtp() != null) plugin.rtp().clearCooldown(player);
                player.sendMessage(Msg.ok("<green>Cooldown RTP reset.</green>"));
            }
            case HOME_RESET -> {
                if (plugin.cooldowns() != null) plugin.cooldowns().clear(player, "home");
                player.sendMessage(Msg.ok("<green>Cooldown home reset.</green>"));
            }
            case VOIDSTONE -> give(player, plugin.voidstones() != null
                    ? plugin.voidstones().createItem()
                    : new ItemStack(Material.ECHO_SHARD));
            case SELL_STICK_I -> give(player, plugin.sellSticks() != null
                    ? plugin.sellSticks().createItem(1)
                    : new ItemStack(Material.STICK));
            case SELL_STICK_II -> give(player, plugin.sellSticks() != null
                    ? plugin.sellSticks().createItem(2)
                    : new ItemStack(Material.STICK));
            case SELL_STICK_III -> give(player, plugin.sellSticks() != null
                    ? plugin.sellSticks().createItem(3)
                    : new ItemStack(Material.STICK));
            case BOUNTY_TOPUP -> fundTopBounty(player);
            case TEAM_REPAIR_ONLINE -> {
                int repaired = 0;
                for (Player target : Bukkit.getOnlinePlayers()) {
                    var data = plugin.players().get(target);
                    if (data != null && ownerId.equals(data.teamId())) repaired += repairAll(target);
                }
                Bukkit.broadcast(Msg.info("<aqua>" + player.getName() + "</aqua> a financé une réparation de team <gray>(" + repaired + " items)</gray>."));
            }
            case TEAM_RTP_PARTY -> {
                int count = 0;
                for (Player target : Bukkit.getOnlinePlayers()) {
                    var data = plugin.players().get(target);
                    if (data != null && ownerId.equals(data.teamId()) && plugin.rtp() != null) {
                        plugin.rtp().clearCooldown(target);
                        count++;
                    }
                }
                Bukkit.broadcast(Msg.info("<aqua>" + player.getName() + "</aqua> a financé une RTP party de team <gray>(" + count + " joueurs)</gray>."));
            }
            case SERVER_RTP_PARTY -> {
                if (plugin.rtp() != null) plugin.rtp().clearCooldown(player);
                player.sendMessage(Msg.ok("<green>Cooldown RTP reset.</green>"));
            }
            case SERVER_REPAIR_PARTY -> {
                int repaired = repairAll(player);
                player.sendMessage(Msg.ok("<green>" + repaired + " item(s) réparé(s).</green>"));
            }
            case RTP_NETHER -> {
                player.sendMessage(Msg.ok("<green>RTP Nether débloqué pour ton compte.</green>"));
            }
            case RTP_END -> {
                player.sendMessage(Msg.ok("<green>RTP End débloqué pour ton compte.</green>"));
            }
            default -> player.sendMessage(Msg.ok("<green>Upgrade débloquée: <white>" + item.displayName() + "</white>.</green>"));
        }
    }

    private void fundTopBounty(Player player) {
        if (plugin.bounties() == null) {
            player.sendMessage(Msg.err("Bounties indisponibles."));
            return;
        }
        var top = plugin.bounties().top(1);
        if (top.isEmpty()) {
            player.sendMessage(Msg.err("Aucune prime active à financer."));
            return;
        }
        var bounty = top.get(0);
        double next = plugin.bounties().add(bounty.target(), bounty.targetName(),
                player.getUniqueId(), player.getName(), 1_000_000);
        Bukkit.broadcast(Msg.info("<gold>" + player.getName() + "</gold> ajoute <yellow>$1M</yellow> à la prime de <red>"
                + bounty.targetName() + "</red> <gray>(total $" + Msg.money(next) + ")</gray>."));
    }

    public double personalSpent(UUID uuid) {
        return sum("buyer_uuid=?", uuid.toString());
    }

    public double teamSpent(String teamId) {
        return sum("owner_type='TEAM' AND owner_id=?", teamId);
    }

    private double sum(String where, String value) {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM wealth_spending WHERE " + where)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0D;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("wealth.sum: " + e.getMessage());
            return 0D;
        }
    }

    public List<SpendingEntry> topPersonalSpenders(int limit) {
        List<SpendingEntry> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT buyer_name, SUM(amount) AS spent FROM wealth_spending GROUP BY buyer_uuid ORDER BY spent DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new SpendingEntry(rs.getString(1), rs.getDouble(2)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("wealth.topPersonal: " + e.getMessage());
        }
        return out;
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        if (!damageable.hasDamage() || damageable.getDamage() <= 0) return false;
        damageable.setDamage(0);
        item.setItemMeta(meta);
        return true;
    }

    private int repairAll(Player target) {
        int count = 0;
        for (ItemStack item : target.getInventory().getContents()) if (repairItem(item)) count++;
        for (ItemStack item : target.getInventory().getArmorContents()) if (repairItem(item)) count++;
        if (repairItem(target.getInventory().getItemInOffHand())) count++;
        return count;
    }

    private void give(Player player, ItemStack item) {
        var overflow = player.getInventory().addItem(item);
        overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        player.sendMessage(Msg.ok("<green>Achat reçu.</green>"));
    }
}
