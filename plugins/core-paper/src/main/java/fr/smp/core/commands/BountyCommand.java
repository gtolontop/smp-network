package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.BountyGUI;
import fr.smp.core.managers.BountyManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class BountyCommand implements CommandExecutor {

    private final SMPCore plugin;

    public BountyCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Joueurs uniquement.");
                return true;
            }
            new BountyGUI(plugin).open(p, 0);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list", "top" -> handleList(sender);
            case "check", "info" -> handleCheck(sender, args);
            case "set", "add", "place" -> handleSet(sender, args);
            case "remove", "cancel" -> handleRemove(sender, args);
            default -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Joueurs uniquement.");
                    return true;
                }
                new BountyGUI(plugin).open(p, 0);
            }
        }
        return true;
    }

    private void handleList(CommandSender sender) {
        List<BountyManager.Bounty> top = plugin.bounties().top(10);
        if (top.isEmpty()) {
            sender.sendMessage(Msg.info("<gray>Aucune bounty active. Utilise <yellow>/bounty</yellow> pour ouvrir le menu.</gray>"));
            return;
        }
        sender.sendMessage(Msg.mm("<gradient:#f85032:#e73827><bold>\u2620 Top bounties</bold></gradient>"));
        int rank = 1;
        for (BountyManager.Bounty b : top) {
            String rankColor = switch (rank) {
                case 1 -> "<gold>";
                case 2 -> "<gray>";
                case 3 -> "<#cd7f32>";
                default -> "<white>";
            };
            sender.sendMessage(Msg.mm(" " + rankColor + "#" + rank + "</>" +
                    " <white>" + b.targetName() + "</white> <dark_gray>→</dark_gray> <gold>$" +
                    Msg.money(b.amount()) + "</gold> <dark_gray>(" + b.contributors() + " contrib.)</dark_gray>"));
            rank++;
        }
    }

    private void handleCheck(CommandSender sender, String[] args) {
        String name;
        if (args.length < 2) {
            if (sender instanceof Player p) name = p.getName();
            else { sender.sendMessage(Msg.err("/bounty check <joueur>")); return; }
        } else {
            name = args[1];
        }
        UUID uuid = plugin.players().resolveUuid(name);
        if (uuid == null) { sender.sendMessage(Msg.err("Joueur introuvable.")); return; }
        BountyManager.Bounty b = plugin.bounties().get(uuid);
        if (b == null || b.amount() <= 0) {
            sender.sendMessage(Msg.info("<gray>Aucune prime sur <white>" + name + "</white>.</gray>"));
            return;
        }
        sender.sendMessage(Msg.info("<red>Prime sur <white>" + b.targetName() + "</white> : <gold>$" +
                Msg.money(b.amount()) + "</gold> <dark_gray>(" + b.contributors() + " contrib.)</dark_gray>"));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (args.length < 3) {
            p.sendMessage(Msg.err("/bounty set <joueur> <montant>"));
            return;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(p.getName())) {
            p.sendMessage(Msg.err("Tu ne peux pas te mettre une prime à toi-même."));
            return;
        }
        double amount = Msg.parseAmount(args[2]);
        double min = plugin.bounties().minAmount();
        double max = plugin.bounties().maxAmount();
        if (amount <= 0) { p.sendMessage(Msg.err("Montant invalide.")); return; }
        if (amount < min) { p.sendMessage(Msg.err("Minimum <yellow>$" + Msg.money(min) + "</yellow>.")); return; }
        if (amount > max) { p.sendMessage(Msg.err("Maximum <yellow>$" + Msg.money(max) + "</yellow>.")); return; }

        UUID target = plugin.players().resolveUuid(targetName);
        if (target == null) { p.sendMessage(Msg.err("Joueur introuvable.")); return; }
        String resolvedName = targetName;
        var pd = plugin.players().loadOffline(target);
        if (pd != null) resolvedName = pd.name();

        if (!plugin.economy().has(p.getUniqueId(), amount)) {
            p.sendMessage(Msg.err("Fonds insuffisants."));
            return;
        }
        if (!plugin.economy().withdraw(p.getUniqueId(), amount, "bounty on " + resolvedName)) {
            p.sendMessage(Msg.err("Échec du paiement."));
            return;
        }
        double total = plugin.bounties().add(target, resolvedName, p.getUniqueId(), p.getName(), amount);
        if (total < 0) {
            plugin.economy().deposit(p.getUniqueId(), amount, "bounty refund");
            p.sendMessage(Msg.err("Erreur interne, montant remboursé."));
            return;
        }
        p.sendMessage(Msg.ok("<green>Prime de <yellow>$" + Msg.money(amount) +
                "</yellow> ajoutée sur <white>" + resolvedName + "</white>. Total: <gold>$" +
                Msg.money(total) + "</gold>.</green>"));
        Bukkit.broadcast(Msg.mm("<dark_red>\u2620 <red>Une prime de <gold>$" + Msg.money(total) +
                "</gold> pèse sur la tête de <white>" + resolvedName + "</white> !</red>"));
        Player targetOnline = Bukkit.getPlayerExact(resolvedName);
        if (targetOnline != null) {
            targetOnline.sendMessage(Msg.err("<red>Une prime de <gold>$" + Msg.money(amount) +
                    "</gold> vient d'être posée sur toi par <aqua>" + p.getName() + "</aqua>.</red>"));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return;
        }
        if (args.length < 2) { sender.sendMessage(Msg.err("/bounty remove <joueur>")); return; }
        UUID target = plugin.players().resolveUuid(args[1]);
        if (target == null) { sender.sendMessage(Msg.err("Joueur introuvable.")); return; }
        BountyManager.Bounty b = plugin.bounties().get(target);
        if (b == null) { sender.sendMessage(Msg.err("Pas de prime sur ce joueur.")); return; }
        plugin.bounties().remove(target);
        sender.sendMessage(Msg.ok("<green>Prime retirée (<gold>$" + Msg.money(b.amount()) + "</gold>).</green>"));
    }
}
