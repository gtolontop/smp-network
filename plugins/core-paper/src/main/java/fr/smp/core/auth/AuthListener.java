package fr.smp.core.auth;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Drives the join → freeze → /login → unfreeze flow and blocks every gameplay
 * action while the session is unauthenticated.
 *
 * Two paths trigger authentication completion:
 * 1. Join detects a premium-verified profile (textures property) — instant
 *    auto-auth, no password.
 * 2. Player runs /login or /register (handled by {@link AuthCommand}).
 *
 * Until then, this listener cancels move (with snap-back), command (allowlist),
 * chat, block break/place, interact, drop, pickup, inventory open/click,
 * damage in/out, food, mob targeting, and item swap.
 */
public final class AuthListener implements Listener {

    /** Commands the player IS allowed to run while unauthenticated. */
    private static final Set<String> ALLOWED = Set.of(
            "login", "l", "register", "reg", "registrer", "changepassword", "changepwd"
    );

    /** Names that are reserved (prevent edge-case username spoofing at the proxy). */
    private static final Set<String> BLOCKED_NAMES = Set.of(
            "console", "server", "rcon", "system", "admin"
    );

    private final SMPCore plugin;
    private final AuthManager auth;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public AuthListener(SMPCore plugin, AuthManager auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    // ---------------------------------------------------------------- pre-login validation

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (name == null || name.isBlank() || name.length() > 16
                || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    AuthEffects.kickComponent("<red>Pseudo invalide.</red>"));
            return;
        }
        if (BLOCKED_NAMES.contains(name.toLowerCase())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    AuthEffects.kickComponent("<red>Ce pseudo est réservé.</red>"));
            return;
        }
        // Reject if a different UUID is already online with this name (same name reconnect).
        // Paper's online-player map is concurrent, so this lookup is safe from async.
        Player existing = Bukkit.getPlayerExact(name);
        if (existing != null && !existing.getUniqueId().equals(event.getUniqueId())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    AuthEffects.kickComponent("<red>Ce pseudo est déjà connecté.</red>"));
            return;
        }
        // Check lockout from prior failed attempts.
        AuthAccount acc = auth.loadBlocking(name.toLowerCase());
        long now = System.currentTimeMillis();
        if (acc != null && acc.isLocked(now)) {
            long remaining = (acc.lockedUntil() - now) / 1000;
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    AuthEffects.kickComponent("<red>Trop de tentatives échouées.</red>\n" +
                            "<gray>Réessaie dans <white>" + remaining + "s</white>.</gray>"));
        }
    }

    // ---------------------------------------------------------------- join + auto-auth

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        boolean premium = AuthManager.detectPremium(p);
        AuthSession session = auth.beginSession(p, premium);

        // Pre-freeze: block movement immediately even before DB load resolves.
        AuthEffects.apply(p);

        String nameLower = p.getName().toLowerCase();
        String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : null;
        long now = System.currentTimeMillis();
        long nowSec = now / 1000;

        auth.loadAsync(nameLower, account -> {
            if (!p.isOnline()) return;
            // Cross-backend session relay (auth-validated from proxy) may have
            // completed while this DB load was in flight. If so, the player is
            // already authenticated and we must not run the cracked /login flow
            // (which would call markAwaiting and re-freeze them).
            if (auth.isAuthenticated(p)) return;

            // Premium connection — Mojang has authenticated them at the proxy.
            if (premium) {
                if (account == null) {
                    // First-ever join for this name; create premium-only record.
                    account = new AuthAccount(nameLower, null, p.getUniqueId(), null, nowSec, nowSec, ip, 0, 0, false);
                    auth.saveAsync(account);
                    p.sendMessage(AuthEffects.prefixed("<green>Compte premium détecté</green> <gray>— inscription automatique.</gray>"));
                } else {
                    // Premium ownership trumps any prior cracked password.
                    boolean changed = false;
                    if (account.passwordHash() != null) { account.setPasswordHash(null); changed = true; }
                    if (!p.getUniqueId().equals(account.premiumUuid())) {
                        account.setPremiumUuid(p.getUniqueId()); changed = true;
                    }
                    account.setLastLogin(nowSec);
                    account.setLastIp(ip);
                    account.setFailedAttempts(0);
                    account.setLockedUntil(0);
                    account.setMustRechange(false);
                    auth.saveAsync(account);
                    if (changed) {
                        p.sendMessage(AuthEffects.prefixed("<green>Compte premium vérifié</green> <gray>— ancien mot de passe effacé.</gray>"));
                    } else {
                        p.sendMessage(AuthEffects.prefixed("<green>Bon retour</green> <gray>— compte premium vérifié.</gray>"));
                    }
                }
                auth.markAuthenticated(p, account);
                plugin.logs().log(LogCategory.JOIN, p, "auth_premium_auto");
                return;
            }

            // Cracked connection.
            if (account != null && account.premiumUuid() != null && account.passwordHash() == null) {
                // Account is premium-only; cracked client cannot use this name.
                p.kick(AuthEffects.kickComponent(
                        "<red>Ce pseudo appartient à un compte premium.</red>\n" +
                        "<gray>Connecte-toi avec un client Minecraft légitime.</gray>"));
                plugin.logs().log(LogCategory.JOIN, p, "auth_block_premium_name");
                return;
            }

            auth.markAwaiting(p);
            if (account == null || account.passwordHash() == null || account.mustRechange()) {
                p.sendMessage(mm.deserialize(
                        "<gradient:#a8edea:#fed6e3><bold>Bienvenue sur SMP</bold></gradient>\n" +
                        "<gray>Crée ton mot de passe :</gray> <white>/register <mdp> <mdp></white>\n" +
                        "<dark_gray>(min " + AuthManager.MIN_PASSWORD_LEN + " caractères, ne donne ton mdp à personne)</dark_gray>"));
                if (account != null && account.mustRechange()) {
                    p.sendMessage(AuthEffects.prefixed(
                            "<yellow>Un admin a réinitialisé ton mot de passe.</yellow> <gray>Choisis-en un nouveau.</gray>"));
                }
                plugin.logs().log(LogCategory.JOIN, p, "auth_await_register");
            } else {
                p.sendMessage(mm.deserialize(
                        "<gradient:#fde68a:#fb923c><bold>Connexion requise</bold></gradient>\n" +
                        "<gray>Tape</gray> <white>/login <mdp></white> <gray>pour jouer.</gray>"));
                plugin.logs().log(LogCategory.JOIN, p, "auth_await_login");
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        auth.endSession(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- gameplay block-list

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (auth.isAuthenticated(event.getPlayer())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Allow head turn (yaw/pitch) but not displacement.
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        event.setTo(from);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (auth.isAuthenticated(event.getPlayer())) return;
        // Allow our own freeze-snap teleports (PLUGIN cause is what we use) but
        // block plugin/cmd teleports that would carry the player away.
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (auth.isAuthenticated(event.getPlayer())) return;
        String msg = event.getMessage();
        if (msg.isEmpty() || msg.charAt(0) != '/') {
            event.setCancelled(true);
            return;
        }
        int sp = msg.indexOf(' ');
        String cmd = (sp == -1 ? msg.substring(1) : msg.substring(1, sp)).toLowerCase();
        // Strip namespace prefix if present (minecraft:login, smpcore:login, ...)
        int colon = cmd.indexOf(':');
        if (colon != -1) cmd = cmd.substring(colon + 1);
        if (!ALLOWED.contains(cmd)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(mm.deserialize(
                    "<red>Tu dois te connecter avant.</red> <gray>/login <mdp></gray>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(AuthEffects.prefixed(
                    "<red>Tu ne peux pas parler avant de t'authentifier.</red>"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (auth.isUnauthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && auth.isUnauthenticated(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && auth.isUnauthenticated(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && auth.isUnauthenticated(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && auth.isUnauthenticated(p)) event.setCancelled(true);
        if (event.getEntity() instanceof Player p && auth.isUnauthenticated(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p && auth.isUnauthenticated(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && auth.isUnauthenticated(p)) event.setCancelled(true);
    }

    @SuppressWarnings("unused")
    private void unused(UUID id) { /* keep import */ }
}
