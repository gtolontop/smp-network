package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Admin instant teleport, network-wide.
 *   /tp <player>                     — teleport self to player
 *   /tp <x> <y> <z>                  — teleport self to coords (current world)
 *   /tp <player> <target>            — teleport another player to target
 *   /tp <player> <x> <y> <z>         — teleport another player to coords
 *
 * Cross-server flow: we ask the target's backend for current coords via the
 * smp:core forward pipeline, then PendingTp + transfer the traveler.
 */
public class TpCommand implements CommandExecutor {

    private final SMPCore plugin;

    public TpCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }

        if (args.length == 0) { p.sendMessage(Msg.err("/tp <joueur> | /tp <x> <y> <z> | /tp <joueur> <cible>")); return true; }

        // /tp <x> <y> <z>
        if (args.length == 3 && areCoordinates(args[0], args[1], args[2])) {
            return teleportSelfToCoords(p, args[0], args[1], args[2]);
        }

        // /tp <player> <x> <y> <z>
        if (args.length == 4 && areCoordinates(args[1], args[2], args[3])) {
            return teleportPlayerToCoords(sender, p, args[0], args[1], args[2], args[3]);
        }

        // /tp <player> <target>
        if (args.length == 2) {
            return teleportPlayerToPlayer(sender, p, args[0], args[1]);
        }

        // /tp <player>  — self to target
        if (args.length == 1) {
            return teleportSelfToPlayer(sender, p, args[0]);
        }

        p.sendMessage(Msg.err("/tp <joueur> | /tp <x> <y> <z> | /tp <joueur> <cible> | /tp <joueur> <x> <y> <z>"));
        return true;
    }

    private boolean teleportSelfToCoords(Player p, String sx, String sy, String sz) {
        try {
            Location origin = p.getLocation();
            Location destination = resolveCoordinates(origin, sx, sy, sz);
            destination.setYaw(origin.getYaw());
            destination.setPitch(origin.getPitch());
            p.teleportAsync(destination);
            p.sendMessage(Msg.ok("<aqua>Téléporté.</aqua>"));
        } catch (IllegalArgumentException e) {
            p.sendMessage(Msg.err(e.getMessage()));
        }
        return true;
    }

    private boolean teleportPlayerToCoords(CommandSender src, Player sender, String name, String sx, String sy, String sz) {
        Player target = findLocalPlayer(src, name);
        if (target == null) {
            sender.sendMessage(Msg.err("Le déplacement par coordonnées ne marche que si le joueur est sur ce serveur."));
            return true;
        }
        try {
            Location origin = target.getLocation();
            Location destination = resolveCoordinates(origin, sx, sy, sz);
            destination.setYaw(origin.getYaw());
            destination.setPitch(origin.getPitch());
            target.teleportAsync(destination);
            sender.sendMessage(Msg.ok("<aqua>" + target.getName() + " téléporté(e).</aqua>"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Msg.err(e.getMessage()));
        }
        return true;
    }

    private boolean teleportSelfToPlayer(CommandSender src, Player p, String targetName) {
        Player local = findLocalPlayer(src, targetName);
        if (local != null && local.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Msg.err("Tu es déjà toi-même.")); return true;
        }
        if (local != null) {
            p.teleportAsync(local.getLocation());
            p.sendMessage(Msg.ok("<aqua>Téléporté à " + local.getName() + ".</aqua>"));
            return true;
        }

        NetworkRoster.Entry entry = plugin.roster() != null ? plugin.roster().get(targetName) : null;
        if (entry == null) { p.sendMessage(Msg.err("Joueur introuvable: " + targetName)); return true; }

        String requesterName = p.getName();
        String requesterServer = plugin.getServerType();
        plugin.getMessageChannel().sendForward(entry.name(), "tp-lookup", out -> {
            out.writeUTF(requesterName);
            out.writeUTF(requesterServer);
        });
        p.sendMessage(Msg.info("<aqua>Recherche de " + entry.name() +
                " sur <white>" + entry.server() + "</white>...</aqua>"));
        return true;
    }

    private boolean teleportPlayerToPlayer(CommandSender src, Player sender, String moverName, String destName) {
        Player mover = findLocalPlayer(src, moverName);
        Player destLocal = findLocalPlayer(src, destName);

        // Both on this server: easy case.
        if (mover != null && destLocal != null) {
            mover.teleportAsync(destLocal.getLocation());
            sender.sendMessage(Msg.ok("<aqua>" + mover.getName() + " → " + destLocal.getName() + ".</aqua>"));
            return true;
        }

        // Mover is the sender themselves: reuse the self-to-player path.
        if (mover == null && moverName.equalsIgnoreCase(sender.getName())) {
            return teleportSelfToPlayer(src, sender, destName);
        }

        // Mover local, dest remote: lookup dest coords, then teleport mover (possibly via transfer).
        if (mover != null) {
            NetworkRoster.Entry destEntry = plugin.roster() != null ? plugin.roster().get(destName) : null;
            if (destEntry == null) {
                sender.sendMessage(Msg.err("Cible introuvable: " + destName));
                return true;
            }
            String moverExact = mover.getName();
            plugin.getMessageChannel().sendForward(destEntry.name(), "tp-lookup", out -> {
                out.writeUTF(moverExact);
                out.writeUTF(plugin.getServerType());
            });
            sender.sendMessage(Msg.info("<aqua>Déplacement de " + mover.getName() +
                    " vers " + destEntry.name() + "...</aqua>"));
            return true;
        }

        // Mover remote. We forward "tp-move-here" to mover's backend, which has
        // the UUID locally — it writes the pending-tp and triggers a transfer.
        NetworkRoster.Entry moverEntry = plugin.roster() != null ? plugin.roster().get(moverName) : null;
        if (moverEntry == null) {
            sender.sendMessage(Msg.err("Joueur introuvable: " + moverName));
            return true;
        }
        String moverExact = moverEntry.name();
        String adminName = sender.getName();
        String senderServer = plugin.getServerType();

        // Dest is the admin themselves.
        if (destName.equalsIgnoreCase(sender.getName())) {
            Location loc = sender.getLocation();
            plugin.getMessageChannel().sendForward(moverExact, "tp-move-here",
                    out -> writeMoveHere(out, senderServer, loc, adminName));
            sender.sendMessage(Msg.info("<aqua>Déplacement de " + moverExact + "...</aqua>"));
            return true;
        }

        // Dest is local on admin's server.
        if (destLocal != null) {
            Location loc = destLocal.getLocation();
            String destNameFinal = destLocal.getName();
            plugin.getMessageChannel().sendForward(moverExact, "tp-move-here",
                    out -> writeMoveHere(out, senderServer, loc, adminName));
            sender.sendMessage(Msg.info("<aqua>Déplacement de " + moverExact + " vers " + destNameFinal + "...</aqua>"));
            return true;
        }

        // Both mover and dest are remote. Ask dest's backend for its location;
        // it relays a "tp-move-here" directly to mover.
        NetworkRoster.Entry destEntry = plugin.roster().get(destName);
        if (destEntry == null) {
            sender.sendMessage(Msg.err("Cible introuvable: " + destName));
            return true;
        }
        String destExact = destEntry.name();
        plugin.getMessageChannel().sendForward(destExact, "tp-dest-lookup", out -> {
            out.writeUTF(moverExact);
            out.writeUTF(adminName);
        });
        sender.sendMessage(Msg.info("<aqua>Déplacement de " + moverExact +
                " → " + destExact + " <gray>(" + destEntry.server() + ")</gray>...</aqua>"));
        return true;
    }

    private static void writeMoveHere(java.io.DataOutputStream out, String destServer,
                                      Location loc, String adminName) throws java.io.IOException {
        out.writeUTF(destServer);
        out.writeUTF(loc.getWorld().getName());
        out.writeDouble(loc.getX());
        out.writeDouble(loc.getY());
        out.writeDouble(loc.getZ());
        out.writeFloat(loc.getYaw());
        out.writeFloat(loc.getPitch());
        out.writeUTF(adminName);
    }

    private static Player findLocalPlayer(CommandSender src, String name) {
        if (name == null || name.isEmpty()) return null;
        // Minecraft target selector (@p, @s, @r, @a[...]) — resolve against sender.
        if (name.charAt(0) == '@' && src != null) {
            try {
                for (org.bukkit.entity.Entity e : Bukkit.selectEntities(src, name)) {
                    if (e instanceof Player pl) return pl;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to name lookup.
            }
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;
        // Case-insensitive fallback: scan online players.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static boolean isNumber(String s) {
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static boolean areCoordinates(String sx, String sy, String sz) {
        return isCoordinateToken(sx) && isCoordinateToken(sy) && isCoordinateToken(sz);
    }

    private static boolean isCoordinateToken(String s) {
        if (s == null || s.isEmpty()) return false;
        char prefix = s.charAt(0);
        if (prefix == '~' || prefix == '^') {
            return s.length() == 1 || isNumber(s.substring(1));
        }
        return isNumber(s);
    }

    private static Location resolveCoordinates(Location base, String sx, String sy, String sz) {
        boolean anyLocal = sx.startsWith("^") || sy.startsWith("^") || sz.startsWith("^");
        boolean allLocal = sx.startsWith("^") && sy.startsWith("^") && sz.startsWith("^");
        if (anyLocal && !allLocal) {
            throw new IllegalArgumentException("Les coordonnées locales (^) ne peuvent pas être mélangées.");
        }
        return allLocal ? resolveLocalCoordinates(base, sx, sy, sz) : resolveWorldCoordinates(base, sx, sy, sz);
    }

    private static Location resolveWorldCoordinates(Location base, String sx, String sy, String sz) {
        return new Location(
                base.getWorld(),
                resolveWorldCoordinate(base.getX(), sx),
                resolveWorldCoordinate(base.getY(), sy),
                resolveWorldCoordinate(base.getZ(), sz),
                base.getYaw(),
                base.getPitch()
        );
    }

    private static double resolveWorldCoordinate(double baseValue, String token) {
        if (token.startsWith("~")) {
            return baseValue + parseCoordinateOffset(token.substring(1));
        }
        if (token.startsWith("^")) {
            throw new IllegalArgumentException("Coordonnées invalides.");
        }
        return parseCoordinateNumber(token);
    }

    private static Location resolveLocalCoordinates(Location base, String sx, String sy, String sz) {
        double left = parseCoordinateOffset(sx.substring(1));
        double up = parseCoordinateOffset(sy.substring(1));
        double forwards = parseCoordinateOffset(sz.substring(1));

        double yaw = Math.toRadians(base.getYaw() + 90.0);
        double pitch = Math.toRadians(-base.getPitch());
        double pitchPlusNinety = Math.toRadians(-base.getPitch() + 90.0);

        Vector forward = new Vector(
                Math.cos(yaw) * Math.cos(pitch),
                Math.sin(pitch),
                Math.sin(yaw) * Math.cos(pitch)
        );
        Vector upVector = new Vector(
                Math.cos(yaw) * Math.cos(pitchPlusNinety),
                Math.sin(pitchPlusNinety),
                Math.sin(yaw) * Math.cos(pitchPlusNinety)
        );
        Vector leftVector = forward.clone().crossProduct(upVector).multiply(-1.0);

        Vector offset = forward.multiply(forwards)
                .add(upVector.multiply(up))
                .add(leftVector.multiply(left));

        return base.clone().add(offset);
    }

    private static double parseCoordinateOffset(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0.0;
        }
        return parseCoordinateNumber(raw);
    }

    private static double parseCoordinateNumber(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Coordonnées invalides.");
        }
    }

    /** Static helper: consumed by MessageChannel when handling tp-execute (reply from target's server). */
    public static void applyTpExecute(SMPCore plugin, String requesterName,
                                      String world, double x, double y, double z, float yaw, float pitch,
                                      String targetServer) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayerExact(requesterName);
            if (p == null) return;
            if (targetServer.equalsIgnoreCase(plugin.getServerType())) {
                org.bukkit.World w = Bukkit.getWorld(world);
                if (w != null) {
                    p.teleportAsync(new Location(w, x, y, z, yaw, pitch));
                    p.sendMessage(Msg.ok("<aqua>Téléporté.</aqua>"));
                } else {
                    p.sendMessage(Msg.err("Monde <white>" + world + "</white> introuvable ici."));
                }
                return;
            }
            plugin.pendingTp().set(p.getUniqueId(), new PendingTeleportManager.Pending(
                    PendingTeleportManager.Kind.LOC, world, x, y, z, yaw, pitch,
                    System.currentTimeMillis(), targetServer));
            p.sendMessage(Msg.info("<aqua>Transfert vers <white>" + targetServer + "</white>...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, targetServer);
        });
    }
}
