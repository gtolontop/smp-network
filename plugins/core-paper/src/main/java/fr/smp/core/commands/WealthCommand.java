package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.WealthGUI;
import fr.smp.core.managers.WealthManager;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WealthCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public WealthCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }
        if (args.length == 0) {
            new WealthGUI(plugin).open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("buy") || sub.equals("achat")) {
            if (args.length < 2) {
                player.sendMessage(Msg.err("/wealth buy <id>"));
                return true;
            }
            WealthManager.WealthItem item = WealthManager.WealthItem.byId(args[1]);
            if (item == null) {
                player.sendMessage(Msg.err("Achat inconnu."));
                return true;
            }
            plugin.wealth().purchase(player, item);
            return true;
        }
        if (sub.equals("top")) {
            int rank = 1;
            player.sendMessage(Msg.mm("<gold><bold>Top argent dépensé utile</bold></gold>"));
            for (var entry : plugin.wealth().topPersonalSpenders(10)) {
                player.sendMessage(Msg.mm("<gray>#" + rank++ + " <white>" + entry.name()
                        + "</white> <green>$" + Msg.money(entry.amount()) + "</green></gray>"));
            }
            return true;
        }
        new WealthGUI(plugin).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("buy", "top"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            List<String> ids = new ArrayList<>();
            for (WealthManager.WealthItem item : WealthManager.WealthItem.values()) ids.add(item.id());
            return filter(ids, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String raw) {
        String prefix = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (prefix.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(value);
        }
        return out;
    }
}
