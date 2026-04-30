package fr.smp.core.sellstick;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.sell.SellCategory;
import fr.smp.core.sell.SellTierManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SellStickListener implements Listener {

    private static final long COOLDOWN_MS = 3_000;

    private final SMPCore plugin;
    private final SellStickManager manager;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public SellStickListener(SMPCore plugin, SellStickManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!manager.isSellStick(hand)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST
                && block.getType() != Material.BARREL) return;

        event.setCancelled(true);

        // Anti-spam: cooldown par joueur
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            long left = (COOLDOWN_MS - (now - last) + 999) / 1000;
            player.sendMessage(Msg.err("<red>Attends encore <yellow>" + left + "s</yellow>.</red>"));
            return;
        }
        lastUse.put(player.getUniqueId(), now);

        int level = manager.getLevel(hand);
        double mult = manager.multiplier(level);

        // Résoudre l'inventaire selon le type de bloc
        Inventory inv;
        if (block.getState() instanceof Chest chest) {
            inv = chest.getInventory(); // getInventory() couvre les doubles coffres entiers
        } else if (block.getState() instanceof Barrel barrel) {
            inv = barrel.getInventory();
        } else {
            return;
        }

        double total = 0;
        int items = 0;

        // Aggregate per-category for tier progression bookkeeping. The stick
        // multiplier and the tier multiplier stack: stick × tier × base.
        long[] catItems = new long[SellCategory.values().length];
        double[] catValue = new double[SellCategory.values().length];
        Material[] catSampleMat = new Material[SellCategory.values().length];

        PlayerData data = plugin.players().get(player.getUniqueId());

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            double base = plugin.worth().worth(s);
            if (base <= 0) continue;
            SellCategory cat = SellCategory.of(s.getType());
            double tierMult = (cat != null && data != null)
                    ? plugin.sellTiers().multiplier(data, cat)
                    : 1.0;
            double v = base * mult * tierMult;
            total += v;
            items += s.getAmount();
            if (cat != null) {
                int idx = cat.ordinal();
                catItems[idx] += s.getAmount();
                catValue[idx] += v;
                if (catSampleMat[idx] == null) catSampleMat[idx] = s.getType();
            }
            inv.setItem(i, null);
        }

        if (total <= 0) {
            player.sendMessage(Msg.info("<gray>Rien a vendre dans ce coffre.</gray>"));
            return;
        }

        plugin.economy().deposit(player.getUniqueId(), total, "sell.stick.l" + level);
        plugin.getSyncManager().markDirty(player);
        if (data != null) {
            for (int idx = 0; idx < catItems.length; idx++) {
                if (catItems[idx] <= 0) continue;
                boolean levelUp = plugin.sellTiers().recordSale(player.getUniqueId(),
                        catSampleMat[idx],
                        (int) Math.min(catItems[idx], Integer.MAX_VALUE),
                        catValue[idx]);
                if (levelUp) {
                    SellCategory cat = SellCategory.values()[idx];
                    int newTier = SellTierManager.tierFor(data.tierSellCount(idx));
                    double newMult = SellTierManager.MULTIPLIERS[newTier];
                    player.sendMessage(Msg.ok("<gold>★ Palier débloqué — <yellow>"
                            + cat.displayName() + " T" + newTier
                            + "</yellow> <gray>(<green>x" + fmtMult(newMult) + "</green>)</gray>"));
                }
            }
        }
        player.sendMessage(Msg.ok("<green>Vendu <yellow>x" + items + "</yellow> pour <yellow>$"
                + Msg.money(total) + "</yellow> <gray>(stick x" + mult + ")</gray>.</green>"));
        plugin.logs().log(LogCategory.SELL, player, "stick l" + level + " items=" + items + " $" + total);
        plugin.getLogger().info("[SELL] " + player.getName() + " sell-stick L" + level + " x" + items + " pour $" + Msg.money(total) + " (stick x" + mult + ")");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    private static String fmtMult(double m) {
        if (m == Math.floor(m)) return String.format("%.0f", m);
        return String.format("%.2f", m);
    }
}
