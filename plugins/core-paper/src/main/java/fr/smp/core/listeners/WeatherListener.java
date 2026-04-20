package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces a "never rain" rule on worlds listed in config (weather.no-rain-worlds).
 * Also forces current weather clear on startup for those worlds.
 */
public class WeatherListener implements Listener {

    private final SMPCore plugin;

    public WeatherListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    private Set<String> noRainWorlds() {
        return plugin.getConfig().getStringList("weather.no-rain-worlds").stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public void clearNow() {
        Set<String> list = noRainWorlds();
        for (World w : plugin.getServer().getWorlds()) {
            if (!list.contains(w.getName().toLowerCase())) continue;
            w.setStorm(false);
            w.setThundering(false);
            w.setWeatherDuration(Integer.MAX_VALUE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (!event.toWeatherState()) return;
        if (noRainWorlds().contains(event.getWorld().getName().toLowerCase())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        if (!event.toThunderState()) return;
        if (noRainWorlds().contains(event.getWorld().getName().toLowerCase())) {
            event.setCancelled(true);
        }
    }
}
