package fr.smp.logger.rare;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dedicated tracker for high-value/rare resources. Writes to its own
 * cross-day `rare_resources` table with enriched context (Y, biome, fortune,
 * silktouch, tool used, spawner type) so /rare queries are blazing fast and
 * the data survives the 7-day partition purge.
 *
 * Hooks the same Block/Pickup events as other modules but with very narrow
 * filters — only triggers for rare blocks/items.
 */
public class RareResourceTracker implements Listener {

    private final SMPLogger plugin;
    private final Set<Material> rareBlocks;
    private final Set<Material> rareItems;

    public RareResourceTracker(SMPLogger plugin) {
        this.plugin = plugin;
        this.rareBlocks = parseMaterials(plugin.getConfig().getStringList("rare.blocks"));
        this.rareItems = parseMaterials(plugin.getConfig().getStringList("rare.items"));
    }

    private static Set<Material> parseMaterials(List<String> raw) {
        Set<Material> out = new HashSet<>();
        for (String s : raw) {
            try { out.add(Material.valueOf(s)); }
            catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!rareBlocks.contains(b.getType())) return;
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        int fortune = tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE);
        int silk = tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH) ? 1 : 0;
        String biome = b.getBiome().getKey().toString();
        String spawnerType = null;
        if (b.getType() == Material.SPAWNER && b.getState() instanceof CreatureSpawner cs) {
            spawnerType = cs.getSpawnedType() == null ? "?" : cs.getSpawnedType().getKey().toString();
        }
        Action action = (b.getType() == Material.SPAWNER || b.getType() == Material.TRIAL_SPAWNER)
                ? Action.SPAWNER_BREAK : Action.RARE_BREAK;
        persist(action, p, b, b.getType(), 1, tool == null ? null : tool.getType(),
                fortune, silk, biome, spawnerType, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!rareBlocks.contains(b.getType())) return;
        Player p = e.getPlayer();
        String biome = b.getBiome().getKey().toString();
        String spawnerType = null;
        if (b.getType() == Material.SPAWNER && b.getState() instanceof CreatureSpawner cs) {
            spawnerType = cs.getSpawnedType() == null ? "?" : cs.getSpawnedType().getKey().toString();
        }
        Action action = (b.getType() == Material.SPAWNER || b.getType() == Material.TRIAL_SPAWNER)
                ? Action.SPAWNER_PLACE : Action.RARE_PLACE;
        persist(action, p, b, b.getType(), 1, null, 0, 0, biome, spawnerType, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Item entity = e.getItem();
        ItemStack st = entity.getItemStack();
        if (!rareItems.contains(st.getType())) return;
        persist(Action.RARE_PICKUP, p, p.getLocation().getBlock(), st.getType(),
                st.getAmount(), null, 0, 0, p.getLocation().getBlock().getBiome().getKey().toString(),
                null, plugin.preciousDetector().summarize(st));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack st = e.getItemDrop().getItemStack();
        if (!rareItems.contains(st.getType())) return;
        Player p = e.getPlayer();
        persist(Action.RARE_DROP, p, p.getLocation().getBlock(), st.getType(),
                st.getAmount(), null, 0, 0, p.getLocation().getBlock().getBiome().getKey().toString(),
                null, plugin.preciousDetector().summarize(st));
    }

    private void persist(Action action, Player p, Block at, Material mat, int amount,
                         Material tool, int fortune, int silk, String biome,
                         String spawnerType, String extra) {
        int playerId = plugin.players().idOf(p.getUniqueId(), p.getName());
        int worldId = plugin.worlds().idOf(at.getWorld());
        int matId = plugin.materials().idOf(mat);
        int toolId = tool == null ? 0 : plugin.materials().idOf(tool);
        try (Connection c = plugin.db().writer();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO rare_resources(t, action, player_id, world_id, x, y, z, "
                             + "material_id, amount, tool_material_id, fortune, silktouch, biome, "
                             + "spawner_type, extra) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, action.id());
            ps.setInt(3, playerId);
            ps.setInt(4, worldId);
            ps.setInt(5, at.getX());
            ps.setInt(6, at.getY());
            ps.setInt(7, at.getZ());
            ps.setInt(8, matId);
            ps.setInt(9, amount);
            if (toolId == 0) ps.setNull(10, java.sql.Types.INTEGER); else ps.setInt(10, toolId);
            ps.setInt(11, fortune);
            ps.setInt(12, silk);
            ps.setString(13, biome);
            ps.setString(14, spawnerType);
            ps.setString(15, extra);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Rare persist failed: " + e.getMessage());
        }
    }
}
