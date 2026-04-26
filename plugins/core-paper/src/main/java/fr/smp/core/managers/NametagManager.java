package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a rank prefix + [TAG] above each player's head using per-player
 * scoreboard teams on the main scoreboard.
 *
 * Each player gets their own scoreboard team so the prefix (grade + team tag)
 * is unique per player. The prefix is only updated when it changes to avoid
 * redundant packet sends.
 */
public class NametagManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final Map<UUID, String> currentTeamByPlayer = new HashMap<>();
    private final Map<UUID, String> currentPrefixByPlayer = new HashMap<>();

    public NametagManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                refreshAll();
            }
        }.runTaskTimer(plugin, 60L, 60L);
    }

    public void refreshAll() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) apply(main, p);
    }

    public void refresh(Player p) {
        apply(Bukkit.getScoreboardManager().getMainScoreboard(), p);
    }

    public void forget(UUID uuid) {
        currentTeamByPlayer.remove(uuid);
        currentPrefixByPlayer.remove(uuid);
    }

    private void apply(Scoreboard main, Player p) {
        if (plugin.hunted() != null && plugin.hunted().isHunted(p.getUniqueId())) return;

        UUID uuid = p.getUniqueId();
        String bukkitTeamName = "smp_p" + safe(p.getName());
        if (bukkitTeamName.length() > 16) bukkitTeamName = bukkitTeamName.substring(0, 16);

        String rank = "";
        if (plugin.permissions() != null) {
            rank = plugin.permissions().prefixOf(uuid);
            if (rank == null) rank = "";
        }

        String teamTag = "";
        PlayerData d = plugin.players().get(p);
        if (d != null && d.teamId() != null) {
            TeamManager.Team gt = plugin.teams().get(d.teamId());
            if (gt != null) {
                teamTag = gt.color() + "[" + gt.tag() + "]<reset> ";
            }
        }

        String wantPrefix = rank + teamTag;

        String previousTeam = currentTeamByPlayer.get(uuid);
        if (previousTeam != null && !previousTeam.equals(bukkitTeamName)) {
            Team old = main.getTeam(previousTeam);
            if (old != null && old.hasEntry(p.getName())) {
                old.removeEntry(p.getName());
            }
        }

        Team team = main.getTeam(bukkitTeamName);
        if (team == null) team = main.registerNewTeam(bukkitTeamName);

        String lastPrefix = currentPrefixByPlayer.get(uuid);
        if (lastPrefix == null || !lastPrefix.equals(wantPrefix)) {
            team.prefix(wantPrefix.isEmpty() ? Component.empty() : MM.deserialize(wantPrefix));
            currentPrefixByPlayer.put(uuid, wantPrefix);
        }

        if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        currentTeamByPlayer.put(uuid, bukkitTeamName);
    }

    private String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
