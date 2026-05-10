package fr.smp.ptr.tour;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.PtrBlock;
import fr.smp.ptr.item.PtrItem;
import fr.smp.ptr.mob.PtrMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ShowcaseBuilder {

    private final PtrShowcase plugin;

    public ShowcaseBuilder(PtrShowcase plugin) { this.plugin = plugin; }

    /**
     * Build a showcase plaza centered at the given block. Returns the tour stops.
     */
    public List<Location> build(Location center, Player by) {
        var world = center.getWorld();
        Location origin = center.clone();
        // Stamp a 32x32 platform of polished_blackstone
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                world.getBlockAt(origin.getBlockX() + dx, origin.getBlockY() - 1, origin.getBlockZ() + dz)
                        .setType(((dx + dz) & 1) == 0 ? Material.POLISHED_BLACKSTONE : Material.GILDED_BLACKSTONE);
            }
        }

        List<Location> stops = new ArrayList<>();

        // 0. Welcome arch + title hologram
        Location welcome = origin.clone();
        spawnText(welcome.clone().add(0, 3, 0), "PTR SHOWCASE", NamedTextColor.AQUA, 1.6f);
        spawnText(welcome.clone().add(0, 2.4, 0), "tour des features V3", NamedTextColor.GRAY, 1.0f);
        stops.add(welcome);

        // 1. Items zone (8 items)
        Location items = origin.clone().add(8, 0, 0);
        spawnText(items.clone().add(0, 3, 0), "Items 3D custom", NamedTextColor.YELLOW, 1.3f);
        int i = 0;
        for (PtrItem it : plugin.items().all().values()) {
            Location pad = items.clone().add(((i % 4) - 1.5) * 1.5, 1.0, ((i / 4) - 0.5) * 1.5);
            spawnItemDisplay(pad, it.create(), 0.7f);
            spawnText(pad.clone().add(0, 0.8, 0), it.displayName(), NamedTextColor.WHITE, 0.6f);
            i++;
        }
        stops.add(items);

        // 2. Blocks zone (5 furniture)
        Location blocks = origin.clone().add(8, 0, 8);
        spawnText(blocks.clone().add(0, 3, 0), "Blocs furniture", NamedTextColor.GOLD, 1.3f);
        int b = 0;
        for (PtrBlock pb : plugin.blocks().all().values()) {
            Location pad = blocks.clone().add(((b % 3) - 1) * 2.5, 0, ((b / 3) - 0.5) * 2.5);
            pb.place(pad, by);
            b++;
        }
        stops.add(blocks);

        // 3. Mobs zone (3 silhouettes - just display the rig items, not the mobs)
        Location mobs = origin.clone().add(0, 0, 8);
        spawnText(mobs.clone().add(0, 4, 0), "Boss 3D custom", NamedTextColor.RED, 1.3f);
        int m = 0;
        for (PtrMob pm : plugin.mobs().all().values()) {
            Location pad = mobs.clone().add((m - 1) * 3.0, 1.5, 0);
            // Use modelMaterial via a proxy: create a representative ItemStack via mob's static info
            ItemStack rig = makeMobIcon(pm);
            spawnItemDisplay(pad, rig, 1.5f);
            spawnText(pad.clone().add(0, 1.2, 0), pm.displayName(), NamedTextColor.LIGHT_PURPLE, 0.8f);
            spawnText(pad.clone().add(0, 0.6, 0), "/showcase spawn " + pm.id(), NamedTextColor.GRAY, 0.5f);
            m++;
        }
        stops.add(mobs);

        // 4. Biome zone (3 teleport pads with descriptions)
        Location biomes = origin.clone().add(-8, 0, 8);
        spawnText(biomes.clone().add(0, 3, 0), "Biomes datapack", NamedTextColor.GREEN, 1.3f);
        String[][] biomesData = {
                {"ptr:prismatic_dunes", "Prismatic Dunes", "desert chaud rose end_rod"},
                {"ptr:glitchwood",      "Glitchwood",      "mystique sombre endermen"},
                {"ptr:abyss_caves",     "Abyss Caves",     "deep cave warden silverfish"}};
        for (int k = 0; k < biomesData.length; k++) {
            Location pad = biomes.clone().add((k - 1) * 2.5, 0.05, 0);
            spawnText(pad.clone().add(0, 1.6, 0), biomesData[k][1], NamedTextColor.WHITE, 0.9f);
            spawnText(pad.clone().add(0, 1.0, 0), biomesData[k][2], NamedTextColor.GRAY, 0.5f);
            spawnText(pad.clone().add(0, 0.4, 0), "/showcase biome paint " + biomesData[k][0], NamedTextColor.AQUA, 0.5f);
            // Decorate pad
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    pad.getWorld().getBlockAt(pad.getBlockX() + dx, pad.getBlockY() - 1, pad.getBlockZ() + dz)
                            .setType(k == 0 ? Material.PINK_CONCRETE : k == 1 ? Material.PURPLE_CONCRETE : Material.BLACK_CONCRETE);
                }
        }
        stops.add(biomes);

        return stops;
    }

    private ItemStack makeMobIcon(PtrMob pm) {
        // Best effort: use the mob's expected modelMaterial reflectively
        try {
            var f = pm.getClass().getSuperclass().getDeclaredMethod("modelMaterial");
            f.setAccessible(true);
            Material mat = (Material) f.invoke(pm);
            int cmd = (int) pm.getClass().getSuperclass().getDeclaredMethod("customModelData").invoke(pm);
            ItemStack stack = new ItemStack(mat);
            if (cmd > 0) stack.editMeta(meta -> meta.setCustomModelData(cmd));
            return stack;
        } catch (Throwable t) {
            return new ItemStack(Material.SKELETON_SKULL);
        }
    }

    private void spawnText(Location at, String text, TextColor color, float scale) {
        at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.text(Component.text(text, color));
            e.setBillboard(Display.Billboard.CENTER);
            e.setSeeThrough(false);
            e.setShadowed(true);
            e.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()));
        });
    }

    private void spawnItemDisplay(Location at, ItemStack stack, float scale) {
        at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(stack);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()));
        });
    }
}
