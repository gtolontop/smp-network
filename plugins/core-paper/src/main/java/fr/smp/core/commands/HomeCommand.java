package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.HomesGUI;
import fr.smp.core.managers.HomeManager;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomeCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final String mode; // "home", "homes", "sethome", "delhome"

    public HomeCommand(SMPCore plugin, String mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (plugin.combat() != null && plugin.combat().isTagged(p) && !mode.equals("homes") && !mode.equals("sethome")) {
            p.sendMessage(Msg.err("Tu es en combat. Attends <white>" + plugin.combat().remainingSec(p) + "s</white>."));
            return true;
        }

        int max = plugin.homes().maxSlots(p);

        switch (mode) {
            case "homes", "home" -> {
                if (args.length == 0) { new HomesGUI(plugin, p).open(); return true; }
                int slot;
                try { slot = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
                    p.sendMessage(Msg.err("/home <slot>")); return true;
                }
                HomeManager.Home home = plugin.homes().get(p.getUniqueId(), slot);
                if (home == null) { p.sendMessage(Msg.err("Pas de home au slot " + slot + ".")); return true; }
                teleportHome(p, home);
            }
            case "sethome" -> {
                int slot;
                if (args.length == 0) {
                    slot = plugin.homes().firstFreeSlot(p.getUniqueId(), max);
                    if (slot == -1) { p.sendMessage(Msg.err("Tous tes slots sont pris (max " + max + ").")); return true; }
                } else {
                    try { slot = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
                        p.sendMessage(Msg.err("/sethome [slot]")); return true;
                    }
                }
                if (slot < 1 || slot > max) {
                    p.sendMessage(Msg.err("Slot 1-" + max + " seulement.")); return true;
                }
                plugin.homes().set(p.getUniqueId(), slot, p.getLocation());
                p.sendMessage(Msg.ok("<green>Home <yellow>" + slot + "</yellow> défini.</green>"));
            }
            case "delhome" -> {
                if (args.length == 0) { new HomesGUI(plugin, p).open(); return true; }
                int slot;
                try { slot = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
                    p.sendMessage(Msg.err("/delhome <slot>")); return true;
                }
                plugin.homes().delete(p.getUniqueId(), slot);
                p.sendMessage(Msg.ok("<red>Home " + slot + " supprimé.</red>"));
            }
        }
        return true;
    }

    private void teleportHome(Player p, HomeManager.Home home) {
        if (plugin.cooldowns() != null && plugin.cooldowns().isOnCooldown(p, "home")) {
            p.sendMessage(Msg.err("Cooldown: <white>" + Msg.duration(plugin.cooldowns().remaining(p, "home")) + "</white>"));
            return;
        }
        String homeServer = home.server();
        if (homeServer != null && !homeServer.equalsIgnoreCase(plugin.getServerType())) {
            // Cross-server teleport: persist pending + transfer
            plugin.pendingTp().set(p.getUniqueId(), new PendingTeleportManager.Pending(
                    PendingTeleportManager.Kind.LOC,
                    home.world(), home.x(), home.y(), home.z(),
                    home.yaw(), home.pitch(),
                    System.currentTimeMillis(), homeServer));
            p.sendMessage(Msg.info("<aqua>Transfert vers <white>" + homeServer + "</white>...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, homeServer);
            return;
        }
        Location loc = home.toLocation();
        if (loc == null) {
            p.sendMessage(Msg.err("Le monde <white>" + home.world() + "</white> n'est pas chargé ici."));
            return;
        }
        p.teleportAsync(loc);
        if (plugin.cooldowns() != null) plugin.cooldowns().set(p, "home");
        p.sendMessage(Msg.ok("<aqua>Téléporté à home " + home.slot() + ".</aqua>"));
    }
}
