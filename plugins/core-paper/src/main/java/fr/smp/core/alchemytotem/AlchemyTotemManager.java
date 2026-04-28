package fr.smp.core.alchemytotem;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.Keyed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pilote l'ensemble : recettes, détection HP, application d'effet, conso de charges.
 *
 * Règles:
 *   - HP <= 8 (4 coeurs): une charge est consommée à l'entrée, l'effet tourne en continu.
 *   - HP remonte au-dessus de 8: rémanence de 45s pendant laquelle l'effet reste appliqué.
 *   - Charges à 0: l'item est détruit (particules + son).
 *
 * La résurrection vanilla du totem reste opérationnelle (consomme l'item vanilla-style).
 */
public final class AlchemyTotemManager implements Listener {

    private static final double HP_THRESHOLD = 8.0;
    private static final long REMANENCE_MS = 45_000L;
    private static final long TICK_PERIOD = 10L; // 0.5s
    private static final int EFFECT_DURATION_TICKS = 40; // renouvelé à chaque tick, overlap large

    private final SMPCore plugin;
    private final AlchemyTotemItem items;

    private final Map<AlchemyEffect, NamespacedKey> recipeKeys = new EnumMap<>(AlchemyEffect.class);
    private final Map<NamespacedKey, AlchemyEffect> effectByKey = new HashMap<>();

    private final Map<UUID, Session> sessions = new HashMap<>();

    /** État par joueur: savoir si on est en phase "below", quel effet on pousse, et jusqu'à quand. */
    private static final class Session {
        boolean below;
        long expiresAt;              // 0 = pas de rémanence; >0 = timestamp de fin
        AlchemyEffect activeEffect;  // effet qu'on est en train d'appliquer (null si aucun)
    }

    public AlchemyTotemManager(SMPCore plugin) {
        this.plugin = plugin;
        this.items = new AlchemyTotemItem(plugin);
    }

    public AlchemyTotemItem items() { return items; }

    public void start() {
        registerRecipes();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        for (NamespacedKey key : recipeKeys.values()) {
            Bukkit.removeRecipe(key);
        }
        recipeKeys.clear();
        effectByKey.clear();
        sessions.clear();
    }

    private void registerRecipes() {
        for (AlchemyEffect effect : AlchemyEffect.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "alchemy_totem_" + effect.id());
            ItemStack result = items.build(effect, AlchemyTotemItem.MAX_CHARGES);

            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("EDE", "NSN", "EDE");
            recipe.setIngredient('E', effect.ingredient());
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
            recipe.setIngredient('N', Material.NETHERITE_INGOT);
            recipe.setIngredient('S', Material.NETHER_STAR);

            Bukkit.addRecipe(recipe);
            recipeKeys.put(effect, key);
            effectByKey.put(key, effect);
        }
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;
        AlchemyEffect effect = effectByKey.get(keyed.getKey());
        if (effect == null) return;
        event.getInventory().setResult(items.build(effect, AlchemyTotemItem.MAX_CHARGES));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid() || player.isDead()) continue;
            handle(player, now);
        }
    }

    private void handle(Player player, long now) {
        SlotRef ref = findTotem(player);
        Session session = sessions.get(player.getUniqueId());
        boolean below = player.getHealth() <= HP_THRESHOLD;

        if (ref == null) {
            // Plus de totem en inventaire : clôturer proprement la session.
            if (session != null) {
                if (session.activeEffect != null) player.removePotionEffect(session.activeEffect.type());
                sessions.remove(player.getUniqueId());
            }
            return;
        }

        AlchemyEffect effect = items.readEffect(ref.item());
        int charges = items.readCharges(ref.item());
        if (effect == null) return;

        if (session == null) {
            session = new Session();
            sessions.put(player.getUniqueId(), session);
        }

        if (below) {
            if (!session.below) {
                // Transition haut -> bas : tenter de consommer une charge.
                if (charges <= 0) {
                    destroyTotem(player, ref, session);
                    return;
                }
                items.decrementCharges(ref.item());
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
                player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                        30, 0.4, 0.6, 0.4, 0.3);
                session.below = true;
                session.expiresAt = 0L;
            }
            apply(player, session, effect);
        } else {
            if (session.below) {
                session.below = false;
                session.expiresAt = now + REMANENCE_MS;
            }
            if (session.expiresAt > now) {
                apply(player, session, effect);
            } else if (session.activeEffect != null) {
                player.removePotionEffect(session.activeEffect.type());
                session.activeEffect = null;
                session.expiresAt = 0L;
            }
        }
    }

    private void apply(Player player, Session session, AlchemyEffect effect) {
        if (session.activeEffect != null && session.activeEffect != effect) {
            player.removePotionEffect(session.activeEffect.type());
        }
        int ticks = EFFECT_DURATION_TICKS;
        if (session.expiresAt > 0) {
            long remaining = session.expiresAt - System.currentTimeMillis();
            ticks = (int) Math.max(TICK_PERIOD + 10, remaining / 50L);
        }
        player.addPotionEffect(new PotionEffect(effect.type(), ticks, effect.amplifier(),
                false, true, true));
        session.activeEffect = effect;
    }

    private void destroyTotem(Player player, SlotRef ref, Session session) {
        player.getInventory().setItem(ref.slot(), null);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.7f);
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                80, 0.5, 0.8, 0.5, 0.5);
        if (session.activeEffect != null) player.removePotionEffect(session.activeEffect.type());
        sessions.remove(player.getUniqueId());
    }

    private SlotRef findTotem(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (items.isTotem(contents[i])) return new SlotRef(i, contents[i]);
        }
        return null;
    }

    private record SlotRef(int slot, ItemStack item) {}
}
