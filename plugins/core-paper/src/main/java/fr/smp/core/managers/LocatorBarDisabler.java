package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.lang.reflect.Field;

/**
 * Best-effort disabler for the vanilla locator bar HUD added in 1.21.x.
 *
 * The gamerule name is not stable across snapshots (has been seen as
 * `locatorBar`, `sendPlayerLocator`, `playerLocator`, etc.) and the
 * Bukkit {@link GameRule} constants are also not stable across Paper
 * builds in this range. We try each candidate via reflection and fall
 * back to {@code /gamerule} console commands if none resolve.
 */
public final class LocatorBarDisabler {

    private LocatorBarDisabler() {}

    public static void apply(SMPCore plugin) {
        if (!plugin.getConfig().getBoolean("world.disable-locator-bar", true)) return;

        String[] candidateConstants = {
                "LOCATOR_BAR", "PLAYER_LOCATOR", "SEND_PLAYER_LOCATOR",
                "LOCATOR", "WAYPOINT_TRANSMIT_RANGE"
        };
        String[] candidateIds = {
                "locatorBar", "playerLocator", "sendPlayerLocator", "locator"
        };

        GameRule<Boolean> boolRule = null;
        for (String name : candidateConstants) {
            try {
                Field f = GameRule.class.getDeclaredField(name);
                Object v = f.get(null);
                if (v instanceof GameRule<?> gr && gr.getType() == Boolean.class) {
                    @SuppressWarnings("unchecked")
                    GameRule<Boolean> boolGr = (GameRule<Boolean>) gr;
                    boolRule = boolGr;
                    break;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        }

        if (boolRule != null) {
            for (World w : Bukkit.getWorlds()) w.setGameRule(boolRule, false);
            plugin.getLogger().info("Locator bar gamerule disabled: " + boolRule.getName());
            return;
        }

        // Fallback: try to run `/gamerule <candidate> false` on every world.
        for (String id : candidateIds) {
            for (World w : Bukkit.getWorlds()) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "execute in " + w.getName() + " run gamerule " + id + " false");
                } catch (Throwable ignored) {}
            }
        }
    }
}
