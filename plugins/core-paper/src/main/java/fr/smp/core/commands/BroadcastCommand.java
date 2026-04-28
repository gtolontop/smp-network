package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class BroadcastCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] ANNOUNCE_AT = {300, 120, 60, 30, 20, 10, 5, 4, 3, 2, 1};

    private static final String BC_TITLE = "<gold><bold>📢 ANNONCE</bold></gold>";
    private static final int BC_FADE_IN = 10;
    private static final int BC_STAY = 80;
    private static final int BC_FADE_OUT = 20;

    private final SMPCore plugin;
    private BukkitTask countdownTask;
    private int countdownSeconds;

    public BroadcastCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Msg.info("<gray>/bc <message></gray>"));
            sender.sendMessage(Msg.info("<gray>/bc restart [secondes]</gray>"));
            sender.sendMessage(Msg.info("<gray>/bc cancel</gray>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "restart", "reboot" -> {
                int seconds = 60;
                if (args.length >= 2) {
                    try {
                        seconds = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Msg.err("Durée invalide."));
                        return true;
                    }
                }
                startRestart(seconds);
                sender.sendMessage(Msg.ok("Redémarrage dans <yellow>" + formatTime(seconds) + "</yellow>."));
            }
            case "cancel", "abort" -> {
                if (countdownTask == null || countdownTask.isCancelled()) {
                    sender.sendMessage(Msg.err("Aucun redémarrage en cours."));
                } else {
                    countdownTask.cancel();
                    countdownTask = null;
                    broadcastChat(MM.deserialize(Msg.PREFIX + "<green>✔</green> <green>Redémarrage annulé.</green>"));
                    sender.sendMessage(Msg.ok("Redémarrage annulé."));
                }
            }
            default -> {
                String msg = String.join(" ", args);
                broadcastNetwork(Msg.PREFIX + msg, BC_TITLE, msg, BC_FADE_IN, BC_STAY, BC_FADE_OUT);
                sender.sendMessage(Msg.ok("Diffusé sur tout le réseau."));
            }
        }
        return true;
    }

    private void startRestart(int seconds) {
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
        countdownSeconds = seconds;
        announceRestart(seconds);
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            countdownSeconds--;
            if (countdownSeconds <= 0) {
                countdownTask.cancel();
                countdownTask = null;
                broadcastChat(MM.deserialize(Msg.PREFIX + "<red><bold>Redémarrage du serveur...</bold></red>"));
                broadcastTitle("<red><bold>RESTART</bold></red>", "<gray>À tout de suite !</gray>", 10, 60, 20);
                Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 60L);
                return;
            }
            for (int mark : ANNOUNCE_AT) {
                if (countdownSeconds == mark) {
                    announceRestart(countdownSeconds);
                    break;
                }
            }
        }, 20L, 20L);
    }

    private void announceRestart(int seconds) {
        String t = formatTime(seconds);
        broadcastChat(MM.deserialize(
            Msg.PREFIX + "<yellow>⚠</yellow> <white>Redémarrage dans <yellow><bold>" + t + "</bold></yellow>.</white>"
        ));
        if (seconds <= 30) {
            broadcastTitle(
                "<yellow><bold>REDÉMARRAGE</bold></yellow>",
                "<gray>dans " + t + "</gray>",
                10, 40, 20
            );
        }
    }

    private void broadcastChat(Component msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    /** Diffuse chat + title localement ET sur tous les autres backends via Velocity. */
    private void broadcastNetwork(String chatRendered, String titleRendered, String subtitleRendered,
                                  int fadeIn, int stay, int fadeOut) {
        Component chat = MM.deserialize(chatRendered);
        broadcastChat(chat);
        broadcastTitle(titleRendered, subtitleRendered, fadeIn, stay, fadeOut);
        if (plugin.getMessageChannel() != null) {
            plugin.getMessageChannel().sendBroadcast(chatRendered, titleRendered, subtitleRendered, fadeIn, stay, fadeOut);
        }
    }

    private void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title t = Title.title(
            MM.deserialize(title),
            MM.deserialize(subtitle),
            Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
            )
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(t));
    }

    private static String formatTime(int seconds) {
        if (seconds >= 60) {
            int m = seconds / 60, s = seconds % 60;
            return s == 0 ? m + "min" : m + "min " + s + "s";
        }
        return seconds + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) {
            var out = new ArrayList<String>();
            String pref = args[0].toLowerCase();
            for (String s : List.of("restart", "cancel")) if (s.startsWith(pref)) out.add(s);
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("restart")) {
            return List.of("30", "60", "120", "300");
        }
        return List.of();
    }
}
