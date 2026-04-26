package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.skins.SkinManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SkinCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public SkinCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission required."));
            return true;
        }
        if (plugin.skins() == null) {
            sender.sendMessage(Msg.err("Skin system unavailable."));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> handleInfo(sender, args);
            case "reset" -> handleReset(sender, args);
            case "random" -> handleRandom(sender, args);
            case "take" -> handleTake(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /skin info <player>"));
            return;
        }
        String target = args[1];
        if (!SkinManager.isValidMinecraftName(target)) {
            sender.sendMessage(Msg.err("Invalid Minecraft name."));
            return;
        }
        plugin.skins().lookupInfoAsync(target, info -> {
            String displayTarget = info.targetName();
            if (info.premiumOnly()) {
                sender.sendMessage(Msg.err("<white>" + displayTarget + "</white> is a premium-only account. Cracked skin overrides do not apply."));
                return;
            }
            if (info.currentSkin() == null) {
                sender.sendMessage(Msg.info("No saved cracked skin for <white>" + displayTarget
                        + "</white>. Default preview: <white>" + info.defaultOwnerPreview() + "</white>."));
                return;
            }
            String mode = switch (info.currentSkin().mode()) {
                case DEFAULT -> "default";
                case RANDOM -> "random";
                case TAKEN -> "taken";
            };
            sender.sendMessage(Msg.info("Skin of <white>" + displayTarget + "</white>: "
                    + "<yellow>" + mode + "</yellow> via <white>" + info.currentSkin().ownerName() + "</white>."));
        });
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /skin reset <player>"));
            return;
        }
        String target = args[1];
        if (!SkinManager.isValidMinecraftName(target)) {
            sender.sendMessage(Msg.err("Invalid Minecraft name."));
            return;
        }
        if (rejectPremiumOnlineTarget(sender, target)) {
            return;
        }
        sender.sendMessage(Msg.info("Restoring the default cracked skin for <white>" + target + "</white>..."));
        plugin.skins().resetAsync(target, result -> {
            SkinManager.OperationResult applied = plugin.skins().applyNowIfPossible(result);
            if (!applied.success()) {
                sender.sendMessage(Msg.err(applied.message()));
                return;
            }
            sender.sendMessage(Msg.ok(applied.message()
                    + (applied.appliedNow() ? " Applied immediately." : " Saved for the next cracked join.")));
        });
    }

    private void handleRandom(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("Usage: /skin random <player>"));
            return;
        }
        String target = args[1];
        if (!SkinManager.isValidMinecraftName(target)) {
            sender.sendMessage(Msg.err("Invalid Minecraft name."));
            return;
        }
        if (rejectPremiumOnlineTarget(sender, target)) {
            return;
        }
        sender.sendMessage(Msg.info("Rolling a random skin for <white>" + target + "</white>..."));
        plugin.skins().setRandomAsync(target, result -> {
            SkinManager.OperationResult applied = plugin.skins().applyNowIfPossible(result);
            if (!applied.success()) {
                sender.sendMessage(Msg.err(applied.message()));
                return;
            }
            sender.sendMessage(Msg.ok(applied.message()
                    + (applied.appliedNow() ? " Applied immediately." : " Saved for the next cracked join.")));
        });
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.err("Usage: /skin take <player> <premiumName>"));
            return;
        }
        String target = args[1];
        String premiumName = args[2];
        if (!SkinManager.isValidMinecraftName(target) || !SkinManager.isValidMinecraftName(premiumName)) {
            sender.sendMessage(Msg.err("Invalid Minecraft name."));
            return;
        }
        if (rejectPremiumOnlineTarget(sender, target)) {
            return;
        }
        sender.sendMessage(Msg.info("Taking the skin of <white>" + premiumName + "</white> for <white>" + target + "</white>..."));
        plugin.skins().takeAsync(target, premiumName, result -> {
            SkinManager.OperationResult applied = plugin.skins().applyNowIfPossible(result);
            if (!applied.success()) {
                sender.sendMessage(Msg.err(applied.message()));
                return;
            }
            sender.sendMessage(Msg.ok(applied.message()
                    + (applied.appliedNow() ? " Applied immediately." : " Saved for the next cracked join.")));
        });
    }

    private boolean rejectPremiumOnlineTarget(CommandSender sender, String targetName) {
        Player online = plugin.skins().findOnlinePlayer(targetName);
        if (online != null && plugin.skins().isPremiumSession(online)) {
            sender.sendMessage(Msg.err("This player is currently connected as premium."));
            return true;
        }
        return false;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Msg.info("<gold>Skin</gold> — /skin info|reset|random|take"));
        sender.sendMessage(Msg.mm("<dark_gray>/skin info <player></dark_gray>"));
        sender.sendMessage(Msg.mm("<dark_gray>/skin reset <player></dark_gray>"));
        sender.sendMessage(Msg.mm("<dark_gray>/skin random <player></dark_gray>"));
        sender.sendMessage(Msg.mm("<dark_gray>/skin take <player> <premiumName></dark_gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin") || plugin.skins() == null) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("info", "reset", "random", "take"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info") || sub.equals("reset") || sub.equals("random") || sub.equals("take")) {
                return onlinePlayerNames(args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("take")) {
            return onlinePlayerNames(args[2]);
        }
        return List.of();
    }

    private List<String> onlinePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }
}
