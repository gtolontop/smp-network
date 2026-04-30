package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.gui.HomesGUI;
import fr.smp.core.managers.HomeManager;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

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
        if (isHomesMode() && args.length == 1 && args[0].endsWith(":")) {
            return openAdminHomes(p, args[0]);
        }
        if (plugin.combat() != null && plugin.combat().isTagged(p) && !mode.equals("homes") && !mode.equals("sethome")) {
            p.sendMessage(Msg.err("Tu es en combat. Attends <white>" + plugin.combat().remainingSec(p) + "s</white>."));
            return true;
        }
        if (plugin.dragonEgg() != null && plugin.dragonEgg().inventoryContainsEgg(p)
                && !mode.equals("homes") && !mode.equals("sethome") && !mode.equals("delhome")) {
            p.sendMessage(Msg.err("Tu portes l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient> — pose-le avant de te téléporter."));
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
                Location setLoc = p.getLocation();
                plugin.homes().set(p.getUniqueId(), slot, setLoc);
                plugin.getLogger().info("[HOME] " + p.getName() + " défini home#" + slot
                        + " à " + setLoc.getBlockX() + "," + setLoc.getBlockY() + "," + setLoc.getBlockZ()
                        + " (" + setLoc.getWorld().getName() + ")");
                p.sendMessage(Msg.ok("<green>Home <yellow>" + slot + "</yellow> défini.</green>"));
            }
            case "delhome" -> {
                if (args.length == 0) { new HomesGUI(plugin, p).open(); return true; }
                int slot;
                try { slot = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
                    p.sendMessage(Msg.err("/delhome <slot>")); return true;
                }
                plugin.homes().delete(p.getUniqueId(), slot);
                plugin.getLogger().info("[HOME] " + p.getName() + " supprimé home#" + slot);
                p.sendMessage(Msg.ok("<red>Home " + slot + " supprimé.</red>"));
            }
        }
        return true;
    }

    private boolean isHomesMode() {
        return mode.equals("home") || mode.equals("homes");
    }

    private boolean openAdminHomes(Player admin, String rawTarget) {
        if (!admin.hasPermission("smp.admin")) {
            admin.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        String targetName = rawTarget.substring(0, rawTarget.length() - 1).trim();
        if (targetName.isEmpty()) {
            admin.sendMessage(Msg.err("/home <joueur>:"));
            return true;
        }
        UUID targetUuid = plugin.players().resolveUuid(targetName);
        if (targetUuid == null) {
            admin.sendMessage(Msg.err("Joueur introuvable."));
            return true;
        }
        PlayerData targetData = plugin.players().loadOffline(targetUuid);
        String displayName = targetData != null && targetData.name() != null ? targetData.name() : targetName;
        new HomesGUI(plugin, admin, targetUuid, displayName).open();
        plugin.getLogger().info("[HOME] " + admin.getName() + " opened admin homes for " + displayName);
        return true;
    }

    private void teleportHome(Player p, HomeManager.Home home) {
        if (plugin.cooldowns() != null && plugin.cooldowns().isOnCooldown(p, "home")) {
            p.sendMessage(Msg.err("Cooldown: <white>" + Msg.duration(plugin.cooldowns().remaining(p, "home")) + "</white>"));
            return;
        }
        String homeServer = home.server();
        if (homeServer != null && !homeServer.equalsIgnoreCase(plugin.getServerType())) {
            plugin.getLogger().info("[HOME] " + p.getName() + " -> home#" + home.slot()
                    + " cross-server " + homeServer
                    + " (" + (int)home.x() + "," + (int)home.y() + "," + (int)home.z() + "@" + home.world() + ")");
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
            plugin.getLogger().warning("[HOME] " + p.getName() + " -> home#" + home.slot()
                    + " monde introuvable: " + home.world());
            p.sendMessage(Msg.err("Le monde <white>" + home.world() + "</white> n'est pas chargé ici."));
            return;
        }
        p.teleportAsync(loc);
        if (plugin.cooldowns() != null) plugin.cooldowns().set(p, "home");
        plugin.getLogger().info("[HOME] " + p.getName() + " -> home#" + home.slot()
                + " à " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + " (" + loc.getWorld().getName() + ")");
        p.sendMessage(Msg.ok("<aqua>Téléporté à home " + home.slot() + ".</aqua>"));
    }
}
