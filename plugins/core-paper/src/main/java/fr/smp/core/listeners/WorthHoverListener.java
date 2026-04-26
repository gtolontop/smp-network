package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Server-side companion to the client-side worth display.
 *
 * Responsibilities:
 *   - Send an action-bar Worth total for the item the player is holding.
 *   - Strip any legacy worth lore / PDC stamps that an earlier version baked
 *     into ItemStacks. Leaving those around breaks vanilla mechanics that
 *     compare item components (enchanting table, vaults/trial keys, recipes).
 *
 * Visual worth lore is injected client-side only — see WorthOutboundHandler.
 */
public class WorthHoverListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern WORTH_LINE = Pattern.compile("^Worth: \\$.+$");

    private final SMPCore plugin;
    private final NamespacedKey legacyLinesKey;
    private final NamespacedKey legacyStampKey;

    public WorthHoverListener(SMPCore plugin) {
        this.plugin = plugin;
        this.legacyLinesKey = new NamespacedKey(plugin, "worth_lore_lines");
        this.legacyStampKey = new NamespacedKey(plugin, "worth_stamp");
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    sendHeldTotal(p);
                    purgeInventory(p.getInventory());
                    Inventory top = p.getOpenInventory().getTopInventory();
                    if (top != null && top != p.getInventory()) purgeInventory(top);
                }
            }
        }.runTaskTimer(plugin, 17L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        purgeInventory(event.getPlayer().getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!needsPickupResync(player, event.getItem().getItemStack())) return;

        // The worth tooltip is injected client-side by rewriting outgoing item
        // packets. When the client picks up a stackable item while it already
        // shows multiple partial matching stacks, it can predict the merge
        // against the undecorated ground item and briefly create a ghost slot.
        // Re-sync on the next tick so the client view matches the server state.
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    // ── Legacy stamp cleanup ──────────────────────────────────────────
    // Items saved before the client-side display existed still carry a PDC
    // stamp + a "Worth: $X" lore line. Strip both so vanilla comparisons
    // (enchant, vault, recipe) match again.

    private void purgeInventory(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            purge(stack);
        }
    }

    public boolean purge(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;

        boolean changed = false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer legacyCount = pdc.get(legacyLinesKey, PersistentDataType.INTEGER);
        if (legacyCount != null && legacyCount > 0 && meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            for (int k = 0; k < legacyCount && !lore.isEmpty(); k++) {
                lore.remove(lore.size() - 1);
            }
            meta.lore(lore.isEmpty() ? null : lore);
            changed = true;
        }
        if (pdc.has(legacyLinesKey, PersistentDataType.INTEGER)) {
            pdc.remove(legacyLinesKey);
            changed = true;
        }
        if (pdc.has(legacyStampKey, PersistentDataType.BYTE)) {
            pdc.remove(legacyStampKey);
            changed = true;
        }

        // Strip any leftover "Worth: $X" lore line directly — covers items that
        // had their stamp removed but still carry the rendered line (e.g. from
        // a previous plugin version that stored the line without the counter).
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            boolean trimmed = lore.removeIf(line -> WORTH_LINE.matcher(plain(line)).matches());
            if (trimmed) {
                meta.lore(lore.isEmpty() ? null : lore);
                changed = true;
            }
        }

        if (changed) stack.setItemMeta(meta);
        return changed;
    }

    private boolean needsPickupResync(Player player, ItemStack pickup) {
        if (pickup == null || pickup.getType().isAir()) return false;
        if (pickup.getMaxStackSize() <= 1) return false;
        if (plugin.worth().worth(pickup.getType()) <= 0) return false;

        int partialMatches = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (stack.getAmount() >= stack.getMaxStackSize()) continue;
            if (!stack.isSimilar(pickup)) continue;

            partialMatches++;
            if (partialMatches >= 2) return true;
        }
        return false;
    }

    private static String plain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    // ── Action-bar total for held item ────────────────────────────────

    private void sendHeldTotal(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return;
        double total = plugin.worth().worth(held);
        if (total <= 0) return;
        int amount = Math.max(1, held.getAmount());
        double unit = total / amount;
        if (amount > 1) {
            p.sendActionBar(MM.deserialize(
                    "<gray>Total: <yellow>$" + Msg.money(total) +
                    "</yellow> <dark_gray>(" + amount + " × $" + Msg.money(unit) + ")</dark_gray>"));
        } else {
            p.sendActionBar(MM.deserialize(
                    "<gray>Worth: <yellow>$" + Msg.money(unit) + "</yellow></gray>"));
        }
    }
}
