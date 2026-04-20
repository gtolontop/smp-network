package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;


/**
 * Updates each player's tab-list entry with their rank + team prefix, plus
 * network-wide header/footer built from the proxy roster so both servers
 * feel like one.
 */
public class TabListManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final SMPCore plugin;

    public TabListManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    update(p);
                    updateNetworkHeaderFooter(p);
                }
                // Push our roster so the proxy can aggregate.
                if (plugin.getMessageChannel() != null) {
                    plugin.getMessageChannel().sendRoster();
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    public void update(Player p) {
        String rank = "";
        if (plugin.permissions() != null) {
            rank = plugin.permissions().prefixOf(p.getUniqueId());
            if (rank == null) rank = "";
        }
        String teamPrefix = "";
        PlayerData d = plugin.players().get(p);
        if (d != null && d.teamId() != null) {
            TeamManager.Team t = plugin.teams().get(d.teamId());
            if (t != null) teamPrefix = t.color() + "[" + t.tag() + "]<reset> ";
        }
        String huntedPrefix = "";
        if (plugin.hunted() != null && plugin.hunted().isHunted(p.getUniqueId())) {
            huntedPrefix = "<dark_red>[<red><bold>CHASSÉ</bold></red>";
            if (plugin.bounties() != null) {
                BountyManager.Bounty b = plugin.bounties().get(p.getUniqueId());
                if (b != null && b.amount() > 0) {
                    huntedPrefix += "<dark_red> • <gold>$" + Msg.money(b.amount()) + "</gold>";
                }
            }
            huntedPrefix += "<dark_red>]</dark_red> ";
        }
        Component comp = MM.deserialize(rank + huntedPrefix + teamPrefix + "<white>" + p.getName() + "</white>");
        p.playerListName(comp);
    }

    public void updateNetworkHeaderFooter(Player viewer) {
        NetworkRoster roster = plugin.roster();
        if (roster == null) return;

        int total = roster.all().size();

        String header = "<gradient:#67e8f9:#a78bfa><bold>SaphirSMP</bold></gradient>\n" +
                "<gray>" + total + " joueur" + (total > 1 ? "s" : "") + " en ligne</gray>";

        viewer.sendPlayerListHeaderAndFooter(MM.deserialize(header), Component.empty());
    }
}
