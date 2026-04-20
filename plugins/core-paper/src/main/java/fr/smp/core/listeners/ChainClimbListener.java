package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;

public class ChainClimbListener implements Listener {

    private static final Set<Material> CLIMBABLE_CHAINS = EnumSet.of(
            Material.IRON_CHAIN,
            Material.COPPER_CHAIN,
            Material.EXPOSED_COPPER_CHAIN,
            Material.WEATHERED_COPPER_CHAIN,
            Material.OXIDIZED_COPPER_CHAIN,
            Material.WAXED_COPPER_CHAIN,
            Material.WAXED_EXPOSED_COPPER_CHAIN,
            Material.WAXED_WEATHERED_COPPER_CHAIN,
            Material.WAXED_OXIDIZED_COPPER_CHAIN
    );

    private final SMPCore plugin;

    public ChainClimbListener(SMPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private boolean isOnChain(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        if (p.isFlying() || p.isGliding()) return false;

        Location loc = p.getLocation();
        double r = 0.25;
        double[] dx = { 0, r, -r, 0, 0 };
        double[] dz = { 0, 0, 0, r, -r };
        double[] dy = { 0.1, 1.0, 1.7 };

        for (double y : dy) {
            for (int i = 0; i < dx.length; i++) {
                Block b = loc.clone().add(dx[i], y, dz[i]).getBlock();
                if (!CLIMBABLE_CHAINS.contains(b.getType())) continue;
                if (b.getBlockData() instanceof Orientable o && o.getAxis() == Axis.Y) {
                    return true;
                }
            }
        }
        return false;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isOnChain(p)) continue;

            Input in = p.getCurrentInput();
            Vector v = p.getVelocity();

            if (in != null && in.isJump()) {
                // Sustained climb while holding space.
                v.setY(0.22);
            } else if (in != null && in.isSneak()) {
                // Cling: freeze vertical motion.
                v.setY(0);
            } else {
                // Slow descent like a ladder.
                if (v.getY() < -0.15) v.setY(-0.15);
            }

            // Dampen horizontal so the player sticks.
            v.setX(v.getX() * 0.5);
            v.setZ(v.getZ() * 0.5);
            p.setVelocity(v);
            p.setFallDistance(0f);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (isOnChain(p)) e.setCancelled(true);
    }
}
