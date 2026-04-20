package fr.smp.core.enchants;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Passive tick for the Vitality enchant: tops up absorption up to
 * (level * 4) hearts-worth while the player wears a chestplate/elytra
 * carrying the enchant.
 */
public class EnchantArmorTask extends BukkitRunnable {

    private final SMPCore plugin;
    private BukkitTask task;

    public EnchantArmorTask(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = runTaskTimer(plugin, 20L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private int tickCounter;

    @Override
    public void run() {
        tickCounter++;
        boolean debug = tickCounter % 20 == 0; // every 10s
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack chest = p.getInventory().getChestplate();
            int level = EnchantEngine.levelOf(chest, CustomEnchant.VITAL);
            if (level <= 0) continue;

            double max = level * 4.0; // 2 hearts per level, 1 heart = 2 units
            double cur = p.getAbsorptionAmount();
            if (debug) {
                plugin.getLogger().info("[Vitalité] " + p.getName()
                        + " lvl=" + level + " abs=" + cur + "/" + max);
            }
            if (cur < max) {
                p.setAbsorptionAmount(Math.min(max, cur + 1.0));
            }
        }
    }
}
