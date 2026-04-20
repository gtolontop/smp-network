package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.WarpsGUI;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.managers.WarpManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WarpCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final String mode; // "warp", "warps", "setwarp", "delwarp"

    public WarpCommand(SMPCore plugin, String mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (mode) {
            case "warps" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
                new WarpsGUI(plugin).open(p);
            }
            case "warp" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length == 0) { new WarpsGUI(plugin).open(p); return true; }
                WarpManager.Warp w = plugin.warps().get(args[0]);
                if (w == null) { p.sendMessage(Msg.err("Warp introuvable.")); return true; }
                teleportWarp(p, w);
            }
            case "setwarp" -> {
                if (!(sender instanceof Player p) || !p.hasPermission("smp.admin")) {
                    sender.sendMessage(Msg.err("Permission refusée.")); return true;
                }
                if (args.length == 0) { p.sendMessage(Msg.err("/setwarp <nom> [icon] [desc...]")); return true; }
                Material icon = Material.COMPASS;
                StringBuilder desc = new StringBuilder();
                if (args.length >= 2) {
                    Material m = Material.matchMaterial(args[1]);
                    if (m != null) icon = m;
                }
                for (int i = 2; i < args.length; i++) desc.append(args[i]).append(" ");
                plugin.warps().set(args[0], p.getLocation(), icon, desc.toString().trim(), p.getUniqueId());
                p.sendMessage(Msg.ok("<green>Warp <aqua>" + args[0] + "</aqua> créé.</green>"));
            }
            case "delwarp" -> {
                if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return true; }
                if (args.length == 0) {
                    if (!(sender instanceof Player p)) { sender.sendMessage(Msg.err("/delwarp <nom>")); return true; }
                    new WarpsGUI(plugin, true).open(p);
                    return true;
                }
                boolean ok = plugin.warps().delete(args[0]);
                sender.sendMessage(ok ? Msg.ok("<red>Warp supprimé.</red>") : Msg.err("Introuvable."));
            }
        }
        return true;
    }

    private void teleportWarp(Player p, WarpManager.Warp w) {
        if (w.server() != null && !w.server().equalsIgnoreCase(plugin.getServerType())) {
            plugin.pendingTp().set(p.getUniqueId(), new PendingTeleportManager.Pending(
                    PendingTeleportManager.Kind.LOC,
                    w.world(), w.x(), w.y(), w.z(), w.yaw(), w.pitch(),
                    System.currentTimeMillis()));
            p.sendMessage(Msg.info("<aqua>Transfert vers <white>" + w.server() + "</white>...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, w.server());
            return;
        }
        Location loc = w.toLocation();
        if (loc == null) {
            p.sendMessage(Msg.err("Monde du warp non chargé ici."));
            return;
        }
        p.teleportAsync(loc);
        p.sendMessage(Msg.ok("<aqua>Warp → " + w.name() + "</aqua>"));
    }
}
