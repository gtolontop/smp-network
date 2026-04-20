package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.gui.WaypointsGUI;
import fr.smp.core.managers.WaypointManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WaypointsCommand implements CommandExecutor {

    private final SMPCore plugin;

    public WaypointsCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (args.length == 0) {
            new WaypointsGUI(plugin).open(p);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "player" -> {
                if (args.length < 2) { p.sendMessage(Msg.err("/waypoints player <nom>")); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { p.sendMessage(Msg.err("Joueur hors-ligne.")); return true; }
                Location loc = t.getLocation();
                p.sendMessage(Msg.ok("<aqua>Position de " + t.getName() + ":</aqua>"));
                p.sendMessage(Msg.mm("<gray>Monde: <white>" + loc.getWorld().getName() + "</white></gray>"));
                p.sendMessage(Msg.mm("<gray>Coords: <white>"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</white></gray>"));
            }
            case "coords" -> {
                if (args.length < 4) { p.sendMessage(Msg.err("/waypoints coords <x> <y> <z>")); return true; }
                double x, y, z;
                try {
                    x = Double.parseDouble(args[1]);
                    y = Double.parseDouble(args[2]);
                    z = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) { p.sendMessage(Msg.err("Coords invalides.")); return true; }
                World w = args.length > 4 ? Bukkit.getWorld(args[4]) : p.getWorld();
                if (w == null) { p.sendMessage(Msg.err("Monde inconnu.")); return true; }
                p.sendMessage(Msg.ok("<aqua>Coords:</aqua>"));
                p.sendMessage(Msg.mm("<gray>Monde: <white>" + w.getName() + "</white></gray>"));
                p.sendMessage(Msg.mm("<gray>Coords: <white>"
                        + (int) x + ", " + (int) y + ", " + (int) z + "</white></gray>"));
            }
            case "solo" -> new WaypointsGUI(plugin).open(p, WaypointManager.Kind.SOLO);
            case "team" -> new WaypointsGUI(plugin).open(p, WaypointManager.Kind.TEAM);
            case "add", "set" -> {
                if (args.length < 3) { p.sendMessage(Msg.err("/waypoints add solo|team <nom>")); return true; }
                WaypointManager.Kind kind = args[1].equalsIgnoreCase("team")
                        ? WaypointManager.Kind.TEAM : WaypointManager.Kind.SOLO;
                String name = args[2];
                String ownerId;
                if (kind == WaypointManager.Kind.TEAM) {
                    PlayerData d = plugin.players().get(p);
                    if (d == null || d.teamId() == null) { p.sendMessage(Msg.err("Pas dans une team.")); return true; }
                    var t = plugin.teams().get(d.teamId());
                    if (t == null || !t.owner().equals(p.getUniqueId().toString())) {
                        p.sendMessage(Msg.err("Owner uniquement.")); return true;
                    }
                    ownerId = d.teamId();
                } else {
                    ownerId = p.getUniqueId().toString();
                }
                plugin.waypoints().set(kind, ownerId, name, p.getLocation(), null);
                p.sendMessage(Msg.ok("<green>Waypoint <aqua>" + name + "</aqua> enregistré.</green>"));
            }
            default -> p.sendMessage(Msg.mm("<gray>/waypoints | player <joueur> | coords <x y z [world]> | solo | team | add solo|team <nom></gray>"));
        }
        return true;
    }
}
