package fr.smp.core.auth;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Player-facing auth commands: /register, /login, /changepassword.
 *
 * All password operations are dispatched off-thread — PBKDF2 at 120k
 * iterations is ~100ms per run and would stutter the server tick if done
 * inline. Results are posted back to the main thread before touching any
 * Bukkit state.
 */
public final class AuthCommand implements CommandExecutor {

    public enum Mode { LOGIN, REGISTER, CHANGE }

    private final SMPCore plugin;
    private final AuthManager auth;
    private final Mode mode;

    public AuthCommand(SMPCore plugin, AuthManager auth, Mode mode) {
        this.plugin = plugin;
        this.auth = auth;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }
        switch (mode) {
            case LOGIN -> handleLogin(p, args);
            case REGISTER -> handleRegister(p, args);
            case CHANGE -> handleChange(p, args);
        }
        return true;
    }

    // ---------------------------------------------------------------- /login

    private void handleLogin(Player p, String[] args) {
        AuthSession s = auth.session(p.getUniqueId());
        if (s == null) return;

        if (s.isAuthenticated()) {
            p.sendMessage(AuthEffects.prefixed("<gray>Tu es déjà connecté.</gray>"));
            return;
        }
        if (args.length != 1) {
            p.sendMessage(AuthEffects.prefixed("<red>Usage :</red> <white>/login <mdp></white>"));
            return;
        }

        String password = args[0];
        String nameLower = p.getName().toLowerCase();
        String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : null;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount acc = auth.loadBlocking(nameLower);
            if (acc == null || acc.passwordHash() == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    p.sendMessage(AuthEffects.prefixed("<red>Aucun mot de passe enregistré.</red> <gray>Tape</gray> <white>/register <mdp> <mdp></white>."));
                });
                return;
            }
            if (acc.mustRechange()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    p.sendMessage(AuthEffects.prefixed("<yellow>Un admin a réinitialisé ton mot de passe.</yellow> <gray>Utilise</gray> <white>/register <mdp> <mdp></white>."));
                });
                return;
            }
            boolean ok = PasswordHasher.verify(password, acc.passwordHash());
            long now = System.currentTimeMillis();
            if (!ok) {
                int total = acc.failedAttempts() + 1;
                acc.setFailedAttempts(total);
                if (total >= AuthManager.MAX_FAILED_ATTEMPTS) {
                    acc.setLockedUntil(now + AuthManager.LOCKOUT_MS);
                    acc.setFailedAttempts(0);
                }
                auth.saveAsync(acc);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    s.incrementFailed();
                    plugin.logs().log(LogCategory.JOIN, p, "auth_fail session=" + s.failedThisSession() + " total=" + total);
                    if (s.failedThisSession() >= AuthManager.SESSION_FAIL_KICK || acc.isLocked(now + 1)) {
                        p.kick(AuthEffects.kickComponent(
                                "<red>Mot de passe incorrect.</red>\n" +
                                "<gray>Compte verrouillé " + (AuthManager.LOCKOUT_MS / 60_000) + " min.</gray>"));
                    } else {
                        p.sendMessage(AuthEffects.prefixed("<red>Mot de passe incorrect.</red>"));
                    }
                });
                return;
            }
            // Success — rehash if policy changed.
            if (PasswordHasher.needsRehash(acc.passwordHash())) {
                acc.setPasswordHash(PasswordHasher.hash(password));
            }
            acc.setCrackedUuid(p.getUniqueId());
            acc.setLastLogin(now / 1000);
            acc.setLastIp(ip);
            acc.setFailedAttempts(0);
            acc.setLockedUntil(0);
            auth.saveAsync(acc);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                auth.markAuthenticated(p, acc);
                p.sendMessage(AuthEffects.prefixed("<green>Connecté.</green> <gray>Bon jeu.</gray>"));
            });
        });
    }

    // ---------------------------------------------------------------- /register

    private void handleRegister(Player p, String[] args) {
        AuthSession s = auth.session(p.getUniqueId());
        if (s == null) return;

        if (s.isAuthenticated()) {
            // Only allow re-register if the account is flagged must_rechange.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                AuthAccount acc = auth.loadBlocking(p.getName().toLowerCase());
                if (acc == null || !acc.mustRechange()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            p.sendMessage(AuthEffects.prefixed("<gray>Tu es déjà connecté.</gray> <white>/changepassword</white>")));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> doRegister(p, args, acc));
            });
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount acc = auth.loadBlocking(p.getName().toLowerCase());
            Bukkit.getScheduler().runTask(plugin, () -> doRegister(p, args, acc));
        });
    }

    private void doRegister(Player p, String[] args, AuthAccount existing) {
        if (!p.isOnline()) return;
        if (args.length != 2) {
            p.sendMessage(AuthEffects.prefixed("<red>Usage :</red> <white>/register <mdp> <mdp></white>"));
            return;
        }
        String pw = args[0];
        String confirm = args[1];
        if (!pw.equals(confirm)) {
            p.sendMessage(AuthEffects.prefixed("<red>Les deux mots de passe ne correspondent pas.</red>"));
            return;
        }
        String err = AuthManager.validatePassword(pw);
        if (err != null) {
            p.sendMessage(AuthEffects.prefixed("<red>" + err + "</red>"));
            return;
        }
        // Prevent plain /register if the account is already set and no rechange flag.
        if (existing != null && existing.passwordHash() != null && !existing.mustRechange()) {
            p.sendMessage(AuthEffects.prefixed("<red>Ce compte a déjà un mot de passe.</red> <gray>Utilise</gray> <white>/login <mdp></white>."));
            return;
        }
        // Prevent registering on a premium-owned name.
        if (existing != null && existing.premiumUuid() != null && existing.passwordHash() == null) {
            p.sendMessage(AuthEffects.prefixed("<red>Ce pseudo appartient à un compte premium.</red>"));
            return;
        }

        String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : null;
        long now = System.currentTimeMillis();
        long nowSec = now / 1000;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String hash = PasswordHasher.hash(pw);
            AuthAccount a = existing != null ? existing : new AuthAccount(
                    p.getName().toLowerCase(), null, null, p.getUniqueId(), nowSec, nowSec, ip, 0, 0, false);
            a.setPasswordHash(hash);
            a.setCrackedUuid(p.getUniqueId());
            a.setLastLogin(nowSec);
            a.setLastIp(ip);
            a.setFailedAttempts(0);
            a.setLockedUntil(0);
            a.setMustRechange(false);
            try {
                auth.saveAsync(a).join();
            } catch (Throwable t) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) p.sendMessage(AuthEffects.prefixed("<red>Erreur interne lors de l'enregistrement.</red>"));
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                auth.markAuthenticated(p, a);
                p.sendMessage(AuthEffects.prefixed("<green>Compte créé.</green> <gray>Retiens bien ton mot de passe.</gray>"));
                plugin.logs().log(LogCategory.JOIN, p, "auth_register password=" + PasswordHasher.redact(pw));
            });
        });
    }

    // ---------------------------------------------------------------- /changepassword

    private void handleChange(Player p, String[] args) {
        AuthSession s = auth.session(p.getUniqueId());
        if (s == null || !s.isAuthenticated()) {
            p.sendMessage(AuthEffects.prefixed("<red>Tu dois être connecté pour changer ton mot de passe.</red>"));
            return;
        }
        if (args.length != 2) {
            p.sendMessage(AuthEffects.prefixed("<red>Usage :</red> <white>/changepassword <ancien> <nouveau></white>"));
            return;
        }
        String oldPw = args[0];
        String newPw = args[1];
        String err = AuthManager.validatePassword(newPw);
        if (err != null) {
            p.sendMessage(AuthEffects.prefixed("<red>" + err + "</red>"));
            return;
        }
        if (oldPw.equals(newPw)) {
            p.sendMessage(AuthEffects.prefixed("<red>Le nouveau mot de passe doit différer de l'ancien.</red>"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount acc = auth.loadBlocking(p.getName().toLowerCase());
            if (acc == null || acc.passwordHash() == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(AuthEffects.prefixed("<red>Aucun mot de passe à changer.</red>")));
                return;
            }
            if (!PasswordHasher.verify(oldPw, acc.passwordHash())) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(AuthEffects.prefixed("<red>Ancien mot de passe incorrect.</red>")));
                return;
            }
            acc.setPasswordHash(PasswordHasher.hash(newPw));
            acc.setLastLogin(System.currentTimeMillis() / 1000);
            auth.saveAsync(acc);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendMessage(AuthEffects.prefixed("<green>Mot de passe modifié.</green>"));
                plugin.logs().log(LogCategory.JOIN, p, "auth_pwd_change");
            });
        });
    }
}
