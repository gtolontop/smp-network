package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Logs every command issued, with optional redaction for password commands.
 */
public class CommandModule implements Listener {

    private final SMPLogger plugin;
    private final Set<String> redacted;

    public CommandModule(SMPLogger plugin) {
        this.plugin = plugin;
        List<String> conf = plugin.getConfig().getStringList("chat.redact-commands");
        this.redacted = new HashSet<>();
        for (String s : conf) redacted.add(s.toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        String full = e.getMessage();
        if (full.length() > 1 && full.charAt(0) == '/') full = full.substring(1);

        int sp = full.indexOf(' ');
        String head = sp < 0 ? full : full.substring(0, sp);
        String headLow = head.toLowerCase(Locale.ROOT);
        String stored;
        if (redacted.contains(headLow)) {
            stored = head + " [REDACTED]";
        } else {
            stored = full;
            if (stored.length() > 4096) stored = stored.substring(0, 4096);
        }

        EventBuilder.begin(plugin)
                .action(Action.COMMAND)
                .actor(e.getPlayer())
                .at(e.getPlayer())
                .text(stored)
                .submit();
    }
}
