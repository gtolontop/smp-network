package fr.smp.core.enchants;

import fr.smp.core.SMPCore;
import fr.smp.core.enchants.EnchantEngine.BookPayload;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles cross-cutting mechanics for custom enchants:
 *  - Enchanting table → chance to roll custom enchants on output
 *  - Anvil          → apply enchant books, fuse chestplate into elytra
 *  - Mob death      → rare drop of an enchant book
 *  - Player death   → Soulbound items stay with the player
 */
public class CustomEnchantListener implements Listener {

    private final SMPCore plugin;
    private final Random rng = new Random();
    private final Map<UUID, ItemStack> pendingSoulboundKeeps = new HashMap<>();

    public CustomEnchantListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    // ═══ Enchanting table ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        if (hasCustomEnchantLock(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        ItemStack item = e.getItem();
        int cost = e.getExpLevelCost();
        if (item == null || item.getType() == Material.AIR) return;
        if (hasCustomEnchantLock(item)) {
            e.setCancelled(true);
            return;
        }

        // Defensive filter: strip any vanilla enchant the table tries to put on an item
        // it doesn't actually apply to (e.g. Feather Falling on a hoe). Paper's enchant
        // table is usually correct but plugins and quirks can slip bad offers through.
        // Do not run this filter for plain books: vanilla enchanting stores enchants
        // onto the resulting ENCHANTED_BOOK even though they do not directly enchant BOOK.
        if (item.getType() != Material.BOOK) {
            e.getEnchantsToAdd().entrySet().removeIf(entry ->
                    !entry.getKey().canEnchantItem(item));
        }

        double customChance = tableCustomChance(cost);
        double overcapChance = tableOvercapChance(cost);

        boolean rollCustom  = rng.nextDouble() < customChance;
        boolean rollOvercap = rng.nextDouble() < overcapChance;
        if (!rollCustom && !rollOvercap) return;

        // Overcaps: add to the enchants-to-apply map NOW so the preview animation
        // and the tooltip both show the over-max level. Vanilla applies the level
        // via ignoreLevelRestriction because we call addUnsafeEnchantment semantics
        // through the event map (Bukkit puts whatever Integer we give).
        CustomEnchant overcapPick = null;
        if (rollOvercap) {
            overcapPick = pickForItem(item, CustomEnchant.Flavour.OVERCAP);
            if (overcapPick != null) {
                e.getEnchantsToAdd().put(overcapPick.vanilla(), overcapPick.maxLevel());
            }
        }

        CustomEnchant customPick = null;
        int customLevel = 0;
        if (rollCustom) {
            customPick = pickForItem(item, CustomEnchant.Flavour.CUSTOM);
            if (customPick != null) {
                int max = customPick.maxLevel();
                customLevel = rollWeightedLevel(max);
            }
        }

        final CustomEnchant overcapFinal = overcapPick;
        final CustomEnchant customFinal = customPick;
        final int customLevelFinal = customLevel;

