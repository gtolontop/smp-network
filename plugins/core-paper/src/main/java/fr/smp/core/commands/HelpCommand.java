package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HelpCommand implements CommandExecutor {

    private static final String TITLE = "<gradient:#67e8f9:#a78bfa><bold>✦ Aide SMP ✦</bold></gradient>";
    private static final String SEP = "<dark_gray>──────────────</dark_gray>";
    private static final String CMD = "<aqua>%s</aqua> <dark_gray>—</dark_gray> <gray>%s</gray>";

    private static final List<String> PAGE_1 = List.of(
            "/spawn — Retourner au spawn",
            "/rtp — Téléportation aléatoire",
            "/home [name] — Aller à un home",
            "/sethome [name] — Définir un home",
            "/warp [name] — Aller à un warp",
            "/bal — Voir son argent",
            "/pay <joueur> <montant> — Envoyer de l'argent",
            "/sell — Vendre l'item en main",
            "/sellall — Vendre tout l'inventaire",
            "/worth — Voir la valeur d'un item"
    );

    private static final List<String> PAGE_2 = List.of(
            "/shop — Boutique du serveur",
            "/ah — Hôtel des ventes",
            "/team — Gérer son équipe",
            "/msg <joueur> — Message privé",
            "/tpa <joueur> — Demande de TP",
            "/bounty — Système de primes",
            "/duel — Défier un joueur",
            "/leaderboard — Classements",
            "/playtime — Temps de jeu",
            "/stat [joueur] — Statistiques"
    );

    private static final List<String> ADMIN_PAGE = List.of(
            "/admin — Mode administrateur",
            "/tp <joueur> — Téléportation",
            "/fly — Voler",
            "/god — Mode invincible",
            "/heal — Se soigner",
            "/vanish — Invisibilité",
            "/invsee <joueur> — Voir un inventaire",
            "/gamemode — Changer de mode",
            "/kick <joueur> — Expulser un joueur",
            "/ban <joueur> — Bannir un joueur",
            "/mute <joueur> — Rendre muet",
            "/eco <give|take|set> — Gérer l'économie",
            "/give — Donner des items",
            "/nick — Changer de pseudo",
            "/speed — Changer de vitesse",
            "/repair — Réparer un item"
    );

    private final SMPCore plugin;

    public HelpCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("smp.admin")) {
                sender.sendMessage(Msg.err("Permission refusée."));
                return true;
            }
            sendAdminPage(sender);
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        if (page < 1) page = 1;
        if (page > 2) page = 2;

        sendPage(sender, page);
        return true;
    }

    private void sendPage(CommandSender sender, int page) {
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm(TITLE));
        sender.sendMessage(Msg.mm(SEP));

        List<String> cmds = page == 1 ? PAGE_1 : PAGE_2;
        for (String entry : cmds) {
            String[] parts = entry.split(" — ", 2);
            sender.sendMessage(Msg.mm(String.format(CMD, parts[0], parts[1])));
        }

        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm("<gray>Page <white>" + page + "</white>/2</gray>" + navHint(page, 2)));
        if (sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.mm("<dark_gray>»</dark_gray> <gray>Tapez <aqua>/help admin</aqua> pour les commandes admin</gray>"));
        }
        sender.sendMessage(Msg.mm(""));
    }

    private void sendAdminPage(CommandSender sender) {
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gradient:#ff6b6b:#ee5a24><bold>✦ Aide Admin ✦</bold></gradient>"));
        sender.sendMessage(Msg.mm(SEP));

        for (String entry : ADMIN_PAGE) {
            String[] parts = entry.split(" — ", 2);
            sender.sendMessage(Msg.mm(String.format(CMD, parts[0], parts[1])));
        }

        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm("<gray>Page <white>admin</white> <dark_gray>|</dark_gray> <aqua>/help 1</aqua> pour revenir</gray>"));
        sender.sendMessage(Msg.mm(""));
    }

    private String navHint(int current, int max) {
        StringBuilder sb = new StringBuilder();
        if (current > 1) {
            sb.append(" <dark_gray>|</dark_gray> <aqua>◀ /help ").append(current - 1).append("</aqua>");
        }
        if (current < max) {
            sb.append(" <dark_gray>|</dark_gray> <aqua>/help ").append(current + 1).append(" ▶</aqua>");
        }
        return sb.toString();
    }
}
