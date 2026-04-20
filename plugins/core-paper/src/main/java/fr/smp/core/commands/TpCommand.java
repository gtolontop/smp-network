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
        if (args.length == 3 && isNumber(args[0]) && isNumber(args[1]) && isNumber(args[2])) {
            return teleportSelfToCoords(p, args[0], args[1], args[2]);
        }

        // /tp <player> <x> <y> <z>
        if (args.length == 4 && isNumber(args[1]) && isNumber(args[2]) && isNumber(args[3])) {
            return teleportPlayerToCoords(p, args[0], args[1], args[2], args[3]);
        }

        // /tp <player> <target>
        if (args.length == 2) {
            return teleportPlayerToPlayer(p, args[0], args[1]);
        }

        // /tp <player>  — self to target
        if (args.length == 1) {
            return teleportSelfToPlayer(p, args[0]);
        }

        p.sendMessage(Msg.err("/tp <joueur> | /tp <x> <y> <z> | /tp <joueur> <cible> | /tp <joueur> <x> <y> <z>"));
        return true;
    }

    private boolean teleportSelfToCoords(Player p, String sx, String sy, String sz) {
        try {
            double x = Double.parseDouble(sx);
            double y = Double.parseDouble(sy);
            double z = Double.parseDouble(sz);
            p.teleportAsync(new Location(p.getWorld(), x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch()));
            p.sendMessage(Msg.ok("<aqua>Téléporté.</aqua>"));
        } catch (NumberFormatException e) {
            p.sendMessage(Msg.err("Coordonnées invalides."));
        }
        return true;
    }

    private boolean teleportPlayerToCoords(Player sender, String name, String sx, String sy, String sz) {
        Player target = findLocalPlayer(name);
        if (target == null) {
            sender.sendMessage(Msg.err("Le déplacement par coordonnées ne marche que si le joueur est sur ce serveur."));
            return true;
        }
        try {
            double x = Double.parseDouble(sx);
            double y = Double.parseDouble(sy);
            double z = Double.parseDouble(sz);
            target.teleportAsync(new Location(target.getWorld(), x, y, z,
                    target.getLocation().getYaw(), target.getLocation().getPitch()));
            sender.sendMessage(Msg.ok("<aqua>" + target.getName() + " téléporté(e).</aqua>"));
        } catch (NumberFormatException e) {
            sender.sendMessage(Msg.err("Coordonnées invalides."));
        }
        return true;
    }

    private boolean teleportSelfToPlayer(Player p, String targetName) {
        if (targetName.equalsIgnoreCase(p.getName())) {
            p.sendMessage(Msg.err("Tu es déjà toi-même.")); return true;
        }

        Player local = findLocalPlayer(targetName);
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

    private boolean teleportPlayerToPlayer(Player sender, String moverName, String destName) {
        Player mover = findLocalPlayer(moverName);
        Player destLocal = findLocalPlayer(destName);

        // Both on this server: easy case.
        if (mover != null && destLocal != null) {
            mover.teleportAsync(destLocal.getLocation());
            sender.sendMessage(Msg.ok("<aqua>" + mover.getName() + " → " + destLocal.getName() + ".</aqua>"));
            return true;
        }

        // Mover is the sender themselves: reuse the self-to-player path.
        if (mover == null && moverName.equalsIgnoreCase(sender.getName())) {
            return teleportSelfToPlayer(sender, destName);
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

    private static Player findLocalPlayer(String name) {
        if (name == null || name.isEmpty()) return null;
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
                    PendingTeleportManager.Kind.LOC, world, x, y, z, yaw, pitch, System.currentTimeMillis()));
            p.sendMessage(Msg.info("<aqua>Transfert vers <white>" + targetServer + "</white>...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, targetServer);
        });
    }
}
