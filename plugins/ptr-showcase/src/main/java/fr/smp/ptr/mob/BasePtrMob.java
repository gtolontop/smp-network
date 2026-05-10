package fr.smp.ptr.mob;

import fr.smp.ptr.PtrShowcase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class BasePtrMob implements PtrMob {

    protected final PtrShowcase plugin;

    protected BasePtrMob(PtrShowcase plugin) { this.plugin = plugin; }

    protected abstract EntityType baseEntity();
    protected abstract Material modelMaterial();
    protected int customModelData() { return 0; }
    protected double maxHealth() { return 60.0; }
    protected double damage() { return 6.0; }
    protected float modelScale() { return 1.0f; }
    protected Color glowColor() { return Color.RED; }
    protected BarColor barColor() { return BarColor.RED; }

    @Override
    public org.bukkit.entity.Entity spawn(Location at, Player by) {
        var world = at.getWorld();
        Mob mob = (Mob) world.spawnEntity(at, baseEntity());
        if (mob instanceof LivingEntity living) {
            var attrMax = living.getAttribute(Attribute.MAX_HEALTH);
            if (attrMax != null) {
                attrMax.setBaseValue(maxHealth());
                living.setHealth(maxHealth());
            }
            var attrAtk = living.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attrAtk != null) attrAtk.setBaseValue(damage());
            living.customName(Component.text(displayName(), NamedTextColor.RED));
            living.setCustomNameVisible(false);
            // Try invisibility on living variants that allow it
            try { living.setInvisible(true); } catch (Throwable ignored) {}
            living.getEquipment().clear();
        }
        var idKey = plugin.key("ptr_mob_id");
        mob.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id());

        // Attach an item display rig as passenger
        ItemDisplay disp = world.spawn(at, ItemDisplay.class, e -> {
            ItemStack stack = new ItemStack(modelMaterial());
            int cmd = customModelData();
            if (cmd > 0) {
                stack.editMeta(m -> {
                    try {
                        var comp = m.getCustomModelDataComponent();
                        comp.setFloats(java.util.List.of((float) cmd));
                        m.setCustomModelDataComponent(comp);
                    } catch (Throwable t) {
                        m.setCustomModelData(cmd);
                    }
                });
            }
            e.setItemStack(stack);
            e.setBillboard(Display.Billboard.FIXED);
            float s = modelScale();
            e.setTransformation(new Transformation(
                    new Vector3f(0, 0.6f, 0),
                    new Quaternionf(),
                    new Vector3f(s, s, s),
                    new Quaternionf()));
            e.setGlowColorOverride(glowColor());
            e.setGlowing(true);
            e.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id());
        });
        mob.addPassenger(disp);

        BossBar bar = org.bukkit.Bukkit.createBossBar(displayName(), barColor(), BarStyle.SEGMENTED_10);
        if (by != null) bar.addPlayer(by);
        bar.setVisible(true);
        bar.setProgress(1.0);

        new BukkitRunnable() {
            @Override public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    bar.removeAll();
                    if (disp.isValid()) disp.remove();
                    cancel();
                    return;
                }
                if (mob instanceof LivingEntity le) {
                    var max = le.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) {
                        bar.setProgress(Math.max(0, Math.min(1, le.getHealth() / max.getBaseValue())));
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 10L);

        return mob;
    }
}
