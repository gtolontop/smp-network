package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.SellGUI;
import fr.smp.core.logging.LogCategory;
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

        switch (mode) {
            case "gui" -> {
                new SellGUI(plugin).open(p);
                return true;
            }
            case "hand" -> {
                ItemStack it = p.getInventory().getItemInMainHand();
                if (it == null || it.getType().isAir()) { p.sendMessage(Msg.err("Rien en main.")); return true; }
                double v = plugin.worth().worth(it);
                if (v <= 0) { p.sendMessage(Msg.err("Cet item n'a pas de valeur.")); return true; }
                total += v;
                items += it.getAmount();
                accumulate(breakdown, it.getType(), it.getAmount(), v);
                p.getInventory().setItemInMainHand(null);
            }
            case "all" -> {
                ItemStack[] cts = p.getInventory().getStorageContents();
                for (int i = 0; i < cts.length; i++) {
                    ItemStack s = cts[i];
                    if (s == null || s.getType().isAir()) continue;
                    double v = plugin.worth().worth(s);
                    if (v <= 0) continue;
                    total += v;
                    items += s.getAmount();
                    accumulate(breakdown, s.getType(), s.getAmount(), v);
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
}
