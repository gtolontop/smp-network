package fr.smp.logger.commands;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.queue.EventQueue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StatsCommand implements CommandExecutor {

    private final SMPLogger plugin;

    public StatsCommand(SMPLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EventQueue q = plugin.queue();
        long fileBytes = plugin.db().file().length();
        long submitted = q.submitted();
        long written = q.written();
        long dropped = q.dropped();

        sender.sendMessage(Component.text("─── SMPLogger stats ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("DB file: " + (fileBytes / 1024 / 1024) + " MB", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Partitions: " + plugin.partitions().ensuredTables().size(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Events submitted/written/dropped: "
                + submitted + " / " + written + " / " + dropped, NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Queued: " + q.queued() + " | batches: " + q.batches(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Precious dedup hits / inserts: "
                + plugin.preciousStore().dedupHits() + " / " + plugin.preciousStore().inserts(), NamedTextColor.AQUA));
        return true;
    }
}
