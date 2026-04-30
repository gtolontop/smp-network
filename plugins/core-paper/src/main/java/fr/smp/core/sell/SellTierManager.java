package fr.smp.core.sell;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import org.bukkit.Material;

import java.util.UUID;

/**
 * Tier-sell snowball system.
 *
 * Each of the 9 {@link SellCategory} categories has its own counter that counts
 * the cumulative number of items the player has sold in that category, across
 * any selling channel (/sell gui, /sell hand, /sell all, /sellauto, sell-sticks).
 *
 * The counter feeds a 13-step multiplier ladder (T0 = no bonus, T12 = 10×).
 * Multipliers are applied on top of base worth at sale time. Sell-sticks
 * multiply on top of the tier multiplier (the two stack), so a fully-snowballed
 * miner with a level-3 stick can hit 25× on diamonds.
 *
 * Counters are stored on {@link PlayerData} so they piggyback on the regular
 * player-data autosave. We deliberately use a long array indexed by ordinal —
 * keeps the on-disk schema flat (one column per category), and adding a 10th
 * category later only takes one ALTER TABLE.
 */
public final class SellTierManager {

    /**
     * Tier thresholds (cumulative items sold). Index = tier number (0..12).
     * THRESHOLDS[t] = minimum items to unlock tier t.
     */
    public static final long[] THRESHOLDS = {
            0L,
            250L,
            1_000L,
            5_000L,
            25_000L,
            100_000L,
            500_000L,
            2_000_000L,
            10_000_000L,
            50_000_000L,
            250_000_000L,
            1_000_000_000L,
            5_000_000_000L
    };

    /** Multiplier for each tier (matches {@link #THRESHOLDS}). */
    public static final double[] MULTIPLIERS = {
            1.00, 1.05, 1.12, 1.20, 1.35, 1.55, 1.80,
            2.10, 2.50, 3.20, 5.00, 8.00, 10.00
    };

    public static final int MAX_TIER = THRESHOLDS.length - 1;

    private final SMPCore plugin;

    public SellTierManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    /** Tier (0..MAX_TIER) reached for the given cumulative count. */
    public static int tierFor(long count) {
        if (count < 0) return 0;
        int t = 0;
        for (int i = MAX_TIER; i >= 0; i--) {
            if (count >= THRESHOLDS[i]) {
                t = i;
                break;
            }
        }
        return t;
    }

    /** Multiplier for the given cumulative count. 1.00 if no progression. */
    public static double multiplierFor(long count) {
        return MULTIPLIERS[tierFor(count)];
    }

    /** Multiplier currently active on the given category for the player. */
    public double multiplier(PlayerData data, SellCategory cat) {
        if (data == null || cat == null) return 1.0;
        return multiplierFor(data.tierSellCount(cat.ordinal()));
    }

    /** Multiplier currently active on the given material for the player. */
    public double multiplier(PlayerData data, Material mat) {
        SellCategory cat = SellCategory.of(mat);
        return cat == null ? 1.0 : multiplier(data, cat);
    }

    /**
     * Record a sale: increments the per-category counter and money earned.
     * The caller is responsible for actually crediting the player — this only
     * tracks progression. Returns true if the sale crossed a tier boundary
     * (so the caller can announce a level-up).
     */
    public boolean recordSale(UUID uuid, Material mat, int amount, double finalValue) {
        if (uuid == null || mat == null || amount <= 0) return false;
        PlayerData data = plugin.players().get(uuid);
        if (data == null) return false;
        SellCategory cat = SellCategory.of(mat);
        if (cat == null) return false; // item is sellable but not part of any tier
        int idx = cat.ordinal();
        long before = data.tierSellCount(idx);
        long after = before + amount;
        // Saturate to avoid overflow if a server runs for a decade.
        if (after < before) after = Long.MAX_VALUE;
        int beforeTier = tierFor(before);
        int afterTier = tierFor(after);
        data.setTierSellCount(idx, after);
        data.addTierMoneyEarned(idx, finalValue);
        return afterTier > beforeTier;
    }

    /**
     * Convenience: "next tier" thresholds for GUI display. Returns -1 for the
     * last tier (no further unlock).
     */
    public static long nextThreshold(int currentTier) {
        if (currentTier >= MAX_TIER) return -1;
        return THRESHOLDS[currentTier + 1];
    }

    public static double nextMultiplier(int currentTier) {
        if (currentTier >= MAX_TIER) return MULTIPLIERS[MAX_TIER];
        return MULTIPLIERS[currentTier + 1];
    }
}
