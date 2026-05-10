package fr.smp.ptr.biome;

import fr.smp.ptr.PtrShowcase;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;

public class BiomePainter {

    private record SurfacePalette(Material top, Material accent, Material under) {}

    private static final Map<String, SurfacePalette> PALETTES = Map.of(
            "ptr:prismatic_dunes", new SurfacePalette(Material.PINK_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.SAND),
            "ptr:glitchwood",      new SurfacePalette(Material.WARPED_WART_BLOCK, Material.OBSIDIAN, Material.WARPED_NYLIUM),
            "ptr:abyss_caves",     new SurfacePalette(Material.SCULK, Material.DEEPSLATE, Material.DEEPSLATE)
    );

    private final PtrShowcase plugin;

    public BiomePainter(PtrShowcase plugin) { this.plugin = plugin; }

    public boolean paint(Player p, String biomeId, int radius) {
        NamespacedKey key;
        try {
            key = NamespacedKey.fromString(biomeId);
        } catch (Throwable t) { return false; }
        if (key == null) return false;

        Biome biome;
        try {
            biome = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.BIOME)
                    .get(key);
        } catch (Throwable t) {
            p.sendMessage(Component.text("Erreur registre biome: " + t.getMessage(), NamedTextColor.RED));
            return false;
        }
        if (biome == null) {
            p.sendMessage(Component.text("Biome introuvable: " + biomeId
                    + " (datapack ptr_content present?)", NamedTextColor.RED));
            return false;
        }
        World w = p.getWorld();
        Location c = p.getLocation();
        int cx = c.getBlockX(), cz = c.getBlockZ();
        int minY = w.getMinHeight(), maxY = w.getMaxHeight() - 1;
        int painted = 0;
        SurfacePalette palette = PALETTES.get(biomeId);
        java.util.Set<Long> chunks = new java.util.HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx*dx + dz*dz > radius*radius) continue;
                int wx = cx + dx, wz = cz + dz;
                // Biome at every 4 blocks (biomes are 4x4x4)
                if ((dx & 3) == 0 && (dz & 3) == 0) {
                    for (int y = minY; y <= maxY; y += 8) {
                        w.setBiome(wx, y, wz, biome);
                        painted++;
                    }
                }
                // Surface stamping
                if (palette != null) {
                    int topY = w.getHighestBlockYAt(wx, wz);
                    Block top = w.getBlockAt(wx, topY, wz);
                    if (!top.getType().isAir() && top.getType() != Material.WATER) {
                        Material pick = ((dx + dz) & 1) == 0 ? palette.top() : palette.accent();
                        top.setType(pick, false);
                        Block under = w.getBlockAt(wx, topY - 1, wz);
                        if (!under.getType().isAir() && under.getType() != Material.WATER) {
                            under.setType(palette.under(), false);
                        }
                    }
                }
                chunks.add(((long) (wx >> 4) << 32) | ((wz >> 4) & 0xFFFFFFFFL));
            }
        }
        // Refresh visible chunks
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (long packed : chunks) {
                int chx = (int) (packed >> 32);
                int chz = (int) (packed & 0xFFFFFFFFL);
                w.refreshChunk(chx, chz);
            }
        }, 5L);
        p.sendMessage(Component.text("Peint " + painted + " noeuds biome + surface (" + chunks.size() + " chunks) " + key,
                NamedTextColor.AQUA));
        return true;
    }

    public boolean locateNearest(Player p, String biomeId, int radius) {
        NamespacedKey key = NamespacedKey.fromString(biomeId);
        if (key == null) return false;
        Biome biome = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
        if (biome == null) {
            p.sendMessage(Component.text("Biome introuvable.", NamedTextColor.RED));
            return false;
        }
        var result = p.getWorld().locateNearestBiome(p.getLocation(), radius, 32, 64, biome);
        if (result == null) {
            p.sendMessage(Component.text("Aucun " + key + " dans " + radius + " blocs.", NamedTextColor.YELLOW));
            return false;
        }
        Location loc = result.getLocation();
        p.sendMessage(Component.text("Trouve " + key + " a " + (int) loc.distance(p.getLocation()) + " blocs.",
                NamedTextColor.AQUA));
        p.setCompassTarget(loc);
        return true;
    }
}
