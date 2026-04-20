package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class MsgCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final SMPCore plugin;
    private final String mode; // "msg" | "reply"

    public MsgCommand(SMPCore plugin, String mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }

        String targetName;
        UUID targetUuid;
        int msgStart;
        if (mode.equals("reply")) {
            UUID last = plugin.messages().last(p.getUniqueId());
            if (last == null) { p.sendMessage(Msg.err("Personne à qui répondre.")); return true; }
            targetUuid = last;
            String resolvedName = resolveName(last);
            if (resolvedName == null) { p.sendMessage(Msg.err("Joueur introuvable.")); return true; }
            targetName = resolvedName;
            if (args.length < 1) { p.sendMessage(Msg.err("/r <message>")); return true; }
            msgStart = 0;
        } else {
            if (args.length < 2) { p.sendMessage(Msg.err("/msg <joueur> <message>")); return true; }
            String arg = args[0];
            if (arg.equalsIgnoreCase(p.getName())) {
                p.sendMessage(Msg.err("Parler à soi-même n'aide pas.")); return true;
            }
            Player local = Bukkit.getPlayerExact(arg);
            if (local != null) {
                targetName = local.getName();
                targetUuid = local.getUniqueId();
            } else {
                NetworkRoster.Entry e = plugin.roster() != null ? plugin.roster().get(arg) : null;
                if (e == null) { p.sendMessage(Msg.err("Joueur hors-ligne.")); return true; }
                targetName = e.name();
                UUID resolved = plugin.players().resolveUuid(targetName);
                if (resolved == null) { p.sendMessage(Msg.err("UUID inconnu.")); return true; }
                targetUuid = resolved;
            }
            msgStart = 1;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = msgStart; i < args.length; i++) {
            if (i > msgStart) sb.append(' ');
            sb.append(args[i]);
        }
        String text = sb.toString();
        if (text.isBlank()) { p.sendMessage(Msg.err("Message vide.")); return true; }

        Component out = MM.deserialize("<dark_gray>[</dark_gray><aqua>" + p.getName() +
                "</aqua> <gray>→</gray> <aqua>" + targetName +
                "</aqua><dark_gray>]</dark_gray> <white>" + escape(text) + "</white>");
        p.sendMessage(out);

        Player localTarget = Bukkit.getPlayer(targetUuid);
        if (localTarget != null) {
            Component in = MM.deserialize("<dark_gray>[</dark_gray><aqua>" + p.getName() +
                    "</aqua> <gray>→ moi</gray><dark_gray>]</dark_gray> <white>" + escape(text) + "</white>");
            localTarget.sendMessage(in);
        } else {
            String safeText = text;
            UUID fromUuid = p.getUniqueId();
            plugin.getMessageChannel().sendForward(targetName, "msg", o -> {
                o.writeUTF(p.getName());
                o.writeUTF(fromUuid.toString());
                o.writeUTF(safeText);
            });
        }
        plugin.messages().remember(p.getUniqueId(), targetUuid);
        return true;
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        if (plugin.roster() != null) {
            for (NetworkRoster.Entry e : plugin.roster().all()) {
                UUID u = plugin.players().resolveUuid(e.name());
                if (uuid.equals(u)) return e.name();
            }
        }
        var off = Bukkit.getOfflinePlayer(uuid);
        return off.getName();
    }

    private String escape(String s) {
        // MiniMessage-reserved characters that would otherwise let players send styled text.
        return s.replace("<", "‹").replace(">", "›");
    }
}
