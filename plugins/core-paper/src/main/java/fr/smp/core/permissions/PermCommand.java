package fr.smp.core.permissions;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PermCommand implements CommandExecutor, TabCompleter {

    private final SMPCore plugin;

    public PermCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("smp.perm.manage")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        PermissionsManager pm = plugin.permissions();
        if (pm == null) {
            sender.sendMessage(Msg.err("Permissions offline."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        try {
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    pm.reload();
                    sender.sendMessage(Msg.ok("Permissions rechargées localement."));
                }
                case "list" -> {
                    sender.sendMessage(Msg.info("<gold>Groups:</gold>"));
                    for (PermissionsManager.Group g : pm.groups()) {
                        sender.sendMessage(Msg.mm(" <yellow>- " + g.name + "</yellow> <gray>(weight " + g.weight + (g.admin ? ", admin" : "") + ")</gray>"));
                        if (!g.prefix.isEmpty()) sender.sendMessage(Msg.mm("   prefix: " + g.prefix));
                        if (!g.parents.isEmpty()) sender.sendMessage(Msg.mm("   parents: <white>" + String.join(", ", g.parents) + "</white>"));
                        if (!g.permissions.isEmpty()) sender.sendMessage(Msg.mm("   perms: <white>" + String.join(", ", g.permissions) + "</white>"));
                    }
                }
                case "group" -> handleGroup(sender, args);
                case "user" -> handleUser(sender, args);
                default -> sendHelp(sender);
            }
        } catch (Exception e) {
            sender.sendMessage(Msg.err("Erreur: " + e.getMessage()));
        }
        return true;
    }

    private void handleGroup(CommandSender sender, String[] args) {
        PermissionsManager pm = plugin.permissions();
        if (args.length < 2) { sendHelp(sender); return; }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (args.length < 3) { sender.sendMessage(Msg.err("/perm group create <nom>")); return; }
                boolean ok = pm.createGroup(args[2]);
                sender.sendMessage(ok ? Msg.ok("Groupe créé.") : Msg.err("Échec (existe déjà?)."));
            }
            case "delete" -> {
                if (args.length < 3) { sender.sendMessage(Msg.err("/perm group delete <nom>")); return; }
                boolean ok = pm.deleteGroup(args[2]);
                sender.sendMessage(ok ? Msg.ok("Groupe supprimé.") : Msg.err("Échec."));
            }
            case "addperm" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/perm group addperm <groupe> <perm>")); return; }
                boolean ok = pm.addGroupPerm(args[2], args[3]);
                sender.sendMessage(ok ? Msg.ok("Perm ajoutée.") : Msg.err("Échec."));
            }
            case "delperm" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/perm group delperm <groupe> <perm>")); return; }
                boolean ok = pm.delGroupPerm(args[2], args[3]);
                sender.sendMessage(ok ? Msg.ok("Perm retirée.") : Msg.err("Échec."));
            }
            case "setprefix" -> {
                if (args.length < 3) { sender.sendMessage(Msg.err("/perm group setprefix <groupe> <prefix MiniMessage>")); return; }
                String prefix = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
                boolean ok = pm.setGroupPrefix(args[2], prefix);
                sender.sendMessage(ok ? Msg.ok("Prefix mis à jour.") : Msg.err("Échec."));
            }
            case "addparent" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/perm group addparent <groupe> <parent>")); return; }
                boolean ok = pm.addGroupParent(args[2], args[3]);
                sender.sendMessage(ok ? Msg.ok("Parent ajouté.") : Msg.err("Échec."));
            }
            case "delparent" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/perm group delparent <groupe> <parent>")); return; }
                boolean ok = pm.delGroupParent(args[2], args[3]);
                sender.sendMessage(ok ? Msg.ok("Parent retiré.") : Msg.err("Échec."));
            }
            default -> sendHelp(sender);
        }
    }

    private void handleUser(CommandSender sender, String[] args) {
        PermissionsManager pm = plugin.permissions();
        if (args.length < 3) { sendHelp(sender); return; }
        String sub = args[1].toLowerCase();
        String target = args[2];
        UUID uuid = plugin.players().resolveUuid(target);
        if (uuid == null) {
            sender.sendMessage(Msg.err("Joueur inconnu: " + target));
            return;
        }
        switch (sub) {
            case "setgroup" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/perm user setgroup <joueur> <groupe>")); return; }
                boolean ok = pm.setUserGroup(uuid, target, args[3]);
                sender.sendMessage(ok ? Msg.ok("Groupe modifié.") : Msg.err("Échec (groupe introuvable)."));
            }
            case "addperm" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/perm user addperm <joueur> <perm>")); return; }
                boolean ok = pm.addUserPerm(uuid, target, args[3]);
                sender.sendMessage(ok ? Msg.ok("Perm ajoutée.") : Msg.err("Échec."));
            }
            case "delperm" -> {
                if (args.length < 4) { sender.sendMessage(Msg.err("/perm user delperm <joueur> <perm>")); return; }
                boolean ok = pm.delUserPerm(uuid, args[3]);
                sender.sendMessage(ok ? Msg.ok("Perm retirée.") : Msg.err("Échec."));
            }
            case "info" -> {
                PermissionsManager.User u = pm.getOrCreateUser(uuid, target);
                sender.sendMessage(Msg.info("<gold>" + u.name + "</gold> <gray>→ " + u.primaryGroup + "</gray>"));
                if (!u.permissions.isEmpty())
                    sender.sendMessage(Msg.mm("  perms: <white>" + String.join(", ", u.permissions) + "</white>"));
            }
            default -> sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Msg.mm("<gold>/perm reload</gold>"));
        sender.sendMessage(Msg.mm("<gold>/perm list</gold>"));
        sender.sendMessage(Msg.mm("<gold>/perm group</gold> <gray>create|delete|addperm|delperm|setprefix|addparent|delparent</gray>"));
        sender.sendMessage(Msg.mm("<gold>/perm user</gold> <gray>setgroup|addperm|delperm|info</gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.perm.manage")) return List.of();
        PermissionsManager pm = plugin.permissions();
        if (pm == null) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("reload", "list", "group", "user"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
            out.addAll(List.of("create", "delete", "addperm", "delperm", "setprefix", "addparent", "delparent"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("user")) {
            out.addAll(List.of("setgroup", "addperm", "delperm", "info"));
        } else if (args[0].equalsIgnoreCase("group") && args.length == 3) {
            for (var g : pm.groups()) out.add(g.name);
        } else if (args[0].equalsIgnoreCase("user") && args.length == 3) {
            for (Player p : plugin.getServer().getOnlinePlayers()) out.add(p.getName());
        } else if (args[0].equalsIgnoreCase("user") && args[1].equalsIgnoreCase("setgroup") && args.length == 4) {
            for (var g : pm.groups()) out.add(g.name);
        } else if (args[0].equalsIgnoreCase("group") && (args[1].equalsIgnoreCase("addparent") || args[1].equalsIgnoreCase("delparent")) && args.length == 4) {
            for (var g : pm.groups()) out.add(g.name);
        }
        return out;
    }
}
