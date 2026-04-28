package fr.smp.core.dragonegg;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DragonEggCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;
    private final DragonEggManager manager;

    public DragonEggCommand(SMPCore plugin, DragonEggManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> handleGive(sender, args);
            case "tracker", "compass", "track" -> handleTracker(sender, args);
            case "respawn" -> handleRespawn(sender);
            case "setspawn" -> handleSetSpawn(sender);
            case "status", "info" -> handleStatus(sender);
            case "destroy" -> handleDestroy(sender);
            case "tp" -> handleTp(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Msg.info("<gray>Usage : /dragonegg <give|tracker|respawn|setspawn|status|destroy|tp></gray>"));
    }

    private void handleGive(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Msg.err("Usage : /dragonegg give <joueur>"));
            return;
        }
        if (target == null) {
            sender.sendMessage(Msg.err("Joueur introuvable."));
            return;
        }
        int purged = manager.giveNewEgg(target, "give-" + (sender instanceof Player p ? p.getName() : "console"));
        if (purged > 0) {
            sender.sendMessage(Msg.ok("<gray>(<yellow>" + purged + "</yellow> œuf(s) parasite(s) purgé(s))</gray>"));
        }
        sender.sendMessage(Msg.ok("Œuf du Dragon donné à <yellow>" + target.getName() + "</yellow>."));
    }

    private void handleTracker(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Msg.err("Usage : /dragonegg tracker <joueur>"));
            return;
        }
        if (target == null) {
            sender.sendMessage(Msg.err("Joueur introuvable."));
            return;
        }
        manager.giveTracker(target);
        sender.sendMessage(Msg.ok("Traqueur de l'Œuf donné à <yellow>" + target.getName() + "</yellow>."));
    }

    private void handleRespawn(CommandSender sender) {
        manager.respawnNow();
        sender.sendMessage(Msg.ok("Respawn de l'œuf forcé."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.err("Joueur uniquement."));
            return;
        }
        if (manager.setRespawnLocation(p.getLocation())) {
            Location loc = p.getLocation();
            sender.sendMessage(Msg.ok("Autel défini en <white>" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                    + "</white> <gray>(" + loc.getWorld().getName() + ")</gray>."));
        } else {
            sender.sendMessage(Msg.err("Impossible de définir l'autel ici."));
        }
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Msg.info("<gradient:#a78bfa:#67e8f9><bold>Œuf du Dragon — état</bold></gradient>"));
        sender.sendMessage(Msg.info("<gray>État : <yellow>" + manager.currentState() + "</yellow></gray>"));
        Location respawn = manager.respawnLocation();
        if (respawn != null) {
            sender.sendMessage(Msg.info("<gray>Autel : <white>" + respawn.getBlockX() + " " + respawn.getBlockY() + " " + respawn.getBlockZ()
                    + "</white> <dark_gray>(" + respawn.getWorld().getName() + ")</dark_gray></gray>"));
        }
        Location last = manager.lastKnownLocation();
        if (last != null && last.getWorld() != null) {
            sender.sendMessage(Msg.info("<gray>Dernier emplacement : <white>" + last.getBlockX() + " " + last.getBlockY() + " " + last.getBlockZ()
                    + "</white> <dark_gray>(" + last.getWorld().getName() + ")</dark_gray></gray>"));
        }
        UUID holder = manager.currentHolder();
        if (holder != null) {
            Player p = Bukkit.getPlayer(holder);
            sender.sendMessage(Msg.info("<gray>Tenu par : <yellow>" + (p != null ? p.getName() : holder.toString()) + "</yellow></gray>"));
        } else if (manager.placedAt() != null) {
            sender.sendMessage(Msg.info("<gray>Posé au sol.</gray>"));
        }
        long reclaim = manager.secondsUntilReclaim();
        if (manager.placedAt() != null && reclaim > 0L) {
            sender.sendMessage(Msg.info("<gray>Récupérable dans : <yellow>" + Msg.duration(reclaim) + "</yellow></gray>"));
        }
        long secs = manager.secondsUntilRespawn();
        if (secs >= 0) {
            sender.sendMessage(Msg.info("<gray>Respawn dans : <yellow>" + secs + "s</yellow></gray>"));
        }
    }

    private void handleDestroy(CommandSender sender) {
        Location at = manager.lastKnownLocation();
        if (at == null && sender instanceof Player p) at = p.getLocation();
        manager.destroy("admin", at);
        sender.sendMessage(Msg.ok("Œuf marqué comme détruit."));
    }

    private void handleTp(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.err("Joueur uniquement."));
            return;
        }
        Location at = manager.lastKnownLocation();
        if (at == null || at.getWorld() == null) {
            sender.sendMessage(Msg.err("Aucun emplacement connu."));
            return;
        }
        p.teleport(at);
        sender.sendMessage(Msg.ok("Téléporté au dernier emplacement de l'œuf."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("give", "tracker", "respawn", "setspawn", "status", "destroy", "tp")) {
                if (s.startsWith(prefix)) out.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("tracker") || args[0].equalsIgnoreCase("compass") || args[0].equalsIgnoreCase("track"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }
}
