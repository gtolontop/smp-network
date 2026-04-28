package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TutoCommand implements CommandExecutor, TabCompleter {

    private static final String SEP = "<dark_gray>──────────────</dark_gray>";

    private static final Map<String, String[]> TOPICS = new LinkedHashMap<>();

    static {
        TOPICS.put("start", new String[]{"debut", "1"});
        TOPICS.put("money", new String[]{"argent", "2"});
        TOPICS.put("homes", new String[]{"3"});
        TOPICS.put("teams", new String[]{"4"});
        TOPICS.put("enchant", new String[]{"5"});
        TOPICS.put("combat", new String[]{"6"});
        TOPICS.put("rtp", new String[]{"7"});
    }

    private final SMPCore plugin;

    public TutoCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendIndex(sender);
            return true;
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        String topic = resolveTopic(input);
        if (topic == null) {
            sender.sendMessage(Msg.err("Sujet introuvable. Tapez <white>/tuto</white> pour la liste."));
            return true;
        }

        switch (topic) {
            case "start" -> sendStart(sender);
            case "money" -> sendMoney(sender);
            case "homes" -> sendHomes(sender);
            case "teams" -> sendTeams(sender);
            case "enchant" -> sendEnchant(sender);
            case "combat" -> sendCombat(sender);
            case "rtp" -> sendRtp(sender);
        }
        return true;
    }

    private String resolveTopic(String input) {
        for (Map.Entry<String, String[]> entry : TOPICS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(input)) return entry.getKey();
            for (String alias : entry.getValue()) {
                if (alias.equalsIgnoreCase(input)) return entry.getKey();
            }
        }
        return null;
    }

    private void sendIndex(CommandSender sender) {
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gradient:#ffd700:#ff8a00><bold>✦ Tutoriels SMP ✦</bold></gradient>"));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm("<gray>Bienvenue sur le serveur ! Choisissez un sujet :</gray>"));
        sender.sendMessage(Msg.mm(""));

        int i = 1;
        for (String topic : TOPICS.keySet()) {
            sender.sendMessage(Msg.mm("  <aqua>" + i + ".</aqua> <white>/tuto " + topic + "</white> <dark_gray>—</dark_gray> <gray>" + topicLabel(topic) + "</gray>"));
            i++;
        }

        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm("<gray>Utilisez <aqua>/tuto <sujet></aqua> pour en savoir plus.</gray>"));
        sender.sendMessage(Msg.mm(""));
    }

    private String topicLabel(String topic) {
        return switch (topic) {
            case "start" -> "Bien débuter";
            case "money" -> "Économie & argent";
            case "homes" -> "Système de homes";
            case "teams" -> "Équipes";
            case "enchant" -> "Enchantements custom";
            case "combat" -> "Combat & PvP";
            case "rtp" -> "Téléportation aléatoire";
            default -> topic;
        };
    }

    private void topicHeader(CommandSender sender, String title) {
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gradient:#ffd700:#ff8a00><bold>✦ " + title + " ✦</bold></gradient>"));
        sender.sendMessage(Msg.mm(SEP));
    }

    private void cmdLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Msg.mm("<aqua>" + cmd + "</aqua> <dark_gray>—</dark_gray> <gray>" + desc + "</gray>"));
    }

    private void sendStart(CommandSender sender) {
        topicHeader(sender, "Bien débuter");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Pour commencer votre aventure sur le serveur :</gray>"));
        sender.sendMessage(Msg.mm(""));
        cmdLine(sender, "/rtp", "Téléportez-vous aléatoirement dans le monde pour trouver un endroit vierge.");
        cmdLine(sender, "/spawn", "Retournez au spawn à tout moment.");
        cmdLine(sender, "/home [name]", "Téléportez-vous vers un home que vous avez défini.");
        cmdLine(sender, "/sethome [name]", "Définissez un point de téléportation personnel.");
        cmdLine(sender, "/warp [name]", "Allez vers un warp public (boutiques, arènes…).");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Conseil : utilisez <aqua>/tuto</aqua> pour explorer toutes les fonctionnalités du serveur !</gray>"));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm(""));
    }

    private void sendMoney(CommandSender sender) {
        topicHeader(sender, "Économie & argent");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Le serveur dispose d'un système économique complet :</gray>"));
        sender.sendMessage(Msg.mm(""));
        cmdLine(sender, "/bal", "Consultez votre solde actuel.");
        cmdLine(sender, "/pay <joueur> <montant>", "Envoyez de l'argent à un autre joueur.");
        cmdLine(sender, "/sell", "Vendez l'item que vous tenez en main.");
        cmdLine(sender, "/sellall", "Vendez tous les items vendables de votre inventaire.");
        cmdLine(sender, "/worth", "Vérifiez la valeur d'un item avant de le vendre.");
        cmdLine(sender, "/shop", "Ouvrez la boutique du serveur pour acheter des items.");
        cmdLine(sender, "/ah", "Accédez à l'hôtel des ventes (enchères entre joueurs).");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Astuce : les items ont une valeur affichée en <aqua>survolant</aqua> dans l'inventaire.</gray>"));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm(""));
    }

    private void sendHomes(CommandSender sender) {
        topicHeader(sender, "Système de homes");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Les homes vous permettent de sauvegarder des points de téléportation :</gray>"));
        sender.sendMessage(Msg.mm(""));
        cmdLine(sender, "/home [name]", "Vous téléporter vers un home existant.");
        cmdLine(sender, "/sethome [name]", "Créer un home à votre position actuelle.");
        cmdLine(sender, "/delhome [name]", "Supprimer un home.");
        cmdLine(sender, "/homes", "Lister tous vos homes.");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Le nombre de homes dépend de votre <aqua>grade</aqua> :</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <white>Default</white> — 2 homes</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <white>VIP</white> — 5 homes</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <white>VIP+</white> — 10 homes</gray>"));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm(""));
    }

    private void sendTeams(CommandSender sender) {
        topicHeader(sender, "Équipes");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Créez ou rejoignez une équipe pour jouer ensemble :</gray>"));
        sender.sendMessage(Msg.mm(""));
        cmdLine(sender, "/team create <nom>", "Créer une nouvelle équipe.");
        cmdLine(sender, "/team invite <joueur>", "Inviter un joueur dans votre équipe.");
        cmdLine(sender, "/team leave", "Quitter votre équipe actuelle.");
        cmdLine(sender, "/team info", "Voir les informations de votre équipe.");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Fonctionnalités d'équipe :</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Banque partagée</aqua> — déposez et retirez de l'argent en commun</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Tag</aqua> — un préfixe personnalisé visible dans le chat</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Homes d'équipe</aqua> — des points de TP accessibles à tous les membres</gray>"));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm(""));
    }

    private void sendEnchant(CommandSender sender) {
        topicHeader(sender, "Enchantements custom");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Le serveur propose des enchantements uniques et puissants :</gray>"));
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Table d'enchantement</aqua> — chance d'obtenir un enchantement custom en enchantant normalement</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Livres enchantés</aqua> — trouvés dans les loots ou la boutique</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Enclume</aqua> — fusionnez un livre custom avec votre équipement via l'enclume</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Overcap</aqua> — certains enchantements peuvent dépasser le niveau vanilla maximum</gray>"));
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Les enchantements custom sont plus rares mais bien plus puissants que les vanilla.</gray>"));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm(""));
    }

    private void sendCombat(CommandSender sender) {
        topicHeader(sender, "Combat & PvP");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Le PvP est un élément clé du serveur :</gray>"));
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gold>Combat Tag</gold>"));
        sender.sendMessage(Msg.mm("<gray>Si vous êtes frappé par un joueur, vous êtes <red>taggé</red> pendant quelques secondes.</gray>"));
        sender.sendMessage(Msg.mm("<gray>Vous ne pouvez pas vous déconnecter ou vous téléporter pendant ce temps.</gray>"));
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gold>Primes (Bounty)</gold>"));
        cmdLine(sender, "/bounty set <joueur> <montant>", "Placez une prime sur un joueur.");
        cmdLine(sender, "/bounty list", "Consultez les primes actives.");
        cmdLine(sender, "/bounty check <joueur>", "Vérifiez la prime sur un joueur.");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gold>Duels</gold>"));
        cmdLine(sender, "/duel queue [arène]", "Rejoignez la file d'attente pour un duel classé.");
        cmdLine(sender, "/duel stats", "Consultez vos statistiques de duel et votre ELO.");
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm(""));
    }

    private void sendRtp(CommandSender sender) {
        topicHeader(sender, "Téléportation aléatoire");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Le RTP vous téléporte à un endroit aléatoire du monde :</gray>"));
        sender.sendMessage(Msg.mm(""));
        cmdLine(sender, "/rtp", "Téléportation aléatoire dans le monde principal.");
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Le RTP fonctionne dans différentes dimensions :</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Overworld</aqua> — monde normal (par défaut)</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>Nether</aqua> — monde du Nether</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <aqua>End</aqua> — monde de l'End</gray>"));
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm("<gray>Un <aqua>cooldown</aqua> est appliqué entre chaque utilisation pour éviter les abus.</gray>"));
        sender.sendMessage(Msg.mm(SEP));
        sender.sendMessage(Msg.mm(""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (String topic : TOPICS.keySet()) {
                if (topic.startsWith(pref)) suggestions.add(topic);
                for (String a : TOPICS.get(topic)) {
                    if (a.startsWith(pref) && !a.matches("\\d+")) suggestions.add(a);
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
