package fr.smp.core.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Visual + audio cues applied to a player while they are awaiting auth.
 *
 * Blindness hides the world (no farming intel for unauthorised users on a
 * cracked imposter attempt), slowness pins them in place as a visual
 * reinforcement of the move-cancel logic, and a flat actionbar reminder
 * tells them what to do.
 */
public final class AuthEffects {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private AuthEffects() {}

    public static void apply(Player p) {
        // Long durations so the effect doesn't expire before they auth.
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 600, 1, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 600, 6, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 600, 4, false, false, false));
        p.setInvulnerable(true);
        p.setCollidable(false);
        // Keep Paper's anti-fly check quiet while the player is frozen mid-air
        // during /login or /register.
        p.setFlying(false);
        p.setAllowFlight(true);
        p.setFallDistance(0f);
    }

    public static void clear(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.WEAKNESS);
        p.setInvulnerable(false);
        p.setCollidable(true);
        p.setFallDistance(0f);
    }

    public static void reminder(Player p, boolean registered) {
        Component msg = registered
                ? MM.deserialize("<gradient:#fde68a:#fb923c><bold>⚠ Connecte-toi</bold></gradient> <dark_gray>•</dark_gray> <gray>tape</gray> <white>/login <mdp></white>")
                : MM.deserialize("<gradient:#a8edea:#fed6e3><bold>⚠ Inscris-toi</bold></gradient> <dark_gray>•</dark_gray> <gray>tape</gray> <white>/register <mdp> <mdp></white>");
        p.sendActionBar(msg);
    }

    public static Component kickComponent(String mini) {
        return MM.deserialize(
                "<gradient:#a8edea:#fed6e3><bold>SMP</bold></gradient>\n\n" + mini);
    }

    public static Component prefixed(String mini) {
        return MM.deserialize("<gradient:#a8edea:#fed6e3>Auth</gradient> <dark_gray>»</dark_gray> " + mini);
    }
}
