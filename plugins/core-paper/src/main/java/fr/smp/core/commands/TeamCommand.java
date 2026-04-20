package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.gui.TeamsGUI;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TeamCommand implements CommandExecutor {

    private final SMPCore plugin;

    public TeamCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (args.length == 0) { new TeamsGUI(plugin).open(p); return true; }

        String sub = args[0].toLowerCase(Locale.ROOT);
        PlayerData d = plugin.players().get(p);

        switch (sub) {
            case "list" -> new TeamsGUI(plugin).open(p);

            case "create" -> {
                if (args.length < 3) { p.sendMessage(Msg.err("/team create <tag> <nom>")); return true; }
                if (d.teamId() != null) { p.sendMessage(Msg.err("Tu es déjà dans une team.")); return true; }
                String tag = args[1];
                if (!tag.matches("[A-Za-z0-9]{2,5}")) { p.sendMessage(Msg.err("Tag 2-5 alphanumérique.")); return true; }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                if (plugin.teams().byTag(tag) != null) { p.sendMessage(Msg.err("Tag déjà pris.")); return true; }
                double cost = plugin.teams().creationCost();
                if (!plugin.economy().has(p.getUniqueId(), cost)) {
                    p.sendMessage(Msg.err("Il te faut $" + Msg.money(cost) + ".")); return true;
                }
                plugin.economy().withdraw(p.getUniqueId(), cost, "team.create");
                String id = tag.toLowerCase();
                TeamManager.Team t = plugin.teams().create(id, tag, name, p.getUniqueId());
                if (t == null) {
                    plugin.economy().deposit(p.getUniqueId(), cost, "team.create.refund");
                    p.sendMessage(Msg.err("Création échouée.")); return true;
                }
                p.sendMessage(Msg.ok("<green>Team <aqua>[" + tag + "] " + name + "</aqua> créée.</green>"));
            }

            case "invite" -> {
                if (d.teamId() == null) { p.sendMessage(Msg.err("Tu n'es pas dans une team.")); return true; }
                if (args.length < 2) { p.sendMessage(Msg.err("/team invite <joueur>")); return true; }
                TeamManager.Team t = plugin.teams().get(d.teamId());
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    PlayerData td = plugin.players().get(target);
                    if (td == null || td.teamId() != null) { p.sendMessage(Msg.err("Déjà dans une team.")); return true; }
                    plugin.teamInvites().invite(target.getUniqueId(), d.teamId());
                    target.sendMessage(Msg.info("<aqua>" + p.getName() + "</aqua> t'invite dans <white>" +
                            t.color() + "[" + t.tag() + "] " + t.name() + "<reset></white>. <green>/team join " + t.tag() + "</green>"));
                    p.sendMessage(Msg.ok("<green>Invitation envoyée.</green>"));
                } else {
                    var entry = plugin.roster() != null ? plugin.roster().get(args[1]) : null;
                    if (entry == null) { p.sendMessage(Msg.err("Joueur hors-ligne.")); return true; }
                    final String teamId = t.id();
                    final String teamTag = t.tag();
                    final String teamColor = t.color() == null ? "" : t.color();
                    final String teamName = t.name();
                    plugin.getMessageChannel().sendForward(entry.name(), "team-invite", o -> {
                        o.writeUTF(p.getName());
                        o.writeUTF(teamId);
                        o.writeUTF(teamTag);
                        o.writeUTF(teamColor);
                        o.writeUTF(teamName);
                    });
                    p.sendMessage(Msg.ok("<green>Invitation envoyée à " + entry.name() +
                            " <gray>(" + entry.server() + ")</gray>.</green>"));
                }
            }

            case "join" -> {
                if (d.teamId() != null) { p.sendMessage(Msg.err("Tu es déjà dans une team.")); return true; }
                if (args.length < 2) { p.sendMessage(Msg.err("/team join <tag>")); return true; }
                TeamManager.Team t = plugin.teams().byTag(args[1]);
                if (t == null) { p.sendMessage(Msg.err("Team introuvable.")); return true; }
                String invited = plugin.teamInvites().peek(p.getUniqueId());
                if (invited == null || !invited.equals(t.id())) {
                    p.sendMessage(Msg.err("Aucune invitation pour cette team.")); return true;
                }
                plugin.teamInvites().consume(p.getUniqueId());
                plugin.teams().addMember(t.id(), p.getUniqueId());
                plugin.tabList().update(p);
                if (plugin.nametags() != null) plugin.nametags().refreshAll();
                p.sendMessage(Msg.ok("<green>Tu as rejoint <aqua>" + t.name() + "</aqua>.</green>"));
            }

            case "leave" -> {
                if (d.teamId() == null) { p.sendMessage(Msg.err("Pas dans une team.")); return true; }
                TeamManager.Team t = plugin.teams().get(d.teamId());
                if (t != null && t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner: utilise /team disband.")); return true;
                }
                plugin.teams().removeMember(d.teamId(), p.getUniqueId());
                p.sendMessage(Msg.ok("<red>Team quittée.</red>"));
            }

            case "kick" -> {
                if (d.teamId() == null || args.length < 2) { p.sendMessage(Msg.err("/team kick <joueur>")); return true; }
                TeamManager.Team t = plugin.teams().get(d.teamId());
                if (!t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner uniquement.")); return true;
                }
                UUID u = plugin.players().resolveUuid(args[1]);
                if (u == null) { p.sendMessage(Msg.err("Joueur inconnu.")); return true; }
                plugin.teams().removeMember(t.id(), u);
                p.sendMessage(Msg.ok("<red>" + args[1] + " kické.</red>"));
            }

            case "disband" -> {
                if (d.teamId() == null) { p.sendMessage(Msg.err("Pas dans une team.")); return true; }
                TeamManager.Team t = plugin.teams().get(d.teamId());
                if (!t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner uniquement.")); return true;
                }
                for (TeamManager.Member m : plugin.teams().members(t.id())) {
                    plugin.teams().removeMember(t.id(), m.uuid());
                }
                plugin.teams().disband(t.id());
                p.sendMessage(Msg.ok("<red>Team dissoute.</red>"));
                plugin.logs().log(LogCategory.TEAM, "disband id=" + t.id());
            }

            case "sethome" -> {
                if (d.teamId() == null) { p.sendMessage(Msg.err("Pas dans une team.")); return true; }
                TeamManager.Team t = plugin.teams().get(d.teamId());
                if (!t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner uniquement.")); return true;
                }
                plugin.teams().setHome(t.id(), p.getLocation());
                p.sendMessage(Msg.ok("<green>Home de team défini.</green>"));
            }

            case "home" -> {
                if (d.teamId() == null) { p.sendMessage(Msg.err("Pas dans une team.")); return true; }
                TeamManager.Team t = plugin.teams().get(d.teamId());
                if (t.home() == null) { p.sendMessage(Msg.err("Pas de home de team.")); return true; }
                p.teleportAsync(t.home());
                p.sendMessage(Msg.ok("<aqua>Téléporté au home de team.</aqua>"));
            }

            case "info" -> {
                TeamManager.Team t = args.length > 1
                        ? plugin.teams().byTag(args[1])
                        : (d.teamId() != null ? plugin.teams().get(d.teamId()) : null);
                if (t == null) { p.sendMessage(Msg.err("Team introuvable.")); return true; }
                List<TeamManager.Member> members = plugin.teams().members(t.id());
                p.sendMessage(Msg.info("<aqua>" + t.color() + "[" + t.tag() + "] " + t.name() + "<reset></aqua> <gray>(" + members.size() + " membres)</gray>"));
                for (TeamManager.Member m : members) {
                    String nm = Bukkit.getOfflinePlayer(m.uuid()).getName();
                    p.sendMessage(Msg.mm("<gray>• <white>" + nm + "</white> <dark_gray>" + m.role() + "</dark_gray>"));
                }
            }

            case "color" -> {
                if (d.teamId() == null || args.length < 2) { p.sendMessage(Msg.err("/team color <mini-tag>")); return true; }
                TeamManager.Team t = plugin.teams().get(d.teamId());
                if (!t.owner().equals(p.getUniqueId().toString())) {
                    p.sendMessage(Msg.err("Owner uniquement.")); return true;
                }
                plugin.teams().setColor(t.id(), args[1]);
                p.sendMessage(Msg.ok("<green>Couleur mise à jour.</green>"));
            }

            default -> p.sendMessage(Msg.err("Sous-commandes: create, list, invite, join, leave, kick, disband, sethome, home, info, color"));
        }
        return true;
    }
}
