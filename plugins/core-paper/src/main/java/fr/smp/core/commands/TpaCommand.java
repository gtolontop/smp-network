package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.managers.TpaManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final String action; // "to", "here", "accept", "deny", "cancel"

    public TpaCommand(SMPCore plugin, String action) {
        this.plugin = plugin;
        this.action = action;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        switch (action) {
            case "to", "here" -> handleSend(p, args);
            case "accept" -> handleAccept(p);
            case "deny" -> handleDeny(p);
            case "cancel" -> handleCancel(p);
        }
        return true;
    }

    private void handleSend(Player p, String[] args) {
        TpaManager.Type type = action.equals("here") ? TpaManager.Type.HERE : TpaManager.Type.TO;
        if (args.length == 0) {
            p.sendMessage(Msg.err("/" + (type == TpaManager.Type.HERE ? "tpahere" : "tpa") + " <joueur>"));
            return;
        }
        String targetName = args[0];
        if (targetName.equalsIgnoreCase(p.getName())) {
            p.sendMessage(Msg.err("Impossible: toi-même.")); return;
        }

        Player localTarget = Bukkit.getPlayerExact(targetName);
        if (localTarget != null) {
            plugin.tpa().send(p, localTarget, type);
            p.sendMessage(Msg.ok("<aqua>Demande envoyée à " + localTarget.getName() + ".</aqua>"));
            String verb = type == TpaManager.Type.HERE ? "veut que tu le rejoignes" : "veut se téléporter chez toi";
            localTarget.sendMessage(Msg.info("<aqua>" + p.getName() + "</aqua> " + verb +
                    ". <green>/tpaccept</green> <dark_gray>|</dark_gray> <red>/tpdeny</red>."));
            return;
        }

        NetworkRoster.Entry entry = plugin.roster() != null ? plugin.roster().get(targetName) : null;
        if (entry == null) {
            p.sendMessage(Msg.err("Joueur introuvable."));
            return;
        }

        final TpaManager.SenderLoc loc = type == TpaManager.Type.HERE
                ? new TpaManager.SenderLoc(p.getWorld().getName(),
                        p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                        p.getLocation().getYaw(), p.getLocation().getPitch())
                : TpaManager.SenderLoc.EMPTY;
        String senderServer = plugin.getServerType();
        String targetRealName = entry.name();
        plugin.getMessageChannel().sendForward(targetRealName, "tpa-request", out -> {
            out.writeUTF(p.getName());
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(senderServer);
            out.writeUTF(type.name());
            out.writeUTF(loc.world());
            out.writeDouble(loc.x());
            out.writeDouble(loc.y());
            out.writeDouble(loc.z());
            out.writeFloat(loc.yaw());
            out.writeFloat(loc.pitch());
        });
        // Track outgoing locally (for /tpacancel).
        plugin.tpa().receiveRemote(p.getName(), p.getUniqueId(), senderServer,
                targetRealName, type, loc);
        p.sendMessage(Msg.ok("<aqua>Demande envoyée à " + targetRealName +
                " <gray>(" + entry.server() + ")</gray>.</aqua>"));
    }

    private void handleAccept(Player p) {
        TpaManager.Request r = plugin.tpa().consume(p);
        if (r == null) { p.sendMessage(Msg.err("Aucune demande en attente.")); return; }
        if (plugin.combat().isTagged(p)) {
            p.sendMessage(Msg.err("Combat en cours.")); return;
        }

        String myServer = plugin.getServerType();
        boolean crossServer = r.fromServer() != null && !r.fromServer().equalsIgnoreCase(myServer);

        if (!crossServer) {
            Player from = Bukkit.getPlayer(r.from());
            if (from == null) { p.sendMessage(Msg.err("Demandeur hors-ligne.")); return; }
            if (plugin.combat().isTagged(from)) {
                p.sendMessage(Msg.err("Combat en cours.")); return;
            }
            Location dest = r.type() == TpaManager.Type.TO ? p.getLocation() : from.getLocation();
            if (!dest.getWorld().getWorldBorder().isInside(dest)) {
                p.sendMessage(Msg.err("Destination hors worldborder."));
                from.sendMessage(Msg.err("TPA annulé: destination hors worldborder."));
                return;
            }
            if (r.type() == TpaManager.Type.TO) from.teleportAsync(dest);
            else p.teleportAsync(dest);
            p.sendMessage(Msg.ok("<green>TPA accepté.</green>"));
            from.sendMessage(Msg.ok("<green>" + p.getName() + " a accepté.</green>"));
            return;
        }

        // Cross-server flow.
        if (r.type() == TpaManager.Type.TO) {
            // Sender teleports to target. Target is on this server; write pending with target's loc
            // then tell Velocity to transfer sender to this server.
            Location here = p.getLocation();
            if (!here.getWorld().getWorldBorder().isInside(here)) {
                p.sendMessage(Msg.err("Destination hors worldborder.")); return;
            }
            plugin.pendingTp().set(r.from(), new PendingTeleportManager.Pending(
                    PendingTeleportManager.Kind.LOC,
                    here.getWorld().getName(), here.getX(), here.getY(), here.getZ(),
                    here.getYaw(), here.getPitch(), System.currentTimeMillis()));
            plugin.getMessageChannel().sendTransferByName(r.fromName(), myServer);
            p.sendMessage(Msg.ok("<green>Transfert de " + r.fromName() + " en cours...</green>"));
        } else {
            // HERE: target (this player) teleports to sender. Sender's loc was captured at request.
            TpaManager.SenderLoc loc = r.senderLoc();
            if (loc == null || loc.isEmpty()) {
                p.sendMessage(Msg.err("Position du demandeur inconnue.")); return;
            }
            plugin.pendingTp().set(p.getUniqueId(), new PendingTeleportManager.Pending(
                    PendingTeleportManager.Kind.LOC,
                    loc.world(), loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch(),
                    System.currentTimeMillis()));
            p.sendMessage(Msg.info("<aqua>Transfert vers <white>" + r.fromServer() + "</white>...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, r.fromServer());
        }
    }

    private void handleDeny(Player p) {
        TpaManager.Request r = plugin.tpa().consume(p);
        if (r == null) { p.sendMessage(Msg.err("Aucune demande.")); return; }
        p.sendMessage(Msg.ok("<red>Demande refusée.</red>"));
        String myServer = plugin.getServerType();
        boolean crossServer = r.fromServer() != null && !r.fromServer().equalsIgnoreCase(myServer);
        if (crossServer) {
            plugin.getMessageChannel().sendForward(r.fromName(), "tpa-denied", out -> out.writeUTF(p.getName()));
        } else {
            Player from = Bukkit.getPlayer(r.from());
            if (from != null) from.sendMessage(Msg.err(p.getName() + " a refusé ta demande."));
        }
    }

    private void handleCancel(Player p) {
        plugin.tpa().cancelOutgoing(p.getUniqueId());
        p.sendMessage(Msg.ok("<gray>Demandes sortantes annulées.</gray>"));
    }
}
