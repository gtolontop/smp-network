package fr.smp.ptr.foundation.drop;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.registry.PtrIdentified;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

/**
 * Spawns a {@link TextDisplay} hologram showing a boss's damage
 * leaderboard. Auto-despawns after a configurable lifetime.
 *
 * <p>The hologram is one {@link TextDisplay} per spawn — cheap and
 * region-thread-friendly. Lines are formatted in Adventure and joined with
 * newlines so a single component drives the whole rendering.
 */
public final class LeaderboardHologram {

    private LeaderboardHologram() {}

    /** Spawn a leaderboard hologram at {@code location} for {@code ticksToLive}. */
    public static void spawn(
            @NotNull SchedulerService scheduler,
            @NotNull Location location,
            @NotNull PtrIdentified boss,
            @NotNull List<DamageTracker.Entry> leaderboard,
            int ticksToLive,
            int topRanks) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(boss, "boss");
        Objects.requireNonNull(leaderboard, "leaderboard");
        if (ticksToLive <= 0) {
            throw new IllegalArgumentException("ticksToLive must be > 0, got " + ticksToLive);
        }

        Component content = render(boss, leaderboard, Math.max(1, topRanks));

        TextDisplay display =
                location.getWorld()
                        .spawn(
                                location.clone().add(0, 1.5, 0),
                                TextDisplay.class,
                                td -> {
                                    td.text(content);
                                    td.setBillboard(Display.Billboard.CENTER);
                                    td.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
                                    td.setShadowed(true);
                                    td.setSeeThrough(false);
                                    td.setAlignment(TextDisplay.TextAlignment.CENTER);
                                });

        scheduler.runOnEntityLater(
                display, () -> {
                    if (display.isValid()) {
                        display.remove();
                    }
                }, null, ticksToLive);
    }

    private static Component render(
            PtrIdentified boss, List<DamageTracker.Entry> leaderboard, int topRanks) {
        Component header =
                Component.text("DEFEATED ", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(boss.displayName().color(NamedTextColor.GOLD));
        Component body = Component.empty();
        int max = Math.min(topRanks, leaderboard.size());
        for (int i = 0; i < max; i++) {
            DamageTracker.Entry entry = leaderboard.get(i);
            int rank = i + 1;
            NamedTextColor rankColor =
                    rank == 1
                            ? NamedTextColor.GOLD
                            : rank == 2
                                    ? NamedTextColor.YELLOW
                                    : rank == 3
                                            ? NamedTextColor.AQUA
                                            : NamedTextColor.GRAY;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.player());
            String name = offline.getName() == null ? "—" : offline.getName();
            Component line =
                    Component.newline()
                            .append(Component.text("#" + rank + " ", rankColor))
                            .append(Component.text(name, NamedTextColor.WHITE))
                            .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                            .append(
                                    Component.text(
                                            String.format("%.0f", entry.damage()),
                                            NamedTextColor.RED));
            body = body.append(line);
        }
        return header.append(body);
    }
}
