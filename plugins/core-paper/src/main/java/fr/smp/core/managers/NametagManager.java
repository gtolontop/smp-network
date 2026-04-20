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
 * Renders a [TAG] prefix above each player's head by driving a single shared
 * scoreboard-team per game-team on the main scoreboard.
 *
 * Why main scoreboard: nametags above players always use the main scoreboard,
 * so we must write the prefix/suffix there — it does not matter that
 * {@link ScoreboardManager} hands each player their own sidebar scoreboard.
 *
 * Previous implementation walked every {@code smp_*} scoreboard-team per player
 * per refresh ({@code O(N * M)}), which scaled badly with many teams. We now
 * remember the scoreboard-team each player is currently assigned to and only
 * act when it changes — {@code O(changed players)} instead.
 */
public class NametagManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    /** uuid → bukkit scoreboard-team name the player is currently assigned to. */
    private final Map<UUID, String> currentTeamByPlayer = new HashMap<>();
    /** bukkit scoreboard-team name → last prefix string sent (to skip redundant prefix writes). */
    private final Map<String, String> currentPrefixByTeam = new HashMap<>();

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

    /**
     * Called from JoinListener / quit so we stop tracking someone who left and don't
     * leak their entry in the currentTeamByPlayer map over long uptimes.
     */
    public void forget(UUID uuid) {
        currentTeamByPlayer.remove(uuid);
    }

    private void apply(Scoreboard main, Player p) {
        // Hunted players are managed by HuntedManager (smp_hunted team with [CHASSÉ] prefix + red glow).
        if (plugin.hunted() != null && plugin.hunted().isHunted(p.getUniqueId())) return;

        PlayerData d = plugin.players().get(p);
        String teamId = d != null ? d.teamId() : null;
        String bukkitTeamName = teamId != null ? ("smp_" + safe(teamId)) : "smp_noteam";
        if (bukkitTeamName.length() > 16) bukkitTeamName = bukkitTeamName.substring(0, 16);

        // Compute the prefix we want.
        String wantPrefixKey;
        Component prefix;
        if (teamId != null) {
            TeamManager.Team gt = plugin.teams().get(teamId);
            if (gt != null) {
                String raw = gt.color() + "[" + gt.tag() + "]<reset> ";
                wantPrefixKey = raw;
                prefix = MM.deserialize(raw);
            } else {
                wantPrefixKey = "";
                prefix = Component.empty();
            }
        } else {
            wantPrefixKey = "";
            prefix = Component.empty();
        }

        // Move the player if their scoreboard-team changed. We only walk teams when we
        // know we need to remove the player from a stale assignment.
        String previousTeam = currentTeamByPlayer.get(p.getUniqueId());
        if (previousTeam != null && !previousTeam.equals(bukkitTeamName)) {
            Team old = main.getTeam(previousTeam);
            if (old != null && old.hasEntry(p.getName())) {
                old.removeEntry(p.getName());
            }
        }

        Team team = main.getTeam(bukkitTeamName);
        if (team == null) team = main.registerNewTeam(bukkitTeamName);

        // Only update prefix when it has actually changed — this is the packet-heavy
        // part. Prior code deserialised + set prefix every 60 ticks per player.
        String lastPrefixKey = currentPrefixByTeam.get(bukkitTeamName);
        if (lastPrefixKey == null || !lastPrefixKey.equals(wantPrefixKey)) {
            team.prefix(prefix);
            currentPrefixByTeam.put(bukkitTeamName, wantPrefixKey);
        }

        if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        currentTeamByPlayer.put(p.getUniqueId(), bukkitTeamName);
    }

    private String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
