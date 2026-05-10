package fr.smp.ptr.block;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.impl.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class BlockRegistry {

    private final PtrShowcase plugin;
    private final Map<String, PtrBlock> blocks = new LinkedHashMap<>();

    public BlockRegistry(PtrShowcase plugin) { this.plugin = plugin; }

    public void registerAll() {
        register(new ForgeRunique(plugin));
        register(new StationRecharge(plugin));
        register(new ConsoleMarcheNoir(plugin));
        register(new PyloneTelegraph(plugin));
        register(new TableauEvents(plugin));
        // Real custom blocks via note_block tuning
        register(new BlueGrass(plugin));
        register(new PlasmaBlock(plugin));
        register(new Darkstone(plugin));
        register(new GlowMoss(plugin));
        register(new Runesteel(plugin));
    }

    public void register(PtrBlock b) { blocks.put(b.id(), b); }
    public PtrBlock get(String id) { return blocks.get(id); }
    public Map<String, PtrBlock> all() { return blocks; }
    public int size() { return blocks.size(); }

    public void cleanupAll() {
        for (PtrBlock b : blocks.values()) b.cleanup();
    }
}
