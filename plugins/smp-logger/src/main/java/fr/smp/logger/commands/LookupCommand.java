package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.query.FilterParser;
import fr.smp.logger.query.LookupEngine;
import fr.smp.logger.query.LookupFilter;
import fr.smp.logger.util.RowFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LookupCommand implements CommandExecutor {

    private final SMPLogger plugin;
    private final FilterParser parser;
    private final LookupEngine engine;
    private final RowFormatter fmt;

    public LookupCommand(SMPLogger plugin) {
        this.plugin = plugin;
        this.parser = new FilterParser(plugin);
        this.engine = new LookupEngine(plugin);
        this.fmt = new RowFormatter(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = sender instanceof Player pl ? pl : null;
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /lookup player:<n> action:<a> item:<m> time:<7d> radius:<n> world:<w>", NamedTextColor.GRAY));
            return true;
        }
        LookupFilter f = parser.parse(p, args);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<LookupEngine.Row> rows = engine.run(f);
            if (rows.isEmpty()) {
                sender.sendMessage(Component.text("No results.", NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text("─── " + rows.size() + " results (page " + f.page + ") ───", NamedTextColor.GOLD));
            for (LookupEngine.Row r : rows) sender.sendMessage(fmt.format(r));
        });
        return true;
    }
}
