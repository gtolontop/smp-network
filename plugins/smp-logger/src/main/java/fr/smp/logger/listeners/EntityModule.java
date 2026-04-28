package fr.smp.logger.listeners;

import fr.smp.logger.SMPLogger;
import fr.smp.logger.model.Action;
import fr.smp.logger.queue.EventBuilder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Logs all entity-life events:
 *  - kills (with weapon material)
 *  - player deaths
 *  - mob spawns (with reason in meta)
 *  - tame / breed / leash / unleash
 *  - damage events (sampled to avoid spam from redstone trap farms)
 */
public class EntityModule implements Listener {

    private final SMPLogger plugin;

    public EntityModule(SMPLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        Player killer = victim.getKiller();
        EventBuilder eb = EventBuilder.begin(plugin)
                .action(victim instanceof Player ? Action.PLAYER_DEATH : Action.ENTITY_KILL)
                .at(victim)
                .material(victim.getType().getKey().toString());
        if (killer != null) {
            eb.actor(killer);
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            if (weapon != null && !weapon.getType().isAir()) {
                eb.meta(plugin.materials().idOf(weapon.getType()));
                eb.item(weapon);
            }
        }
        if (victim instanceof Player vp) eb.target(vp);
        eb.submit();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        // EntityDeathEvent fires too; we use this only to capture death message.
        Player victim = e.getEntity();
        String msg = e.getDeathMessage();
        if (msg == null || msg.isEmpty()) return;
        EventBuilder.begin(plugin)
                .action(Action.PLAYER_DEATH)
                .actor(victim)
                .at(victim)
                .text(msg)
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        // Skip the most spammy reasons; keep the meaningful ones.
        switch (e.getSpawnReason()) {
            case CHUNK_GEN, NATURAL, JOCKEY, MOUNT, TRAP, RAID, PATROL, SLIME_SPLIT -> {
                if ((System.nanoTime() & 0x1FF) != 0) return; // 1/512 sample
            }
            default -> {}
        }
        EventBuilder.begin(plugin)
                .action(Action.MOB_SPAWN)
                .at(e.getEntity())
                .material(e.getEntity().getType().getKey().toString())
                .meta(e.getSpawnReason().ordinal())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (!(e.getOwner() instanceof Player p)) return;
        EventBuilder.begin(plugin)
                .action(Action.ENTITY_TAME)
                .actor(p)
                .at(e.getEntity())
                .material(e.getEntity().getType().getKey().toString())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        EventBuilder eb = EventBuilder.begin(plugin)
                .action(Action.ENTITY_BREED)
                .at(e.getEntity())
                .material(e.getEntity().getType().getKey().toString());
        if (e.getBreeder() instanceof Player p) eb.actor(p);
        eb.submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent e) {
        EventBuilder.begin(plugin)
                .action(Action.ENTITY_LEASH)
                .actor(e.getPlayer())
                .at(e.getEntity())
                .material(e.getEntity().getType().getKey().toString())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnleash(EntityUnleashEvent e) {
        if (e.getReason() != EntityUnleashEvent.UnleashReason.PLAYER_UNLEASH
                && e.getReason() != EntityUnleashEvent.UnleashReason.HOLDER_GONE) return;
        EventBuilder.begin(plugin)
                .action(Action.ENTITY_UNLEASH)
                .at(e.getEntity())
                .material(e.getEntity().getType().getKey().toString())
                .meta(e.getReason().ordinal())
                .submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHurt(EntityDamageByEntityEvent e) {
        // Only player-on-player and player-on-named-mob; everything else would flood.
        Entity damager = e.getDamager();
        Entity victim = e.getEntity();
        Player attacker = null;
        if (damager instanceof Player p) attacker = p;
        else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;
        if (!(victim instanceof Player) && (victim.getCustomName() == null)) return;
        EventBuilder eb = EventBuilder.begin(plugin)
                .action(Action.ENTITY_DAMAGE)
                .actor(attacker)
                .at(victim)
                .material(victim.getType().getKey().toString())
                .amount((int) Math.round(e.getFinalDamage() * 10));
        if (victim instanceof Player vp) eb.target(vp);
        eb.submit();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!(proj.getShooter() instanceof Player p)) return;
        Entity hit = e.getHitEntity();
        if (hit == null) return;
        EventBuilder.begin(plugin)
                .action(Action.PROJECTILE_HIT)
                .actor(p)
                .at(hit)
                .material(proj.getType().getKey().toString())
                .meta(plugin.materials().idOf(hit.getType().getKey().toString()))
                .submit();
    }
}
