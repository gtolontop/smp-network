package fr.smp.anticheat.command;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.movement.MovementProfile;
import fr.smp.anticheat.xray.XrayProfile;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Admin / test commands for the AntiCheat plugin.
 *
 * {@code /ac}                        status summary
 * {@code /ac reload}                 reload config.yml
 * {@code /ac debug}                  toggle verbose logging
 * {@code /ac me}                     dump active profile for the caller
 * {@code /ac scan [radius]}          scan caller's surroundings, list hidden blocks + mask state
 * {@code /ac ismasked}               report whether the block the caller is looking at is masked
 * {@code /ac reveal}                 force reveal every masked block around the caller (test only)
 * {@code /ac remask}                 clear our per-player mask state so the next chunk refresh rebuilds it
 * {@code /ac bypass [on|off|player]} toggle AntiCheat bypass for self (or set explicit state / for another player)
 * {@code /ac highlight [r] [secs]}   particle-highlight hidden blocks near caller (red = masked, green = visible)
 * {@code /ac simulate <player>}      dump effective xray/movement profile for another player
 */
public final class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "reload", "debug", "me", "scan", "ismasked", "reveal", "remask",
            "bypass", "highlight", "simulate");

    private final AntiCheatPlugin plugin;
    // Running highlight tasks per caller, so re-invoking the command cancels
    // the prior loop instead of stacking them.
    private final ConcurrentMap<UUID, BukkitTask> highlightTasks = new ConcurrentHashMap<>();

    public AntiCheatCommand(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7AntiCheat §8| §fStatus");
            sender.sendMessage("§8- §7xray=" + plugin.acConfig().xrayEnabled());
            sender.sendMessage("§8- §7containers=" + plugin.acConfig().containersEnabled());
            sender.sendMessage("§8- §7entityEsp=" + plugin.acConfig().entityEspEnabled());
            sender.sendMessage("§8- §7movement=" + plugin.acConfig().movementEnabled());
            sender.sendMessage("§8- §7debug=" + plugin.getConfig().getBoolean("debug", false));
            sender.sendMessage("§7Subs: §f" + String.join(", ", SUBS));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.acConfig().reload();
                // Clear every player's per-player mask state + resend nearby chunks
                // so the new profile's hidden-blocks / reveal-distance actually takes
                // effect immediately. Without this, chunks already sent stay masked
                // under the old profile until the player walks far enough for the
                // chunk to be re-sent — which is unintuitive and was the #1 reason
                // "reload didn't fix anything" reports came in.
                int resent = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    plugin.xray().clearPlayer(online.getUniqueId());
                    plugin.visibility().clear(online.getUniqueId());
                    resendNearbyChunks(online);
                    resent++;
                }
                sender.sendMessage("§aAntiCheat config reloaded §8(§7état nettoyé et "
                        + resent + " joueur(s) re-scannés§8)");
            }
            case "debug" -> {
                boolean current = plugin.getConfig().getBoolean("debug", false);
                plugin.getConfig().set("debug", !current);
                plugin.saveConfig();
                sender.sendMessage("§aAntiCheat debug logging: §f" + (!current));
            }
            case "me" -> dumpProfile(sender, requirePlayer(sender));
            case "scan" -> {
                Player p = requirePlayer(sender);
                if (p == null) return true;
                int radius = 6;
                if (args.length > 1) {
                    try { radius = Math.max(1, Math.min(32, Integer.parseInt(args[1]))); }
                    catch (NumberFormatException e) { sender.sendMessage("§cradius must be 1..32"); return true; }
                }
                scan(sender, p, radius);
            }
            case "ismasked" -> {
                Player p = requirePlayer(sender);
                if (p == null) return true;
                Block target = p.getTargetBlockExact(12);
                if (target == null) { sender.sendMessage("§cAucun bloc en vue (distance < 12)."); return true; }
                Location l = target.getLocation();
                boolean masked = plugin.xray().isMasked(p, l.getBlockX(), l.getBlockY(), l.getBlockZ());
                sender.sendMessage("§7Target §f" + target.getType() + " §7at §f" + l.getBlockX()
                        + "," + l.getBlockY() + "," + l.getBlockZ() + " §7— masked=§f" + masked);
            }
            case "reveal" -> {
                Player p = requirePlayer(sender);
                if (p == null) return true;
                int revealed = forceReveal(p, 24);
                sender.sendMessage("§aForced reveal sur §f" + revealed + " §7blocs autour de toi.");
            }
            case "remask" -> {
                Player p = requirePlayer(sender);
                if (p == null) return true;
                plugin.xray().clearPlayer(p.getUniqueId());
                sender.sendMessage("§aMask state cleared — le prochain chunk redémarre.");
            }
            case "bypass" -> handleBypass(sender, args);
            case "highlight" -> handleHighlight(sender, args);
            case "simulate" -> {
                if (args.length < 2) { sender.sendMessage("§c/ac simulate <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage("§cOffline."); return true; }
                dumpProfile(sender, target);
            }
            default -> sender.sendMessage("§cUnknown subcommand. §7Use: §f" + String.join(", ", SUBS));
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage("§cIn-game only.");
        return null;
    }

    private void handleBypass(CommandSender sender, String[] args) {
        // /ac bypass                → toggle self
        // /ac bypass on|off         → set self
        // /ac bypass <player>       → toggle other
        // /ac bypass <player> on|off → set other
        Player self = sender instanceof Player ? (Player) sender : null;
        Player target = self;
        Boolean explicit = null;

        if (args.length >= 2) {
            String a = args[1].toLowerCase();
            if (a.equals("on") || a.equals("off") || a.equals("true") || a.equals("false")) {
                explicit = a.equals("on") || a.equals("true");
            } else {
                Player other = Bukkit.getPlayerExact(args[1]);
                if (other == null) {
                    sender.sendMessage("§cJoueur offline ou état invalide (attendu: on/off ou nom de joueur).");
                    return;
                }
                target = other;
                if (args.length >= 3) {
                    String b = args[2].toLowerCase();
                    if (b.equals("on") || b.equals("true")) explicit = true;
                    else if (b.equals("off") || b.equals("false")) explicit = false;
                    else { sender.sendMessage("§cAttendu: on ou off."); return; }
                }
            }
        }

        if (target == null) { sender.sendMessage("§cIn-game only (ou fournis un pseudo)."); return; }

        boolean newState;
        if (explicit == null) {
            newState = plugin.bypass().toggle(target.getUniqueId());
        } else {
            plugin.bypass().setRuntime(target.getUniqueId(), explicit);
            newState = explicit;
        }

        // Force a re-sync: clearing mask state + re-sending close chunks. When we
        // flip OFF bypass we need the client to re-receive the masked versions, so
        // we clear the cache so the next chunk scan rebuilds it. When we flip ON
        // bypass we also clear so the currently-masked blocks get re-sent as real.
        plugin.xray().clearPlayer(target.getUniqueId());
        resendNearbyChunks(target);

        String who = target == sender ? "toi" : target.getName();
        sender.sendMessage("§aBypass AntiCheat pour §f" + who + "§a: §f" + newState
                + " §8(les chunks autour ont été rechargés)");
        if (target != sender) {
            target.sendMessage("§7AntiCheat bypass: §f" + newState + " §8(set par " + sender.getName() + ")");
        }
    }

    /**
     * Re-send the chunks within 4 chunks of the player so the outbound filter
     * re-runs with the new bypass state. Without this the player keeps seeing
     * whatever state was cached client-side until they move chunks.
     */
    private void resendNearbyChunks(Player p) {
        try {
            int pcx = p.getLocation().getBlockX() >> 4;
            int pcz = p.getLocation().getBlockZ() >> 4;
            int r = 4;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.getWorld().refreshChunk(pcx + dx, pcz + dz);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("resendNearbyChunks failed: " + t.getMessage());
        }
    }

    private void handleHighlight(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender);
        if (p == null) return;
        int radius = 12;
        int seconds = 15;
        if (args.length > 1) {
            try { radius = Math.max(1, Math.min(32, Integer.parseInt(args[1]))); }
            catch (NumberFormatException e) { sender.sendMessage("§cradius must be 1..32"); return; }
        }
        if (args.length > 2) {
            try { seconds = Math.max(1, Math.min(60, Integer.parseInt(args[2]))); }
            catch (NumberFormatException e) { sender.sendMessage("§cseconds must be 1..60"); return; }
        }

        // Cancel prior run for this caller.
        UUID id = p.getUniqueId();
        BukkitTask prev = highlightTasks.remove(id);
        if (prev != null) prev.cancel();

        XrayProfile profile = plugin.acConfig().xrayProfile(p.getWorld().getEnvironment());
        if (!profile.enabled) {
            sender.sendMessage("§7Xray disabled for this env — rien à highlight.");
            return;
        }

        final int rad = radius;
        final long endAt = System.currentTimeMillis() + (long) seconds * 1000L;
        // Red dust = block is in hidden list AND currently masked for the caller (client sees stone/netherrack).
        // Green dust = in hidden list but NOT masked (client sees the real block — probably exposed face).
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.3f);
        Particle.DustOptions greenDust = new Particle.DustOptions(Color.fromRGB(60, 255, 80), 1.3f);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || System.currentTimeMillis() > endAt) {
                BukkitTask t = highlightTasks.remove(id);
                if (t != null) t.cancel();
                return;
            }
            Location at = p.getLocation();
            int px = at.getBlockX(), py = at.getBlockY(), pz = at.getBlockZ();
            int masked = 0, visible = 0;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dy = -rad; dy <= rad; dy++) {
                    for (int dz = -rad; dz <= rad; dz++) {
                        int wx = px + dx, wy = py + dy, wz = pz + dz;
                        Block b = p.getWorld().getBlockAt(wx, wy, wz);
                        Material mat = b.getType();
                        if (mat == Material.AIR) continue;
                        if (!profile.hiddenBlocks.contains(mat)) continue;
                        boolean isMasked = plugin.xray().isMasked(p, wx, wy, wz);
                        Location c = new Location(p.getWorld(), wx + 0.5, wy + 0.5, wz + 0.5);
                        // Targeted at the caller only — other players don't see the particles.
                        p.spawnParticle(Particle.DUST, c, 4, 0.2, 0.2, 0.2, 0.0,
                                isMasked ? redDust : greenDust);
                        if (isMasked) masked++; else visible++;
                    }
                }
            }
            // Heartbeat action-bar so the player knows the highlight is running + counts.
            p.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7highlight r=" + rad + " §8| §cmasked=" + masked + " §2visible=" + visible));
        }, 0L, 10L); // 10-tick refresh = 2Hz, visible but not seizure-inducing

        highlightTasks.put(id, task);
        sender.sendMessage("§aHighlight activé §7r=§f" + radius + " §7durée=§f" + seconds + "s"
                + " §8(§crouge=masqué §2vert=visible§8)");
    }

    private void dumpProfile(CommandSender to, Player p) {
        if (p == null) return;
        var env = p.getWorld().getEnvironment();
        XrayProfile xp = plugin.acConfig().xrayProfile(env);
        MovementProfile mp = plugin.acConfig().movementProfile(env);
        to.sendMessage("§7Profile for §f" + p.getName() + " §7in §f" + env);
        to.sendMessage("§8 [xray] §7enabled=" + xp.enabled
                + " reveal=" + xp.revealDistance
                + " hidden-types=" + xp.hiddenBlocks.size()
                + " yRange=[" + xp.minY + "," + xp.maxY + "]"
                + " fakeDensity=" + xp.fakeOreDensity
                + " maskCave=" + xp.maskCaveOres);
        to.sendMessage("§8 [movement] §7enabled=" + mp.enabled
                + " noclipMaxStep=" + mp.noclipMaxStep
                + " walk/sprint=" + mp.speedWalkBps + "/" + mp.speedSprintBps
                + " flyMaxAir=" + mp.flyMaxAirborneTicks
                + " action=" + mp.action
                + " threshold=" + mp.violationThreshold);
        boolean bypassed = plugin.bypass().isBypassed(p);
        boolean runtimeBypass = plugin.bypass().isRuntimeBypass(p.getUniqueId());
        to.sendMessage("§8 §7bypass=§f" + bypassed
                + " §8(perm=" + p.hasPermission("anticheat.bypass")
                + " runtime=" + runtimeBypass + ")");
    }

    private void scan(CommandSender to, Player p, int radius) {
        XrayProfile profile = plugin.acConfig().xrayProfile(p.getWorld().getEnvironment());
        if (!profile.enabled) { to.sendMessage("§7Xray disabled for this env."); return; }
        int total = 0, masked = 0;
        Location at = p.getLocation();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int wx = at.getBlockX() + dx, wy = at.getBlockY() + dy, wz = at.getBlockZ() + dz;
                    Block b = p.getWorld().getBlockAt(wx, wy, wz);
                    if (!profile.hiddenBlocks.contains(b.getType())) continue;
                    if (b.getType() == Material.AIR) continue;
                    total++;
                    if (plugin.xray().isMasked(p, wx, wy, wz)) masked++;
                }
            }
        }
        to.sendMessage("§7Scan r=" + radius + " §8| §7hidden-blocks nearby=§f" + total + " §7masked=§f" + masked);
    }

    private int forceReveal(Player p, int radius) {
        int count = 0;
        Location at = p.getLocation();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int wx = at.getBlockX() + dx, wy = at.getBlockY() + dy, wz = at.getBlockZ() + dz;
                    if (plugin.xray().isMasked(p, wx, wy, wz)) {
                        plugin.xray().revealOnInteract(p, wx, wy, wz);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("simulate") || args[0].equalsIgnoreCase("bypass"))) {
            List<String> out = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            if (args[0].equalsIgnoreCase("bypass")) {
                for (String s : List.of("on", "off")) {
                    if (s.startsWith(args[1].toLowerCase())) out.add(s);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bypass")) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
