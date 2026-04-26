package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.InvseeGUI;
import fr.smp.core.gui.OfflineInvseeGUI;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.UUID;

public class InvseeCommand implements CommandExecutor {

    private final SMPCore plugin;

    public InvseeCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (args.length == 0) { p.sendMessage(Msg.err("/invsee <joueur>")); return true; }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null) {
            if (target.getUniqueId().equals(p.getUniqueId())) {
                p.sendMessage(Msg.err("Utilise E pour voir ton inventaire."));
                return true;
            }
            new InvseeGUI(target).open(p);
            return true;
        }

        UUID uuid = resolveUuid(args[0]);
        if (uuid == null) {
            p.sendMessage(Msg.err("Joueur introuvable."));
            return true;
        }

        YamlConfiguration yaml = loadSyncYaml(uuid);
        if (yaml == null || !yaml.contains("inventory.contents")) {
            p.sendMessage(Msg.err("Aucune donnée d'inventaire trouvée pour ce joueur."));
            return true;
        }

        String name = yaml.getString("name", args[0]);
        new OfflineInvseeGUI(name).open(p, yaml);
        return true;
    }

    private UUID resolveUuid(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p.getUniqueId();
        }
        if (plugin.invHistory() != null) {
            UUID uuid = plugin.invHistory().resolveUuid(name);
            if (uuid != null) return uuid;
        }
        return plugin.players() != null ? resolveFromPlayers(name) : null;
    }

    private UUID resolveFromPlayers(String name) {
        try (var c = plugin.database().get();
             var ps = c.prepareStatement("SELECT uuid FROM players WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString(1));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private YamlConfiguration loadSyncYaml(UUID uuid) {
        if (!plugin.getSyncManager().isEnabled()) return null;
        File file = new File(plugin.getSyncManager().getSyncDataDir(), uuid.toString() + ".yml");
        if (!file.exists()) return null;
        return YamlConfiguration.loadConfiguration(file);
    }
}
