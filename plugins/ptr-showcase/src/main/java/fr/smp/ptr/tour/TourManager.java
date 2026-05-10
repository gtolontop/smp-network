package fr.smp.ptr.tour;

import fr.smp.ptr.PtrShowcase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class TourManager {

    private final PtrShowcase plugin;
    private final ShowcaseBuilder builder;

    public TourManager(PtrShowcase plugin) {
        this.plugin = plugin;
        this.builder = new ShowcaseBuilder(plugin);
    }

    public List<Location> setup(Location at, Player by) {
        return builder.build(at, by);
    }

    public void runTour(Player p) {
        Location origin = p.getLocation();
        List<Stop> built = new ArrayList<>();
        built.add(new Stop("Bienvenue au PTR", "Showcase de la limite Paper + display + pack.", origin));
        built.add(new Stop("Items 3D", "Foreuse, tronconneuse, grappin, scanner, voidstone.",
                origin.clone().add(8, 0, 0)));
        built.add(new Stop("Blocs furniture", "Forge runique, station de recharge, console, pylone.",
                origin.clone().add(8, 0, 8)));
        built.add(new Stop("Mobs custom", "Gardien Mine, Roi Pillards, Anomalie.",
                origin.clone().add(0, 0, 8)));
        built.add(new Stop("Biomes datapack", "Prismatic Dunes, Glitchwood, Abyss Caves.",
                origin.clone().add(-8, 0, 8)));

        new BukkitRunnable() {
            int idx = 0;
            @Override public void run() {
                if (idx >= built.size()) {
                    p.sendMessage(Component.text("Fin du tour PTR.", NamedTextColor.AQUA));
                    cancel();
                    return;
                }
                Stop s = built.get(idx++);
                p.teleport(s.where);
                p.showTitle(net.kyori.adventure.title.Title.title(
                        Component.text(s.title, NamedTextColor.AQUA),
                        Component.text(s.subtitle, NamedTextColor.GRAY)));
                p.playSound(s.where, Sound.UI_TOAST_IN, 1f, 1.4f);
            }
        }.runTaskTimer(plugin, 0L, 80L);
    }

    private record Stop(String title, String subtitle, Location where) {}
}
