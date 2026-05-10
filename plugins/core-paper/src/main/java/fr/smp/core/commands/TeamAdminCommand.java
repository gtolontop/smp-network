package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import fr.smp.core.utils.NetworkTabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TeamAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "help", "list", "info", "create", "disband", "rename", "settag", "color",
            "setowner", "add", "remove", "move", "role", "sethome", "clearhome",
            "balance", "audit", "repair");
    private static final List<String> ROLES = List.of("owner", "officer", "member");
    private static final List<String> BALANCE_ACTIONS = List.of("get", "set", "give", "take");

    private final SMPCore plugin;
    private final NetworkTabCompleter network;

    public TeamAdminCommand(SMPCore plugin) {
        this.plugin = plugin;
        this.network = new NetworkTabCompleter(plugin, -1, false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusee."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "create" -> handleCreate(sender, args);
            case "disband" -> handleDisband(sender, args);
            case "rename" -> handleRename(sender, args);
            case "settag" -> handleSetTag(sender, args);
            case "color" -> handleColor(sender, args);
            case "setowner" -> handleSetOwner(sender, args);
            case "add" -> handleAdd(sender, args);
            case "remove", "kick" -> handleRemove(sender, args);
            case "move" -> handleMove(sender, args);
            case "role" -> handleRole(sender, args);
            case "sethome" -> handleSetHome(sender, args);
            case "clearhome" -> handleClearHome(sender, args);
            case "balance" -> handleBalance(sender, args);
            case "audit" -> handleAudit(sender, args);
            case "repair" -> handleRepair(sender, args);
            default -> sender.sendMessage(Msg.err("Sous-commandes: " + String.join(", ", SUBS)));
        }
        return true;
    }

    private void handleList(CommandSender sender, String[] args) {
        int page = args.length >= 2 ? parsePage(args[1]) : 1;
        List<TeamManager.Team> teams = new ArrayList<>(plugin.teams().list());
        teams.sort(Comparator.comparing(TeamManager.Team::name, String.CASE_INSENSITIVE_ORDER));
        int perPage = 8;
        int pages = Math.max(1, (int) Math.ceil(teams.size() / (double) perPage));
        page = Math.min(page, pages);
        sender.sendMessage(Msg.info("<aqua>Teams</aqua> <gray>(" + teams.size() + ") page " + page + "/" + pages + "</gray>"));
        int from = (page - 1) * perPage;
        int to = Math.min(teams.size(), from + perPage);
        for (int i = from; i < to; i++) {
            TeamManager.Team team = teams.get(i);
            sender.sendMessage(Msg.mm("<gray>- <white>" + team.tag() + "</white> <aqua>" + team.name()
                    + "</aqua> <gray>id=" + team.id() + " members=" + plugin.teams().memberCount(team.id())
                    + " balance=$" + Msg.money(team.balance()) + "</gray>"));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        sendTeamInfo(sender, team);
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Msg.err("/teamadmin create <tag> <owner> <name...>"));
            return;
        }
        String tag = args[1];
        if (!isValidTag(tag)) {
            sender.sendMessage(Msg.err("Tag invalide: 2-5 caracteres alphanumeriques."));
            return;
        }
        if (plugin.teams().byTag(tag) != null) {
            sender.sendMessage(Msg.err("Tag deja pris."));
            return;
        }
        UUID owner = resolvePlayer(sender, args[2]);
        if (owner == null) return;
        String existingTeam = plugin.teams().teamOf(owner);
        if (existingTeam != null) {
            sender.sendMessage(Msg.err("Ce joueur est deja dans une team. Utilise /teamadmin move."));
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (name.isBlank()) {
            sender.sendMessage(Msg.err("Nom invalide."));
            return;
        }
        TeamManager.Team team = plugin.teams().create(tag.toLowerCase(Locale.ROOT), tag, name, owner);
        if (team == null) {
            sender.sendMessage(Msg.err("Creation echouee."));
            return;
        }
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Team creee: <aqua>" + team.tag() + "</aqua>.</green>"));
    }

    private void handleDisband(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        for (TeamManager.Member member : plugin.teams().members(team.id())) {
            plugin.teams().removeMember(team.id(), member.uuid());
        }
        plugin.teams().disband(team.id());
        refreshTeamState();
        plugin.logs().log(LogCategory.TEAM, "admin.disband id=" + team.id() + " by=" + sender.getName());
        sender.sendMessage(Msg.ok("<red>Team dissoute.</red>"));
    }

    private void handleRename(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/teamadmin rename <team> <name...>"));
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (name.isBlank()) {
            sender.sendMessage(Msg.err("Nom invalide."));
            return;
        }
        plugin.teams().setName(team.id(), name);
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Nom de team mis a jour.</green>"));
    }

    private void handleSetTag(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/teamadmin settag <team> <tag>"));
            return;
        }
        String tag = args[2];
        if (!isValidTag(tag)) {
            sender.sendMessage(Msg.err("Tag invalide: 2-5 caracteres alphanumeriques."));
            return;
        }
        TeamManager.Team taken = plugin.teams().byTag(tag);
        if (taken != null && !taken.id().equalsIgnoreCase(team.id())) {
            sender.sendMessage(Msg.err("Tag deja pris."));
            return;
        }
        if (!plugin.teams().setTag(team.id(), tag)) {
            sender.sendMessage(Msg.err("Mise a jour echouee."));
            return;
        }
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Tag mis a jour.</green>"));
    }

    private void handleColor(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/teamadmin color <team> <mini-tag>"));
            return;
        }
        plugin.teams().setColor(team.id(), args[2]);
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Couleur mise a jour.</green>"));
    }

    private void handleSetOwner(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/teamadmin setowner <team> <player>"));
            return;
        }
        UUID owner = resolvePlayer(sender, args[2]);
        if (owner == null) return;
        String previousTeam = plugin.teams().teamOf(owner);
        if (previousTeam != null && !previousTeam.equalsIgnoreCase(team.id())) {
            plugin.teams().removeMember(previousTeam, owner);
        }
        if (!plugin.teams().setOwner(team.id(), owner)) {
            sender.sendMessage(Msg.err("Changement d'owner echoue."));
            return;
        }
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Owner mis a jour.</green>"));
    }

    private void handleAdd(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/teamadmin add <team> <player> [owner|officer|member]"));
            return;
        }
        UUID uuid = resolvePlayer(sender, args[2]);
        if (uuid == null) return;
        TeamManager.Role role = args.length >= 4 ? parseRole(args[3]) : TeamManager.Role.MEMBER;
        if (role == null) {
            sender.sendMessage(Msg.err("Role invalide: owner, officer, member."));
            return;
        }
        String previousTeam = plugin.teams().teamOf(uuid);
        if (previousTeam != null && !previousTeam.equalsIgnoreCase(team.id())) {
            sender.sendMessage(Msg.err("Ce joueur est deja dans une autre team. Utilise /teamadmin move."));
            return;
        }
        plugin.teams().addMember(team.id(), uuid, role);
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Joueur ajoute.</green>"));
    }

    private void handleMove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/teamadmin move <player> <team> [owner|officer|member]"));
            return;
        }
        TeamManager.Team team = requireTeam(sender, args, 2);
        if (team == null) return;
        UUID uuid = resolvePlayer(sender, args[1]);
        if (uuid == null) return;
        TeamManager.Role role = args.length >= 4 ? parseRole(args[3]) : TeamManager.Role.MEMBER;
        if (role == null) {
            sender.sendMessage(Msg.err("Role invalide: owner, officer, member."));
            return;
        }
        String previousTeam = plugin.teams().teamOf(uuid);
        if (previousTeam != null && !previousTeam.equalsIgnoreCase(team.id())) {
            plugin.teams().removeMember(previousTeam, uuid);
            repairOwnerAfterRemoval(previousTeam);
        }
        plugin.teams().addMember(team.id(), uuid, role);
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Joueur deplace.</green>"));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("/teamadmin remove <player>"));
            return;
        }
        UUID uuid = resolvePlayer(sender, args[1]);
        if (uuid == null) return;
        String teamId = plugin.teams().teamOf(uuid);
        if (teamId == null) {
            PlayerData data = plugin.players().loadOffline(uuid);
            if (data != null && data.teamId() != null) {
                data.setTeamId(null);
                plugin.players().save(data);
                sender.sendMessage(Msg.ok("<green>Lien team obsolete nettoye.</green>"));
                return;
            }
            sender.sendMessage(Msg.err("Ce joueur n'est dans aucune team."));
            return;
        }
        plugin.teams().removeMember(teamId, uuid);
        repairOwnerAfterRemoval(teamId);
        refreshTeamState();
        sender.sendMessage(Msg.ok("<red>Joueur retire de sa team.</red>"));
    }

    private void handleRole(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/teamadmin role <player> <owner|officer|member>"));
            return;
        }
        UUID uuid = resolvePlayer(sender, args[1]);
        if (uuid == null) return;
        TeamManager.Role role = parseRole(args[2]);
        if (role == null) {
            sender.sendMessage(Msg.err("Role invalide: owner, officer, member."));
            return;
        }
        String teamId = plugin.teams().teamOf(uuid);
        if (teamId == null) {
            sender.sendMessage(Msg.err("Ce joueur n'est dans aucune team."));
            return;
        }
        if (!plugin.teams().setRole(teamId, uuid, role)) {
            sender.sendMessage(Msg.err("Role non mis a jour."));
            return;
        }
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Role mis a jour.</green>"));
    }

    private void handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.err("Commande joueur uniquement."));
            return;
        }
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        plugin.teams().setHome(team.id(), player.getLocation());
        sender.sendMessage(Msg.ok("<green>Home de team defini.</green>"));
    }

    private void handleClearHome(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        plugin.teams().setHome(team.id(), null);
        sender.sendMessage(Msg.ok("<green>Home de team supprime.</green>"));
    }

    private void handleBalance(CommandSender sender, String[] args) {
        TeamManager.Team team = requireTeam(sender, args, 1);
        if (team == null) return;
        if (args.length < 3) {
            sender.sendMessage(Msg.info("<green>$" + Msg.money(team.balance()) + "</green> <gray>pour</gray> <aqua>" + team.tag() + "</aqua>"));
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("get")) {
            sender.sendMessage(Msg.info("<green>$" + Msg.money(team.balance()) + "</green> <gray>pour</gray> <aqua>" + team.tag() + "</aqua>"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Msg.err("/teamadmin balance <team> get|set|give|take [amount]"));
            return;
        }
        double amount = Msg.parseAmount(args[3]);
        if (amount < 0) {
            sender.sendMessage(Msg.err("Montant invalide."));
            return;
        }
        boolean ok = switch (action) {
            case "set" -> plugin.teams().setBalance(team.id(), amount);
            case "give", "add" -> plugin.teams().addBalance(team.id(), amount);
            case "take", "remove" -> plugin.teams().addBalance(team.id(), -amount);
            default -> false;
        };
        if (!ok) {
            sender.sendMessage(Msg.err("Action invalide ou echouee."));
            return;
        }
        sender.sendMessage(Msg.ok("<green>Balance de team mise a jour.</green>"));
    }

    private void handleAudit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("/teamadmin audit <player>"));
            return;
        }
        UUID uuid = resolvePlayer(sender, args[1]);
        if (uuid == null) return;
        PlayerData data = plugin.players().loadOffline(uuid);
        String memberTeam = plugin.teams().teamOf(uuid);
        String dataTeam = data != null ? data.teamId() : null;
        sender.sendMessage(Msg.info("<aqua>Audit team</aqua> <gray>player=" + playerName(uuid) + "</gray>"));
        sender.sendMessage(Msg.mm("<gray>- membership: <white>" + value(memberTeam) + "</white></gray>"));
        sender.sendMessage(Msg.mm("<gray>- player_data: <white>" + value(dataTeam) + "</white></gray>"));
        if (memberTeam != null) {
            TeamManager.Team team = plugin.teams().get(memberTeam);
            if (team != null) sender.sendMessage(Msg.mm("<gray>- team: <white>" + team.tag() + " / " + team.name() + "</white></gray>"));
        }
    }

    private void handleRepair(CommandSender sender, String[] args) {
        int count;
        if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
            TeamManager.Team team = requireTeam(sender, args, 1);
            if (team == null) return;
            count = plugin.teams().repairPlayerLinks(team.id());
        } else {
            count = plugin.teams().repairAllPlayerLinks();
        }
        refreshTeamState();
        sender.sendMessage(Msg.ok("<green>Repair termine: " + count + " lien(s) corrige(s).</green>"));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Msg.info("<aqua>Admin teams</aqua>"));
        sender.sendMessage(Msg.mm("<gray>/" + label + " list [page] | info <team> | audit <player></gray>"));
        sender.sendMessage(Msg.mm("<gray>/" + label + " create <tag> <owner> <name...> | disband <team></gray>"));
        sender.sendMessage(Msg.mm("<gray>/" + label + " rename <team> <name...> | settag <team> <tag> | color <team> <mini-tag></gray>"));
        sender.sendMessage(Msg.mm("<gray>/" + label + " add <team> <player> [role] | move <player> <team> [role] | remove <player></gray>"));
        sender.sendMessage(Msg.mm("<gray>/" + label + " setowner <team> <player> | role <player> <role></gray>"));
        sender.sendMessage(Msg.mm("<gray>/" + label + " sethome <team> | clearhome <team> | balance <team> get|set|give|take [amount]</gray>"));
        sender.sendMessage(Msg.mm("<gray>/" + label + " repair [team|all]</gray>"));
    }

    private void sendTeamInfo(CommandSender sender, TeamManager.Team team) {
        sender.sendMessage(Msg.info("<aqua>" + team.color() + "[" + team.tag() + "] " + team.name() + "<reset></aqua>"
                + " <gray>id=" + team.id() + " balance=$" + Msg.money(team.balance()) + "</gray>"));
        sender.sendMessage(Msg.mm("<gray>Owner: <white>" + playerName(UUID.fromString(team.owner())) + "</white></gray>"));
        sender.sendMessage(Msg.mm("<gray>Home: <white>" + (team.home() == null ? "none" : formatLocation(team.home())) + "</white></gray>"));
        for (TeamManager.Member member : plugin.teams().members(team.id())) {
            sender.sendMessage(Msg.mm("<gray>- <white>" + playerName(member.uuid()) + "</white> <dark_gray>" + member.role() + "</dark_gray></gray>"));
        }
    }

    private TeamManager.Team requireTeam(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(Msg.err("Team manquante."));
            return null;
        }
        TeamManager.Team team = findTeam(args[index]);
        if (team == null) sender.sendMessage(Msg.err("Team introuvable."));
        return team;
    }

    private TeamManager.Team findTeam(String input) {
        TeamManager.Team byTag = plugin.teams().byTag(input);
        return byTag != null ? byTag : plugin.teams().get(input);
    }

    private UUID resolvePlayer(CommandSender sender, String name) {
        UUID uuid = plugin.players().resolveUuid(name);
        if (uuid == null) {
            sender.sendMessage(Msg.err("Joueur inconnu."));
        }
        return uuid;
    }

    private void repairOwnerAfterRemoval(String teamId) {
        TeamManager.Team team = plugin.teams().get(teamId);
        if (team == null) return;
        List<TeamManager.Member> members = plugin.teams().members(teamId);
        boolean ownerStillPresent = members.stream().anyMatch(m -> m.uuid().toString().equals(team.owner()));
        if (ownerStillPresent) return;
        if (members.isEmpty()) {
            plugin.teams().disband(teamId);
            plugin.logs().log(LogCategory.TEAM, "admin.autodisband-empty id=" + teamId);
            return;
        }
        plugin.teams().setOwner(teamId, members.get(0).uuid());
    }

    private void refreshTeamState() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.tabList() != null) plugin.tabList().update(player);
        }
        if (plugin.nametags() != null) plugin.nametags().refreshAll();
    }

    private static boolean isValidTag(String tag) {
        return tag != null && tag.matches("[A-Za-z0-9]{2,5}");
    }

    private static TeamManager.Role parseRole(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "owner", "leader", "chief" -> TeamManager.Role.OWNER;
            case "officer", "staff", "mod" -> TeamManager.Role.OFFICER;
            case "member", "membre" -> TeamManager.Role.MEMBER;
            default -> null;
        };
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String playerName(UUID uuid) {
        PlayerData data = plugin.players().loadOffline(uuid);
        if (data != null && data.name() != null) return data.name();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    private static String value(String input) {
        return input == null ? "none" : input;
    }

    private static String formatLocation(org.bukkit.Location location) {
        return location.getWorld().getName() + " "
                + location.getBlockX() + " "
                + location.getBlockY() + " "
                + location.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 0) return SUBS;
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return filter(SUBS, prefix);

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (List.of("info", "disband", "rename", "settag", "color", "setowner", "add", "sethome", "clearhome", "balance", "repair").contains(sub)) {
                return teamTags(prefix);
            }
            if (List.of("move", "remove", "kick", "role", "audit").contains(sub)) {
                return network.networkPlayerNames(sender, prefix);
            }
        }
        if (args.length == 3) {
            if (List.of("setowner", "add", "create").contains(sub)) return network.networkPlayerNames(sender, prefix);
            if (sub.equals("move")) return teamTags(prefix);
            if (sub.equals("role")) return filter(ROLES, prefix);
            if (sub.equals("balance")) return filter(BALANCE_ACTIONS, prefix);
        }
        if (args.length == 4 && (sub.equals("add") || sub.equals("move"))) {
            return filter(ROLES, prefix);
        }
        return List.of();
    }

    private List<String> teamTags(String prefix) {
        List<String> tags = new ArrayList<>();
        if (plugin.teams() != null) {
            plugin.teams().list().forEach(t -> tags.add(t.tag()));
        }
        return filter(tags, prefix);
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (prefix.isEmpty() || option.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(option);
        }
        return out;
    }
}
