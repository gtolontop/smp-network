package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.RtpGUI;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RtpCommand implements CommandExecutor {

    private final SMPCore plugin;

    public RtpCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (plugin.combat() != null && plugin.combat().isTagged(p)) {
            p.sendMessage(Msg.err("Tu es en combat. Attends <white>" + plugin.combat().remainingSec(p) + "s</white>."));
            return true;
        }
        if (args.length == 0) {
            new RtpGUI(plugin).open(p);
            return true;
        }
        if (plugin.rtp() != null) {
            long cd = plugin.rtp().cooldownLeft(p);
            if (cd > 0) {
                p.sendMessage(Msg.err("Cooldown: <white>" + Msg.duration(cd) + "</white>"));
                return true;
            }
        }
        String target = args[0].toLowerCase();
        String worldName = switch (target) {
            case "nether" -> plugin.getConfig().getString("rtp.world-nether", "world_nether");
            case "end" -> plugin.getConfig().getString("rtp.world-end", "world_the_end");
            default -> plugin.getConfig().getString("rtp.world-overworld", "world");
        };
        World.Environment env = switch (target) {
            case "nether" -> World.Environment.NETHER;
            case "end" -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };

        // Cross-server: on lobby, delegate to survival.
        if (plugin.isLobby()) {
            plugin.pendingTp().set(p.getUniqueId(), new PendingTeleportManager.Pending(
                    PendingTeleportManager.Kind.RTP,
                    worldName, 0, 0, 0, 0, 0,
                    System.currentTimeMillis()));
            p.sendMessage(Msg.info("<aqua>Transfert vers survie pour RTP...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, "survival");
            return true;
        }

        World w = plugin.resolveWorld(worldName, env);
        if (w == null) {
            p.sendMessage(Msg.err("Monde introuvable."));
            return true;
        }
        p.sendMessage(Msg.info("<aqua>Recherche d'un lieu sûr...</aqua>"));
        plugin.rtp().teleport(p, w);
        return true;
    }
}
