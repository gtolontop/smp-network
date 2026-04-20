package fr.smp.core.permissions;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Overrides vanilla /op + /deop so they route through the shared permissions system. */
public class OpCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;
    private final boolean grant;

    public OpCommand(SMPCore plugin, boolean grant) {
        this.plugin = plugin;
        this.grant = grant;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("smp.op")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        PermissionsManager pm = plugin.permissions();
        if (pm == null) {
            sender.sendMessage(Msg.err("Permissions offline."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Msg.err("Usage: /" + (grant ? "op" : "deop") + " <joueur>"));
            return true;
        }
        String target = args[0];
        UUID uuid = plugin.players().resolveUuid(target);
        if (uuid == null) {
            sender.sendMessage(Msg.err("Joueur inconnu: " + target));
            return true;
        }
        String group = grant ? PermissionsManager.ADMIN_GROUP : PermissionsManager.DEFAULT_GROUP;
        boolean ok = pm.setUserGroup(uuid, target, group);
        sender.sendMessage(ok
                ? Msg.ok(target + (grant ? " est maintenant admin (réseau)." : " n'est plus admin."))
                : Msg.err("Échec."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : plugin.getServer().getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}