        // Custom PDC enchants still need a scheduled write (item isn't mutated yet).
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (customFinal != null) {
                EnchantEngine.apply(item, customFinal, customLevelFinal);
                notify(e.getEnchanter(), customFinal, customLevelFinal);
            }
            if (overcapFinal != null) {
                EnchantEngine.renderLore(item);
                EnchantEngine.setVanillaEnchantUnsafe(item, overcapFinal.vanilla(), overcapFinal.maxLevel());
                notify(e.getEnchanter(), overcapFinal, overcapFinal.maxLevel());
            }
            plugin.getSyncManager().markDirty(e.getEnchanter());
        });
    }

    private double tableCustomChance(int cost) {
        double base = plugin.getConfig().getDouble("customenchants.table.custom-chance-at-30", 0.08);
        return base * Math.pow(cost / 30.0, 2);
    }

    private boolean hasCustomEnchantLock(ItemStack item) {
        return item != null && !EnchantEngine.customOn(item).isEmpty();
    }

    private double tableOvercapChance(int cost) {
        double base = plugin.getConfig().getDouble("customenchants.table.overcap-chance-at-30", 0.03);
        return base * Math.pow(cost / 30.0, 2);
    }

    private CustomEnchant pickForItem(ItemStack item, CustomEnchant.Flavour flavour) {
        java.util.List<CustomEnchant> pool = new java.util.ArrayList<>();
        for (CustomEnchant ce : CustomEnchant.values()) {
            if (ce.flavour() != flavour) continue;
            if (!ce.target().matches(item.getType())) continue;
            if (EnchantEngine.levelOf(item, ce) >= ce.maxLevel()) continue;
            pool.add(ce);
        }
        if (pool.isEmpty()) return null;
        return pool.get(rng.nextInt(pool.size()));
    }

    private void notify(Player p, CustomEnchant ce, int level) {
        if (p == null || !p.isOnline()) return;
        p.sendMessage(fr.smp.core.utils.Msg.ok("<light_purple>Enchant rare obtenu :</light_purple> " +
                "<gradient:#67e8f9:#a78bfa>" + ce.displayName() + "</gradient> " +
                "<light_purple>" + CustomEnchant.roman(level) + "</light_purple>"));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);
    }

    private int rollWeightedLevel(int maxLevel) {
        if (maxLevel <= 1) return 1;
        double r = rng.nextDouble();
        if (r < 0.65) return 1;
        if (r < 0.88) return Math.min(2, maxLevel);
        if (r < 0.97) return Math.min(3, maxLevel);
        return maxLevel;
    }

    // ═══ Anvil ══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv = e.getInventory();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);
        if (first == null || second == null) return;
        if (first.getType() == Material.AIR || second.getType() == Material.AIR) return;

        // ── Chestplate + Elytra fusion ──
        // Output is the CHESTPLATE (real armor visual) with the minecraft:glider
        // data component so the player still glides. Both enchant sets merge.
        ItemStack elytra = null, chest = null;
        if (first.getType() == Material.ELYTRA && second.getType().name().endsWith("_CHESTPLATE")) {
            elytra = first; chest = second;
        } else if (second.getType() == Material.ELYTRA && first.getType().name().endsWith("_CHESTPLATE")) {
            elytra = second; chest = first;
        }
        if (chest != null) {
            ItemStack fused = EnchantEngine.fuseElytraOntoChestplate(chest, elytra);
            if (fused != null) {
                e.setResult(fused);
                int cost = plugin.getConfig().getInt("customenchants.anvil.elytra-fusion-cost", 15);
                // Re-apply next tick because Bukkit resets the repair cost display after the event.
                Bukkit.getScheduler().runTask(plugin, () -> e.getView().setRepairItemCountCost(cost));
                return;
            }
        }

        // ── Enchant book → item ──
        ItemStack target = null, book = null;
        if (EnchantEngine.readBook(first) != null) { book = first; target = second; }
        else if (EnchantEngine.readBook(second) != null) { book = second; target = first; }
        if (book != null) {
            ItemStack merged = (target == null) ? null : EnchantEngine.mergeBookOnto(target, book);
            if (merged == null) {
                if (target != null) {
                    BookPayload payload = EnchantEngine.readBook(book);
                    if (payload != null && EnchantEngine.levelOf(target, payload.enchant()) >= payload.level()) {
                        e.setResult(null);
                        return;
                    }
                }
                e.setResult(null);
                return;
            }
            e.setResult(merged);
            int cost = plugin.getConfig().getInt("customenchants.anvil.book-cost", 6);
            Bukkit.getScheduler().runTask(plugin, () -> e.getView().setRepairItemCountCost(cost));
            return;
        }

        // ── Item + item: merge custom PDC enchants + preserve overcap levels ──
        // Vanilla anvil only merges vanilla enchants AND caps them at maxLevel, so
        // PDC customs are dropped on the second item and overcap levels (Eff VI,
        // Sharp VI, Fortune IV, ...) are silently downgraded on both. We re-inject
        // both onto the result so Eff VI + Carrière III truly gives Eff VI +
        // Carrière III, not Eff V + Carrière III.
        java.util.Map<CustomEnchant, Integer> cus1 = EnchantEngine.customOn(first);
        java.util.Map<CustomEnchant, Integer> cus2 = EnchantEngine.customOn(second);
        java.util.Map<CustomEnchant, Integer> customs = new java.util.LinkedHashMap<>(cus1);
        for (var en : cus2.entrySet()) customs.merge(en.getKey(), en.getValue(), Math::max);

        java.util.Map<CustomEnchant, Integer> overcaps = new java.util.LinkedHashMap<>();
        collectOvercaps(first, overcaps);
        collectOvercaps(second, overcaps);

        // Vanilla → overcap promotion: when both inputs share a vanilla enchant
        // at the same level X and an overcap exists at X+1, promote.  Covers
        // Prot IV+IV → Prot V (vanilla anvil silently caps the result), and
        // Prot V+V → Prot VI (Sharp V+V → VI, Eff V+V → VI, ...).
        java.util.Map<CustomEnchant, Integer> promotions = collectPromotions(first, second);

        if (customs.isEmpty() && overcaps.isEmpty() && promotions.isEmpty()) return;

        ItemStack base = e.getResult();
        boolean synth = (base == null || base.getType() == Material.AIR);
        if (synth) {
            // Vanilla refused (two undamaged items with no vanilla enchants to merge).
            // Only synthesize when both are the same item type.
            if (first.getType() != second.getType()) return;
            base = first.clone();
            ItemMeta bm = base.getItemMeta();
            ItemMeta sm = second.getItemMeta();
            if (bm != null && sm != null) {
                for (var en : sm.getEnchants().entrySet()) {
                    bm.addEnchant(en.getKey(),
                            Math.max(bm.getEnchantLevel(en.getKey()), en.getValue()), true);
                }
                base.setItemMeta(bm);
            }
        }

        ItemStack out = base.clone();
        for (var en : overcaps.entrySet()) {
            if (en.getKey().target().matches(out.getType())) {
                EnchantEngine.apply(out, en.getKey(), en.getValue());
            }
        }
        for (var en : customs.entrySet()) {
            if (en.getKey().target().matches(out.getType())) {
                EnchantEngine.apply(out, en.getKey(), en.getValue());
            }
        }
        // Apply promotions LAST so they override any preserved overcap that the
        // promotion supersedes (e.g. Prot V → Prot VI).
        applyPromotions(out, promotions);
        e.setResult(out);
        if (synth) {
            int cost = plugin.getConfig().getInt("customenchants.anvil.merge-cost", 4);
            Bukkit.getScheduler().runTask(plugin, () -> e.getView().setRepairItemCountCost(cost));
        }
    }

    /** Records overcap vanilla enchants on {@code item} into {@code out}, keeping the max level seen. */
    private static void collectOvercaps(ItemStack item, java.util.Map<CustomEnchant, Integer> out) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        for (CustomEnchant ce : CustomEnchant.values()) {
            if (!ce.isOvercap()) continue;
            int lvl = meta.getEnchantLevel(ce.vanilla());
            if (lvl >= ce.maxLevel()) out.merge(ce, lvl, Math::max);
        }
    }

    /**
     * Detects vanilla → overcap promotions for two anvil inputs.  When both
     * items carry the same vanilla enchant at the same level X with X already
     * at (or above) the vanilla cap, and an overcap CustomEnchant exists at
     * X+1, returns a map containing the promoted overcap.  Below the vanilla
     * cap we leave it alone — vanilla anvil already promotes correctly.
     */
    private static java.util.Map<CustomEnchant, Integer> collectPromotions(ItemStack first, ItemStack second) {
        java.util.Map<CustomEnchant, Integer> out = new java.util.LinkedHashMap<>();
        if (first == null || second == null || !first.hasItemMeta() || !second.hasItemMeta()) return out;
        java.util.Map<Enchantment, Integer> map1 = enchantsAny(first.getItemMeta());
        java.util.Map<Enchantment, Integer> map2 = enchantsAny(second.getItemMeta());
        for (var entry : map1.entrySet()) {
            Enchantment ench = entry.getKey();
            int lvl1 = entry.getValue();
            Integer lvl2 = map2.get(ench);
            if (lvl2 == null || lvl1 != lvl2) continue;
            if (lvl1 < ench.getMaxLevel()) continue;
            int promoted = lvl1 + 1;
            for (CustomEnchant ce : CustomEnchant.values()) {
                if (!ce.isOvercap()) continue;
                if (!ce.vanilla().equals(ench)) continue;
                if (ce.maxLevel() != promoted) continue;
                out.put(ce, promoted);
                break;
            }
        }
        return out;
    }

    /** Returns regular enchants, or stored enchants for an enchanted book. */
    private static java.util.Map<Enchantment, Integer> enchantsAny(ItemMeta meta) {
        if (meta instanceof EnchantmentStorageMeta esm) return esm.getStoredEnchants();
        return meta.getEnchants();
    }

    private static void applyPromotions(ItemStack out, java.util.Map<CustomEnchant, Integer> promotions) {
        if (promotions.isEmpty() || out == null) return;
        boolean isBook = out.getItemMeta() instanceof EnchantmentStorageMeta;
        for (var p : promotions.entrySet()) {
            CustomEnchant ce = p.getKey();
            int level = p.getValue();
            if (isBook) {
                // Book result: write the overcap level via the stored-enchants
                // data component (the EnchantmentStorageMeta API clamps through
                // the codec in Paper 26.x, dropping overcap levels).
                EnchantEngine.setStoredEnchantUnsafe(out, ce.vanilla(), level);
            } else if (ce.target().matches(out.getType())) {
                EnchantEngine.apply(out, ce, level);
            }
        }
    }

    // ═══ Mob drops ══════════════════════════════════════════════════════

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!plugin.getConfig().getBoolean("customenchants.mob-drop.enabled", true)) return;
        if (!(e.getEntity() instanceof Monster)) return;
        if (e.getEntity().getKiller() == null) return;
        if (plugin.spawners() != null && plugin.spawners().isXpMob(e.getEntity())) return;

        double base = plugin.getConfig().getDouble("customenchants.mob-drop.chance", 0.015);
        double roll = rng.nextDouble();
        if (roll >= base) return;

        boolean overcap = rng.nextDouble() < plugin.getConfig()
                .getDouble("customenchants.mob-drop.overcap-ratio", 0.20);
        CustomEnchant ce = rollRandom(overcap ? CustomEnchant.Flavour.OVERCAP : CustomEnchant.Flavour.CUSTOM);
        if (ce == null) return;

        int level = overcap ? ce.maxLevel() : rollWeightedLevel(ce.maxLevel());

        // Boss mobs always drop at max level.
        EntityType t = e.getEntityType();
        if (t == EntityType.WITHER || t == EntityType.ENDER_DRAGON
                || t == EntityType.WARDEN || t == EntityType.ELDER_GUARDIAN) {
            level = ce.maxLevel();
        }

        e.getDrops().add(EnchantEngine.book(ce, level));
    }

    private CustomEnchant rollRandom(CustomEnchant.Flavour flavour) {
        java.util.List<CustomEnchant> pool = new java.util.ArrayList<>();
        for (CustomEnchant ce : CustomEnchant.values()) {
            if (ce.flavour() == flavour) pool.add(ce);
        }
        if (pool.isEmpty()) return null;
        return pool.get(rng.nextInt(pool.size()));
    }

    // ═══ Soulbound ══════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        pendingSoulboundKeeps.remove(e.getPlayer().getUniqueId());

        java.util.List<ItemStack> soulboundDrops = new java.util.ArrayList<>();
        for (ItemStack drop : e.getDrops()) {
            if (drop == null) continue;
            if (EnchantEngine.levelOf(drop, CustomEnchant.SOUL) > 0) {
                soulboundDrops.add(drop);
            }
        }

        if (soulboundDrops.isEmpty()) {
            return;
        }

        ItemStack keptDrop = soulboundDrops.get(rng.nextInt(soulboundDrops.size())).clone();
        pendingSoulboundKeeps.put(e.getPlayer().getUniqueId(), keptDrop.clone());
        e.getItemsToKeep().add(keptDrop.clone());

        java.util.Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            if (sameSoulboundItem(it.next(), keptDrop)) {
                it.remove();
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        UUID playerId = e.getPlayer().getUniqueId();
        ItemStack pending = pendingSoulboundKeeps.remove(playerId);
        if (pending == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = e.getPlayer();
            if (!player.isOnline()) {
                return;
            }
            if (alreadyRestored(player, pending)) {
                return;
            }

            var leftover = player.getInventory().addItem(pending.clone());
            for (ItemStack remaining : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
            player.updateInventory();
        });
    }

    private boolean alreadyRestored(Player player, ItemStack expected) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (sameSoulboundItem(stack, expected)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (sameSoulboundItem(stack, expected)) {
                return true;
            }
        }
        return sameSoulboundItem(player.getInventory().getItemInOffHand(), expected);
    }

    private boolean sameSoulboundItem(ItemStack left, ItemStack right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getInventory().getType() == InventoryType.ENCHANTING) {
            if (e.getRawSlot() == 0) {
                ItemStack item = e.getCurrentItem();
                if (item != null && !EnchantEngine.customOn(item).isEmpty()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> EnchantEngine.renderLore(item), 1L);
                }
            }
        }
        if (e.getInventory() instanceof AnvilInventory) {
            if (e.getRawSlot() == 2 && e.getCurrentItem() != null) {
                ItemStack result = e.getCurrentItem();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ItemStack cursor = player.getItemOnCursor();
                    if (cursor != null && !cursor.getType().isAir()) {
                        EnchantEngine.renderLore(cursor);
                    }
                }, 1L);
            }
        }
    }
}
