package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.gui.BaltopGUI;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EconomyCommand implements CommandExecutor {

    private final SMPCore plugin;
    private final String mode; // "bal", "pay", "shards", "eco"

    public EconomyCommand(SMPCore plugin, String mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (mode) {
            case "bal" -> {
                if (!(sender instanceof Player p)) return true;
                UUID target = args.length > 0 ? plugin.players().resolveUuid(args[0]) : p.getUniqueId();
                if (target == null) { p.sendMessage(Msg.err("Joueur inconnu.")); return true; }
                PlayerData d = plugin.players().loadOffline(target);
                if (d == null) { p.sendMessage(Msg.err("Aucune donnée.")); return true; }
                p.sendMessage(Msg.info("<green>$" + Msg.money(d.money()) + "</green> <gray>pour</gray> <aqua>" + d.name() + "</aqua>"));
            }
            case "pay" -> {
                if (!(sender instanceof Player p)) return true;
                if (plugin.cooldowns() != null && plugin.cooldowns().isOnCooldown(p, "pay")) {
                    p.sendMessage(Msg.err("Cooldown: <white>" + Msg.duration(plugin.cooldowns().remaining(p, "pay")) + "</white>"));
                    return true;
                }
                if (args.length < 2) { p.sendMessage(Msg.err("/pay <joueur> <montant>")); return true; }
                if (args[0].equalsIgnoreCase(p.getName())) {
                    p.sendMessage(Msg.err("Tu ne peux pas te payer toi-même.")); return true;
                }
                Player localTarget = Bukkit.getPlayerExact(args[0]);
                UUID targetUuid;
                String targetName;
                if (localTarget != null) {
                    targetUuid = localTarget.getUniqueId();
                    targetName = localTarget.getName();
                } else {
                    var rosterEntry = plugin.roster() != null ? plugin.roster().get(args[0]) : null;
                    if (rosterEntry != null) {
                        targetName = rosterEntry.name();
                        targetUuid = plugin.players().resolveUuid(targetName);
                    } else {
                        targetName = args[0];
                        targetUuid = plugin.players().resolveUuid(args[0]);
                    }
                    if (targetUuid == null) {
                        p.sendMessage(Msg.err("<red>Ce joueur n'est jamais venu sur le serveur.</red>"));
                        return true;
                    }
                }
                double amount = Msg.parseAmount(args[1]);
                if (amount <= 0) {
                    p.sendMessage(Msg.err("Montant invalide. Ex: <white>67</white>, <white>67k</white>, <white>67m</white>, <white>1.5b</white>.")); return true;
                }
                if (!plugin.economy().has(p.getUniqueId(), amount)) {
                    p.sendMessage(Msg.err("<red>Fonds insuffisants.</red> <gray>Tu as $" +
                            Msg.money(plugin.players().get(p).money()) + ".</gray>"));
                    return true;
                }
                if (!plugin.economy().transfer(p.getUniqueId(), targetUuid, amount)) {
                    p.sendMessage(Msg.err("Transfert échoué.")); return true;
                }
                if (plugin.cooldowns() != null) plugin.cooldowns().set(p, "pay");
                p.sendMessage(Msg.ok("<green>Envoyé $" + Msg.money(amount) + " à " + targetName + ".</green>"));
                if (localTarget != null) {
                    localTarget.sendMessage(Msg.info("<green>$" + Msg.money(amount) +
                            "</green> <gray>reçu de</gray> <aqua>" + p.getName() + "</aqua>"));
                } else {
                    final double amt = amount;
                    plugin.getMessageChannel().sendForward(targetName, "pay-notice", o -> {
                        o.writeUTF(p.getName());
                        o.writeDouble(amt);
                    });
                }
            }
            case "baltop" -> {
                if (!(sender instanceof Player p)) return true;
                new BaltopGUI(plugin).open(p);
            }
            case "shards" -> {
                if (!(sender instanceof Player p)) return true;
                PlayerData d = plugin.players().get(p);
                if (d == null) { p.sendMessage(Msg.err("Aucune donnée.")); return true; }
                p.sendMessage(Msg.info("<aqua>◆ Saphirs</aqua> <white>" + d.shards() + "</white>"));
            }
            case "eco" -> {
                if (!sender.hasPermission("smp.admin")) { sender.sendMessage(Msg.err("Permission refusée.")); return true; }
                if (args.length < 3) { sender.sendMessage(Msg.err("/eco give|take|set <joueur> <montant>")); return true; }
                String sub = args[0].toLowerCase();
                UUID u = plugin.players().resolveUuid(args[1]);
                if (u == null) { sender.sendMessage(Msg.err("Joueur inconnu.")); return true; }
                double v = Msg.parseAmount(args[2]);
                if (v < 0) { sender.sendMessage(Msg.err("Montant invalide. Ex: 67, 67k, 67m, 1.5b.")); return true; }
                if (!sub.equals("set") && v <= 0) { sender.sendMessage(Msg.err("Montant invalide. Ex: 67, 67k, 67m, 1.5b.")); return true; }
                PlayerData d = plugin.players().loadOffline(u);
                if (d == null) return true;
                switch (sub) {
                    case "give" -> plugin.economy().deposit(u, v, "admin.give");
                    case "take" -> plugin.economy().withdraw(u, v, "admin.take");
                    case "set" -> plugin.economy().setBalance(u, v);
                    default -> sender.sendMessage(Msg.err("give|take|set"));
                }
                plugin.players().save(d);
                sender.sendMessage(Msg.ok("<green>OK.</green>"));
            }
        }
        return true;
    }
}
