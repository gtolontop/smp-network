package fr.smp.core.auth;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Admin auth commands. Bound to /auth.
 *
 * Subcommands:
 *   reset   <player>   — clear password and force re-register on next join
 *   unlock  <player>   — clear lockout / failed-attempt counters
 *   info    <player>   — print account metadata (registered, last-login, IP, premium)
 *   delete  <player>   — permanently remove the account row
 *   kick    <player>   — kick the player so they have to re-authenticate
 */
public final class AuthAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reset", "unlock", "info", "delete", "kick");

    private final SMPCore plugin;
    private final AuthManager auth;

    public AuthAdminCommand(SMPCore plugin, AuthManager auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(AuthEffects.prefixed("<red>Permission refusée.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(AuthEffects.prefixed(
                    "<gray>Usage :</gray> <white>/auth reset|unlock|info|delete|kick <joueur></white>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String target = args[1];
        String targetLower = target.toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reset" -> handleReset(sender, targetLower, target);
            case "unlock" -> handleUnlock(sender, targetLower, target);
            case "info" -> handleInfo(sender, targetLower, target);
            case "delete" -> handleDelete(sender, targetLower, target);
            case "kick" -> handleKick(sender, target);
            default -> sender.sendMessage(AuthEffects.prefixed(
                    "<red>Sous-commande inconnue.</red> <gray>reset/unlock/info/delete/kick</gray>"));
        }
        return true;
    }

    private void handleReset(CommandSender sender, String targetLower, String displayName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount acc = auth.loadBlocking(targetLower);
            if (acc == null) {
                reply(sender, "<red>Aucun compte pour</red> <white>" + displayName + "</white>.");
                return;
            }
            acc.setPasswordHash(null);
            acc.setMustRechange(true);
            acc.setFailedAttempts(0);
            acc.setLockedUntil(0);
            auth.saveAsync(acc).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) {
                    reply(sender, "<red>Erreur DB :</red> " + err.getMessage());
                    return;
                }
                reply(sender, "<green>Mot de passe réinitialisé pour</green> <white>" + displayName + "</white>.");
                // Kick online player so they're forced to re-register.
                Player online = Bukkit.getPlayerExact(displayName);
                if (online != null) {
                    online.kick(AuthEffects.kickComponent(
                            "<yellow>Ton mot de passe a été réinitialisé par un admin.</yellow>\n" +
                            "<gray>Reconnecte-toi puis tape</gray> <white>/register <mdp> <mdp></white>."));
                }
                plugin.logs().log(LogCategory.JOIN,
                        sender instanceof Player pl ? pl : null,
                        "auth_admin_reset target=" + displayName);
            }));
        });
    }

    private void handleUnlock(CommandSender sender, String targetLower, String displayName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount acc = auth.loadBlocking(targetLower);
            if (acc == null) {
                reply(sender, "<red>Aucun compte pour</red> <white>" + displayName + "</white>.");
                return;
            }
            acc.setFailedAttempts(0);
            acc.setLockedUntil(0);
            auth.saveAsync(acc).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) {
                    reply(sender, "<red>Erreur DB :</red> " + err.getMessage());
                    return;
                }
                reply(sender, "<green>Déverrouillé</green> <white>" + displayName + "</white>.");
            }));
        });
    }

    private void handleInfo(CommandSender sender, String targetLower, String displayName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount acc = auth.loadBlocking(targetLower);
            if (acc == null) {
                reply(sender, "<red>Aucun compte pour</red> <white>" + displayName + "</white>.");
                return;
            }
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String registered = acc.registeredAt() > 0 ? fmt.format(new Date(acc.registeredAt() * 1000L)) : "-";
            String lastLogin = acc.lastLogin() > 0 ? fmt.format(new Date(acc.lastLogin() * 1000L)) : "-";
            String premium = acc.premiumUuid() != null ? "<green>oui</green>" : "<gray>non</gray>";
            String password = acc.hasPassword() ? "<green>défini</green>" : "<gray>aucun</gray>";
            String locked = acc.lockedUntil() > System.currentTimeMillis()
                    ? "<red>verrouillé " + ((acc.lockedUntil() - System.currentTimeMillis()) / 1000) + "s</red>"
                    : "<gray>non</gray>";
            String rechange = acc.mustRechange() ? "<yellow>reset en attente</yellow>" : "<gray>non</gray>";
            String ip = acc.lastIp() != null ? acc.lastIp() : "-";
            reply(sender,
                    "<gradient:#a8edea:#fed6e3><bold>" + displayName + "</bold></gradient>\n" +
                    "<gray>Inscrit :</gray> <white>" + registered + "</white>\n" +
                    "<gray>Dernier login :</gray> <white>" + lastLogin + "</white>\n" +
                    "<gray>Dernière IP :</gray> <white>" + ip + "</white>\n" +
                    "<gray>Premium :</gray> " + premium + " <gray>• MDP :</gray> " + password + "\n" +
                    "<gray>Tentatives :</gray> <white>" + acc.failedAttempts() + "</white> " +
                    "<gray>• verrou :</gray> " + locked + "\n" +
                    "<gray>Reset admin :</gray> " + rechange);
        });
    }

    private void handleDelete(CommandSender sender, String targetLower, String displayName) {
        auth.deleteAsync(targetLower, () -> {
            reply(sender, "<green>Compte supprimé pour</green> <white>" + displayName + "</white>.");
            Player online = Bukkit.getPlayerExact(displayName);
            if (online != null) {
                online.kick(AuthEffects.kickComponent(
                        "<yellow>Ton compte a été supprimé par un admin.</yellow>\n<gray>Reconnecte-toi pour en créer un nouveau.</gray>"));
            }
            plugin.logs().log(LogCategory.JOIN,
                    sender instanceof Player pl ? pl : null,
                    "auth_admin_delete target=" + displayName);
        });
    }

    private void handleKick(CommandSender sender, String displayName) {
        Player online = Bukkit.getPlayerExact(displayName);
        if (online == null) {
            reply(sender, "<red>Joueur hors ligne.</red>");
            return;
        }
        online.kick(AuthEffects.kickComponent("<yellow>Déconnexion demandée par un admin.</yellow>"));
        reply(sender, "<green>Kické</green> <white>" + displayName + "</white>.");
    }

    private void reply(CommandSender sender, String mini) {
        sender.sendMessage(AuthEffects.prefixed(mini));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUBS) if (s.startsWith(pref)) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String pref = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pref)) out.add(p.getName());
            }
            return out;
        }
        return List.of();
    }
}
