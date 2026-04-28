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
        if (plugin.dragonEgg() != null && plugin.dragonEgg().inventoryContainsEgg(p)) {
            p.sendMessage(Msg.err("Tu portes l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient> — pose-le avant de te téléporter."));
            return true;
        }
        if (args.length == 0) {
            plugin.getLogger().info("[RTP] " + p.getName() + " ouvert GUI RTP");
            new RtpGUI(plugin).open(p);
            return true;
        }
        if (plugin.rtp() != null) {
            long cd = plugin.rtp().cooldownLeft(p);
            if (cd > 0) {
                plugin.getLogger().info("[RTP] " + p.getName() + " bloqué cooldown (" + cd + "s)");
                p.sendMessage(Msg.err("Cooldown: <white>" + cd + "s</white>"));
                return true;
            }
        }
        String target = args[0].toLowerCase();
        if (target.equals("end") && !plugin.endToggle().enabled() && !p.hasPermission("smp.admin")) {
            plugin.getLogger().info("[RTP] " + p.getName() + " bloqué: End désactivé");
            p.sendMessage(Msg.err("<red>L'End est désactivé.</red>"));
            return true;
        }
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

        // Cross-server: on non-survival backends, delegate to survival.
        if (!plugin.isMainSurvival()) {
            plugin.getLogger().info("[RTP] " + p.getName() + " -> survival (cross-server RTP " + target + ")");
            plugin.pendingTp().set(p.getUniqueId(), new PendingTeleportManager.Pending(
                    PendingTeleportManager.Kind.RTP,
                    worldName, 0, 0, 0, 0, 0,
                    System.currentTimeMillis(), "survival"));
            p.sendMessage(Msg.info("<aqua>Transfert vers survie pour RTP...</aqua>"));
            plugin.getMessageChannel().sendTransfer(p, "survival");
            return true;
        }

        World w = plugin.resolveWorld(worldName, env);
        if (w == null) {
            plugin.getLogger().warning("[RTP] " + p.getName() + " -> monde introuvable: " + worldName);
            p.sendMessage(Msg.err("Monde introuvable."));
            return true;
        }
        plugin.getLogger().info("[RTP] " + p.getName() + " -> " + worldName + " (recherche en cours...)");
        p.sendMessage(Msg.info("<aqua>Recherche d'un lieu sûr...</aqua>"));
        plugin.rtp().teleport(p, w);
        return true;
    }
}
