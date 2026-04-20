package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.WorldBorderGUI;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WorldBorderCommand implements CommandExecutor {

    private final SMPCore plugin;

    public WorldBorderCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("/wb set <monde> <taille> | center <monde> <x> <z> | rtp <monde> <radius>");
                return true;
            }
            new WorldBorderGUI(plugin).open(p);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "set" -> {
                if (args.length < 3) { sender.sendMessage(Msg.err("/wb set <monde> <taille>")); return true; }
                World w = Bukkit.getWorld(args[1]);
                if (w == null) { sender.sendMessage(Msg.err("Monde introuvable.")); return true; }
                double size;
                try { size = Double.parseDouble(args[2]); } catch (NumberFormatException e) {
                    sender.sendMessage(Msg.err("Taille invalide.")); return true;
                }
                plugin.worldborders().setBorder(w, size, w.getWorldBorder().getCenter().getX(),
                        w.getWorldBorder().getCenter().getZ());
                sender.sendMessage(Msg.ok("<green>Worldborder de <aqua>" + w.getName() + "</aqua> = <yellow>" +
                        (int) size + "</yellow>.</green>"));
                plugin.logs().log(LogCategory.ADMIN, "wb set " + w.getName() + " " + size);
            }
            case "center" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/wb center <monde> <x> <z>")); return true; }
                World w = Bukkit.getWorld(args[1]);
                if (w == null) { sender.sendMessage(Msg.err("Monde introuvable.")); return true; }
                double x, z;
                try { x = Double.parseDouble(args[2]); z = Double.parseDouble(args[3]); } catch (NumberFormatException e) {
                    sender.sendMessage(Msg.err("Coords invalides.")); return true;
                }
                plugin.worldborders().setBorder(w, w.getWorldBorder().getSize(), x, z);
                sender.sendMessage(Msg.ok("<green>Centre de <aqua>" + w.getName() + "</aqua> = <yellow>" +
                        (int) x + ", " + (int) z + "</yellow>.</green>"));
            }
            case "rtp" -> {
                if (args.length < 3) { sender.sendMessage(Msg.err("/wb rtp <monde> <radius>")); return true; }
                double r;
                try { r = Double.parseDouble(args[2]); } catch (NumberFormatException e) {
                    sender.sendMessage(Msg.err("Radius invalide.")); return true;
                }
                plugin.getConfig().set("rtp.radius." + args[1], r);
                plugin.saveConfig();
                sender.sendMessage(Msg.ok("<green>RTP radius <aqua>" + args[1] +
                        "</aqua> = <yellow>" + (int) r + "</yellow>.</green>"));
            }
            default -> sender.sendMessage(Msg.err("Sous-commandes: set | center | rtp"));
        }
        return true;
    }
}
