package fr.smp.ptr;

import fr.smp.ptr.biome.BiomePainter;
import fr.smp.ptr.command.PtrCommand;
import fr.smp.ptr.item.ItemRegistry;
import fr.smp.ptr.item.ItemListener;
import fr.smp.ptr.block.BlockRegistry;
import fr.smp.ptr.block.BlockListener;
import fr.smp.ptr.block.BlockPlaceListener;
import fr.smp.ptr.block.NoteBlockGuardListener;
import fr.smp.ptr.block.PlacedBlocks;
import fr.smp.ptr.mob.MobRegistry;
import fr.smp.ptr.tour.TourManager;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class PtrShowcase extends JavaPlugin {

    private static PtrShowcase instance;

    private ItemRegistry itemRegistry;
    private BlockRegistry blockRegistry;
    private MobRegistry mobRegistry;
    private TourManager tourManager;
    private BiomePainter biomePainter;
    private PlacedBlocks placedBlocks;

    @Override
    public void onEnable() {
        instance = this;

        this.itemRegistry = new ItemRegistry(this);
        this.blockRegistry = new BlockRegistry(this);
        this.mobRegistry = new MobRegistry(this);
        this.tourManager = new TourManager(this);
        this.biomePainter = new BiomePainter(this);
        this.placedBlocks = new PlacedBlocks(this);

        this.itemRegistry.registerAll();
        this.blockRegistry.registerAll();
        this.mobRegistry.registerAll();

        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new NoteBlockGuardListener(this), this);

        var cmd = getCommand("showcase");
        if (cmd != null) {
            PtrCommand executor = new PtrCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("PtrShowcase enabled — items=" + itemRegistry.size()
                + " blocks=" + blockRegistry.size()
                + " mobs=" + mobRegistry.size());
    }

    @Override
    public void onDisable() {
        if (mobRegistry != null) mobRegistry.cleanupAll();
        if (blockRegistry != null) blockRegistry.cleanupAll();
        instance = null;
    }

    public static PtrShowcase get() { return instance; }

    public ItemRegistry items() { return itemRegistry; }
    public BlockRegistry blocks() { return blockRegistry; }
    public MobRegistry mobs() { return mobRegistry; }
    public TourManager tour() { return tourManager; }
    public BiomePainter biome() { return biomePainter; }
    public PlacedBlocks placedBlocks() { return placedBlocks; }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }
}
