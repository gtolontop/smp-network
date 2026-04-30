package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.gui.SellGUI;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.sell.SellCategory;
import fr.smp.core.sell.SellTierManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class SellCommand implements CommandExecutor {

    private final SMPCore plugin;

    public SellCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String mode = args.length > 0 ? args[0].toLowerCase()
                : label.equalsIgnoreCase("sellall") ? "all" : "gui";
        double total = 0;
        int items = 0;
        Map<Material, double[]> breakdown = new LinkedHashMap<>();
        // Per-category aggregation for tier progression bookkeeping.
        long[] catItems = new long[SellCategory.values().length];
        double[] catValue = new double[SellCategory.values().length];
        Material[] catSampleMat = new Material[SellCategory.values().length];

        PlayerData data = plugin.players().get(p.getUniqueId());

        switch (mode) {
            case "gui" -> {
                new SellGUI(plugin).open(p);
                return true;
            }
            case "hand" -> {
                ItemStack it = p.getInventory().getItemInMainHand();
                if (it == null || it.getType().isAir()) { p.sendMessage(Msg.err("Rien en main.")); return true; }
                double base = plugin.worth().worth(it);
                if (base <= 0) { p.sendMessage(Msg.err("Cet item n'a pas de valeur.")); return true; }
                SellCategory cat = SellCategory.of(it.getType());
                double mult = (cat != null && data != null)
                        ? plugin.sellTiers().multiplier(data, cat)
                        : 1.0;
                double v = base * mult;
                total += v;
                items += it.getAmount();
                accumulate(breakdown, it.getType(), it.getAmount(), v);
                if (cat != null) {
                    int idx = cat.ordinal();
                    catItems[idx] += it.getAmount();
                    catValue[idx] += v;
                    catSampleMat[idx] = it.getType();
                }
                p.getInventory().setItemInMainHand(null);
            }
            case "all" -> {
                ItemStack[] cts = p.getInventory().getStorageContents();
                for (int i = 0; i < cts.length; i++) {
                    ItemStack s = cts[i];
                    if (s == null || s.getType().isAir()) continue;
                    double base = plugin.worth().worth(s);
                    if (base <= 0) continue;
                    SellCategory cat = SellCategory.of(s.getType());
                    double mult = (cat != null && data != null)
                            ? plugin.sellTiers().multiplier(data, cat)
                            : 1.0;
                    double v = base * mult;
                    total += v;
                    items += s.getAmount();
                    accumulate(breakdown, s.getType(), s.getAmount(), v);
                    if (cat != null) {
                        int idx = cat.ordinal();
                        catItems[idx] += s.getAmount();
                        catValue[idx] += v;
                        if (catSampleMat[idx] == null) catSampleMat[idx] = s.getType();
                    }
                    cts[i] = null;
                }
                p.getInventory().setStorageContents(cts);
            }
            default -> {
                p.sendMessage(Msg.err("/sell [gui|hand|all]"));
                return true;
            }
        }

        if (total <= 0) { p.sendMessage(Msg.err("Rien à vendre.")); return true; }
        double before = plugin.economy().balance(p.getUniqueId());
        plugin.economy().deposit(p.getUniqueId(), total, "sell." + mode);
        double after = plugin.economy().balance(p.getUniqueId());

        // Record progression and announce any tier-ups.
        if (data != null) {
            for (int idx = 0; idx < catItems.length; idx++) {
                if (catItems[idx] <= 0) continue;
                boolean levelUp = plugin.sellTiers().recordSale(p.getUniqueId(),
                        catSampleMat[idx],
                        (int) Math.min(catItems[idx], Integer.MAX_VALUE),
                        catValue[idx]);
                if (levelUp) {
                    SellCategory cat = SellCategory.values()[idx];
                    int newTier = SellTierManager.tierFor(data.tierSellCount(idx));
                    double newMult = SellTierManager.MULTIPLIERS[newTier];
                    p.sendMessage(Msg.ok("<gold>★ Palier débloqué — <yellow>"
                            + cat.displayName() + " T" + newTier
                            + "</yellow> <gray>(<green>x" + fmtMult(newMult) + "</green>)</gray>"));
                }
            }
        }

        plugin.getSyncManager().markDirty(p);
        p.sendMessage(Msg.ok("<green>Vendu <yellow>×" + items + "</yellow> pour <yellow>$" +
                Msg.money(total) + "</yellow>.</green>"));
        String log = formatLog(mode, items, total, before, after, breakdown);
        plugin.logs().log(LogCategory.SELL, p, log);
        plugin.getLogger().info("[SELL] " + p.getName() + " " + log);
        return true;
    }

    public static void accumulate(Map<Material, double[]> breakdown, Material mat, int amount, double value) {
        double[] agg = breakdown.computeIfAbsent(mat, k -> new double[2]);
        agg[0] += amount;
        agg[1] += value;
    }

    public static String formatLog(String mode, int items, double total,
                                   double balanceBefore, double balanceAfter,
                                   Map<Material, double[]> breakdown) {
        StringBuilder details = new StringBuilder();
        breakdown.forEach((mat, agg) -> {
            if (details.length() > 0) details.append(" | ");
            int cnt = (int) agg[0];
            double sum = agg[1];
            double per = cnt > 0 ? sum / cnt : sum;
            details.append(mat.name()).append(" x").append(cnt)
                    .append(" @$").append(String.format("%.2f", per))
                    .append("=$").append(String.format("%.2f", sum));
        });
        return "mode=" + mode + " items=" + items
                + " total=$" + String.format("%.2f", total)
                + " balance=$" + String.format("%.2f", balanceBefore)
                + "->$" + String.format("%.2f", balanceAfter)
                + " types=" + breakdown.size()
                + " breakdown={" + details + "}";
    }

    private static String fmtMult(double m) {
        if (m == Math.floor(m)) return String.format("%.0f", m);
        return String.format("%.2f", m);
    }
}
