package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FullbrightManager implements Listener {

    private static final int FULLBRIGHT_DURATION_TICKS = Integer.MAX_VALUE;
    private static final int FULLBRIGHT_MIN_DURATION_TICKS = 1_000_000_000;
    private static final PotionEffect FULLBRIGHT_EFFECT =
            new PotionEffect(PotionEffectType.NIGHT_VISION, FULLBRIGHT_DURATION_TICKS, 0, false, false, false);

    private final SMPCore plugin;

    public FullbrightManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return false;
        }
        boolean enabled = !data.fullbrightEnabled();
        data.setFullbrightEnabled(enabled);
        refresh(player);
        return enabled;
    }

    public void refresh(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return;
        }
        if (data.fullbrightEnabled()) {
            player.addPotionEffect(FULLBRIGHT_EFFECT, true);
            return;
        }
        clear(player);
    }

    public void clear(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (effect == null) {
            return;
        }
        if (looksLikeFullbright(effect)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    private boolean looksLikeFullbright(PotionEffect effect) {
        return effect.getAmplifier() == 0
                && effect.getDuration() >= FULLBRIGHT_MIN_DURATION_TICKS
                && !effect.isAmbient()
                && !effect.hasParticles()
                && !effect.hasIcon();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refresh(event.getPlayer()));
    }
}
