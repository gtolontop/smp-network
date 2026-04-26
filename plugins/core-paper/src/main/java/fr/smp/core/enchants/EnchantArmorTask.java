package fr.smp.core.enchants;

import fr.smp.core.SMPCore;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack chest = p.getInventory().getChestplate();
            int level = EnchantEngine.levelOf(chest, CustomEnchant.VITAL);
            double target = level * 2.0;
            AttributeInstance maxAbsorption = p.getAttribute(Attribute.MAX_ABSORPTION);
            if (maxAbsorption == null) continue;

            boolean capChanged = syncVitalityCap(maxAbsorption, target);
            double cap = maxAbsorption.getValue();
            double cur = p.getAbsorptionAmount();

            if (cur > cap) {
                p.setAbsorptionAmount(cap);
                capChanged = true;
            }

            if (target > 0.0 && cur < target) {
                double newVal = Math.min(target, cur + 1.0);
                p.setAbsorptionAmount(newVal);
                capChanged = true;
            }

            if (capChanged) {
                p.sendHealthUpdate();
            }
        }
    }

    private boolean syncVitalityCap(AttributeInstance maxAbsorption, double target) {
        double base = maxAbsorption.getBaseValue();
        if (target <= 0.0) {
            if (nearlyEquals(base, 0.0)) return false;
            maxAbsorption.setBaseValue(0.0);
            return true;
        }
        if (nearlyEquals(base, target)) return false;
        maxAbsorption.setBaseValue(target);
        return true;
    }

    private boolean nearlyEquals(double left, double right) {
        return Math.abs(left - right) < 1.0E-6;
    }
}
