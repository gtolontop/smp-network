package fr.smp.anticheat.movement;

import fr.smp.anticheat.AntiCheatPlugin;
import fr.smp.anticheat.config.AntiCheatConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Movement validator: detects noclip, speed-hack and fly-hack on survival players.
 * Speed is checked over a ~1s sliding window to avoid false positives on
 * per-tick jitter (sprint-jump peaks, network bursts, knockback).
 */
public final class MovementModule implements Listener {

    private static final long SPEED_WINDOW_NS = 1_000_000_000L; // 1 second
    private static final long DAMAGE_GRACE_NS = 2_000_000_000L; // 2 seconds post-hit
    private static final long SOFT_LOG_INTERVAL_NS = 15_000_000_000L; // 15 seconds
    private static final long HARD_LOG_INTERVAL_NS = 5_000_000_000L; // 5 seconds
    // Ice speed multipliers: sprint on ice is ~2.5× walk, on blue/packed ice ~3×.
    private static final double ICE_MULT = 2.6;
    private static final double BLUE_ICE_MULT = 3.2;

    private final AntiCheatPlugin plugin;
    private final AntiCheatConfig cfg;

    private final ConcurrentMap<UUID, State> states = new ConcurrentHashMap<>();
    private BukkitTask task;

    public MovementModule(AntiCheatPlugin plugin, AntiCheatConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public boolean enabled() {
        return cfg.movementEnabled();
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (State s : states.values()) {
                if (s.violations > 0) s.violations = Math.max(0, s.violations - 1);
            }
        }, 200L, 200L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        states.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        states.put(e.getPlayer().getUniqueId(), new State(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        State state = states.remove(e.getPlayer().getUniqueId());
        if (state != null) flushPendingLogs(e.getPlayer(), state);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        State s = states.get(e.getPlayer().getUniqueId());
        if (s != null) {
            s.lastSafe = e.getTo();
            s.airborneTicks = 0;
            s.windowStartNs = System.nanoTime();
            s.windowHoriz = 0.0;
            s.graceUntilNs = System.nanoTime() + 3_000_000_000L;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        State s = states.get(p.getUniqueId());
        if (s != null) s.damageUntilNs = System.nanoTime() + DAMAGE_GRACE_NS;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!enabled()) return;
        Player p = e.getPlayer();
        if (plugin.bypass().isBypassed(p)) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (p.isFlying() || p.getAllowFlight()) return;

        State s = states.computeIfAbsent(p.getUniqueId(), id -> new State(p));
        long now = System.nanoTime();
        if (now < s.graceUntilNs) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;

        MovementProfile profile = cfg.movementProfile(p.getWorld().getEnvironment());
        if (!profile.enabled) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        boolean violated = false;
        String reason = null;

        if (profile.noclipEnabled) {
            double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (step > profile.noclipMaxStep) {
                violated = true;
                reason = "noclip-step (" + String.format("%.2f", step) + ")";
            } else if (step > 0.1 && segmentCrossesSolid(from, to)) {
                violated = true;
                reason = "noclip-traverse";
            }
        }

        boolean inDamageGrace = now < s.damageUntilNs;

        if (!violated && profile.speedEnabled && !inDamageGrace) {
            // Sliding window: accumulate horizontal distance and reset once the window
            // elapses. This smooths out per-tick peaks (sprint-jump bursts, lag spikes).
            if (now - s.windowStartNs >= SPEED_WINDOW_NS) {
                s.windowStartNs = now;
                s.windowHoriz = 0.0;
            }
            s.windowHoriz += horiz;
            double windowSecs = Math.max(1e-3, (now - s.windowStartNs) / 1_000_000_000.0);
            // Only start checking once the window has enough samples to be meaningful.
            if (windowSecs >= 0.4) {
                double bps = s.windowHoriz / windowSecs;
                double allowed = allowedBps(p, from, profile);
                double slack = profile.violationThreshold <= 1 ? 1.10 : 1.35;
                if (bps > allowed * slack) {
                    violated = true;
                    reason = "speed (" + String.format("%.2f", bps) + " bps > " + String.format("%.2f", allowed) + ")";
                }
            }
        } else if (inDamageGrace) {
            // Keep the window fresh during grace so we don't snap-check right after.
            s.windowStartNs = now;
            s.windowHoriz = 0.0;
        }

        if (!violated && profile.flyEnabled) {
            boolean inPortal = isInPortal(p);
            boolean airborne = !p.isOnGround() && !p.isInWater() && !p.isInLava()
                    && !isClimbable(p) && !p.isGliding() && !inPortal;
            if (inPortal) s.airborneTicks = 0;
            if (airborne) {
                if (dy >= -0.001) s.airborneTicks++;
                else s.airborneTicks = Math.max(0, s.airborneTicks - 2);
                if (s.airborneTicks > profile.flyMaxAirborneTicks) {
                    violated = true;
                    reason = "fly (airborne " + s.airborneTicks + "t)";
                }
            } else {
                s.airborneTicks = 0;
                s.lastSafe = from.clone();
            }
        }

        if (violated) {
            s.violations++;
            if (s.violations >= profile.violationThreshold) {
                handleViolation(p, s, profile, reason);
                s.violations = 0;
                s.windowStartNs = System.nanoTime();
                s.windowHoriz = 0.0;
            } else {
                logSoftViolation(p, s, profile, reason);
            }
        }
    }

    private double allowedBps(Player p, Location from, MovementProfile profile) {
        double allowed = profile.speedWalkBps;
        if (p.isSprinting()) allowed = profile.speedSprintBps;
        if (p.isSprinting() && !p.isOnGround()) allowed = profile.speedSprintJumpBps;

        PotionEffect speedPot = p.getPotionEffect(PotionEffectType.SPEED);
        if (speedPot != null) {
            allowed *= 1.0 + 0.20 * (speedPot.getAmplifier() + 1);
        }
        PotionEffect dolphin = p.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        if (dolphin != null) allowed *= 2.0;

        // Ice / packed ice / blue ice / soul-speed soul sand boost
        Material standingOn = from.getBlock().getRelative(0, -1, 0).getType();
        switch (standingOn) {
            case BLUE_ICE -> allowed *= BLUE_ICE_MULT;
            case PACKED_ICE, ICE, FROSTED_ICE -> allowed *= ICE_MULT;
            default -> {
                if (standingOn == Material.SOUL_SAND || standingOn == Material.SOUL_SOIL) {
                    // soul speed boots boost — check enchant on boots via modifier scale
                    allowed *= 1.6;
                }
            }
        }

        // Riptide trident windup gives huge burst for ~2s when in water / raining
        if (p.isRiptiding()) allowed = Double.MAX_VALUE;
        if (p.isInsideVehicle()) allowed = Double.MAX_VALUE;
        if (p.isGliding()) allowed = Double.MAX_VALUE;
        return allowed;
    }

    private void handleViolation(Player p, State s, MovementProfile profile, String reason) {
        String action = profile.action.toLowerCase();
        logHardViolation(p, s, reason);
        switch (action) {
            case "kick" -> p.kick(net.kyori.adventure.text.Component.text("AntiCheat: " + reason));
            case "teleport" -> {
                Location safe = s.lastSafe != null ? s.lastSafe : p.getWorld().getSpawnLocation();
                p.teleport(safe);
                s.airborneTicks = 0;
            }
            case "log" -> { /* no-op */ }
            default -> { /* no-op */ }
        }
    }

    private boolean isClimbable(Player p) {
        Block b = p.getLocation().getBlock();
        return b.getType().toString().contains("LADDER") || b.getType().toString().contains("VINE")
                || b.getType().toString().contains("SCAFFOLDING");
    }

    private boolean isInPortal(Player p) {
        Block feet = p.getLocation().getBlock();
        Block head = feet.getRelative(0, 1, 0);
        return isPortalBlock(feet.getType()) || isPortalBlock(head.getType());
    }

    private boolean isPortalBlock(Material m) {
        return m == Material.NETHER_PORTAL || m == Material.END_PORTAL
                || m == Material.END_GATEWAY || m == Material.END_PORTAL_FRAME;
    }

    private void logSoftViolation(Player p, State s, MovementProfile profile, String reason) {
        long now = System.nanoTime();
        String env = p.getWorld().getEnvironment().name();
        String message = "[AC] " + p.getName() + " soft-violation: " + reason
                + " (" + s.violations + "/" + profile.violationThreshold + ", env=" + env + ")";
        if (shouldEmit(now, s.lastSoftLogAtNs, SOFT_LOG_INTERVAL_NS)) {
            plugin.getLogger().info(message + summarizedSuffix(s.suppressedSoftLogs, s.lastSoftReason));
            s.lastSoftLogAtNs = now;
            s.suppressedSoftLogs = 0;
        } else {
            s.suppressedSoftLogs++;
        }
        s.lastSoftReason = reason;
    }

    private void logHardViolation(Player p, State s, String reason) {
        long now = System.nanoTime();
        String env = p.getWorld().getEnvironment().name();
        String message = "[AC] " + p.getName() + " VIOLATION (" + env + "): " + reason;
        if (shouldEmit(now, s.lastHardLogAtNs, HARD_LOG_INTERVAL_NS)) {
            plugin.getLogger().warning(message + summarizedSuffix(s.suppressedHardLogs, s.lastHardReason));
            s.lastHardLogAtNs = now;
            s.suppressedHardLogs = 0;
        } else {
            s.suppressedHardLogs++;
        }
        s.lastHardReason = reason;
    }

    private void flushPendingLogs(Player p, State s) {
        if (s.suppressedSoftLogs > 0) {
            plugin.getLogger().info("[AC] " + p.getName() + " soft-violation summary (env="
                    + p.getWorld().getEnvironment().name() + "): "
                    + s.suppressedSoftLogs + " additional suppressed"
                    + reasonSuffix(s.lastSoftReason));
        }
        if (s.suppressedHardLogs > 0) {
            plugin.getLogger().warning("[AC] " + p.getName() + " violation summary (env="
                    + p.getWorld().getEnvironment().name() + "): "
                    + s.suppressedHardLogs + " additional suppressed"
                    + reasonSuffix(s.lastHardReason));
        }
    }

    private boolean shouldEmit(long now, long lastLogAtNs, long intervalNs) {
        return lastLogAtNs == 0L || now - lastLogAtNs >= intervalNs;
    }

    private String summarizedSuffix(int suppressedCount, String lastReason) {
        if (suppressedCount <= 0) return "";
        return " [" + suppressedCount + " additional suppressed" + reasonSuffix(lastReason) + "]";
    }

    private String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : ", last=" + reason;
    }

    private boolean segmentCrossesSolid(Location from, Location to) {
        for (int i = 1; i <= 4; i++) {
            double t = i / 5.0;
            int bx = (int) Math.floor(from.getX() + (to.getX() - from.getX()) * t);
            int by = (int) Math.floor(from.getY() + (to.getY() - from.getY()) * t + 0.5);
            int bz = (int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * t);
            Block b = from.getWorld().getBlockAt(bx, by, bz);
            if (b.getType().isOccluding()) return true;
        }
        return false;
    }

    private static final class State {
        long windowStartNs = System.nanoTime();
        double windowHoriz = 0.0;
        long graceUntilNs = System.nanoTime() + 3_000_000_000L;
        long damageUntilNs = 0L;
        int airborneTicks = 0;
        int violations = 0;
        Location lastSafe;
        long lastSoftLogAtNs = 0L;
        int suppressedSoftLogs = 0;
        String lastSoftReason;
        long lastHardLogAtNs = 0L;
        int suppressedHardLogs = 0;
        String lastHardReason;

        State(Player player) {
            this.lastSafe = player.getLocation();
        }
    }
}
