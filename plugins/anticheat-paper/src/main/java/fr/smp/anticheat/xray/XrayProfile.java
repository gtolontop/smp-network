package fr.smp.anticheat.xray;

import net.minecraft.world.level.block.Block;
import org.bukkit.Material;
import org.bukkit.craftbukkit.block.CraftBlockType;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-environment xray tuning. The most aggressive profile (Nether) uses a tiny
 * reveal radius and high fake-ore density so cheaters cannot map valuables.
 */
public final class XrayProfile {

    public final boolean enabled;
    public final Set<Material> hiddenBlocks;
    public final Set<Block> hiddenBlockTypes;
    public final int maxY;
    public final int minY;
    public final boolean fakeOreInjection;
    public final double fakeOreDensity;
    public final double revealDistance;
    /**
     * If true, ores that touch air (exposed on a cave wall) are ALSO masked — paranoid
     * mode where even naturally-visible ores require LoS + reveal-distance to show. If
     * false (default for overworld/end), exposed ores pass through unmasked: legitimate
     * cave mining shows ores instantly, only deep-buried ores are obfuscated. This is
     * Paper Anti-Xray's "engine-mode 1" behavior — still fully blocks xray cheats (they
     * see buried ores as stone at any distance) while preserving authentic cave UX.
     */
    public final boolean maskCaveOres;

    public XrayProfile(boolean enabled,
                       Set<Material> hiddenBlocks,
                       int maxY, int minY,
                       boolean fakeOreInjection, double fakeOreDensity,
                       double revealDistance,
                       boolean maskCaveOres) {
        this.enabled = enabled;
        this.hiddenBlocks = Set.copyOf(hiddenBlocks);
        Set<Block> blockTypes = new HashSet<>(hiddenBlocks.size());
        for (Material material : hiddenBlocks) {
            blockTypes.add(CraftBlockType.bukkitToMinecraft(material));
        }
        this.hiddenBlockTypes = Set.copyOf(blockTypes);
        this.maxY = maxY;
        this.minY = minY;
        this.fakeOreInjection = fakeOreInjection;
        this.fakeOreDensity = fakeOreDensity;
        this.revealDistance = revealDistance;
        this.maskCaveOres = maskCaveOres;
    }
}
