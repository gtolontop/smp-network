package fr.smp.logger;

import fr.smp.logger.backup.BackupModule;
import fr.smp.logger.commands.CommandsRegistrar;
import fr.smp.logger.db.Database;
import fr.smp.logger.db.PartitionManager;
import fr.smp.logger.dict.MaterialDict;
import fr.smp.logger.dict.PlayerDict;
import fr.smp.logger.dict.StringDict;
import fr.smp.logger.dict.WorldDict;
import fr.smp.logger.items.PreciousDetector;
import fr.smp.logger.items.PreciousStore;
import fr.smp.logger.modules.ModuleManager;
import fr.smp.logger.queue.EventBuilder;
import fr.smp.logger.queue.EventQueue;
import fr.smp.logger.rare.RareResourceTracker;
import fr.smp.logger.scan.ScanModule;
import fr.smp.logger.trade.TradeDetector;
import org.bukkit.plugin.java.JavaPlugin;

public class SMPLogger extends JavaPlugin implements EventBuilder.DictView {

    private Database db;
    private PartitionManager partitions;
    private EventQueue queue;
    private PlayerDict players;
    private MaterialDict materials;
    private WorldDict worlds;
    private StringDict strings;
    private PreciousDetector preciousDetector;
    private PreciousStore preciousStore;
    private TradeDetector tradeDetector;
    private RareResourceTracker rareTracker;
    private ScanModule scanModule;
    private ModuleManager modules;
    private BackupModule backup;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        long t0 = System.currentTimeMillis();

        this.db = new Database(this);
        db.connect();

        this.partitions = new PartitionManager(this, db);
        partitions.start();

        this.players = new PlayerDict(this, db);
        players.load();
        this.materials = new MaterialDict(this, db);
        materials.load();
        this.worlds = new WorldDict(this, db);
        worlds.load();
        this.strings = new StringDict(this, db);

        this.preciousDetector = new PreciousDetector(this);
        this.preciousStore = new PreciousStore(this, db, materials, preciousDetector);

        this.queue = new EventQueue(this, db, partitions);
        queue.start();

        this.tradeDetector = new TradeDetector(this);
        this.rareTracker = new RareResourceTracker(this);
        this.scanModule = new ScanModule(this);

        this.modules = new ModuleManager(this);
        modules.registerAll();

        this.backup = new BackupModule(this);
        backup.start();

        new CommandsRegistrar(this).registerAll();

        getLogger().info("SMPLogger ready in " + (System.currentTimeMillis() - t0) + "ms");
    }

    @Override
    public void onDisable() {
        if (backup != null) backup.stop();
        if (modules != null) modules.unregisterAll();
        if (queue != null) queue.stop();
        if (db != null) db.close();
    }

    // ---- accessors ----
    public Database db() { return db; }
    public PartitionManager partitions() { return partitions; }
    public EventQueue queue() { return queue; }
    @Override public PlayerDict players() { return players; }
    @Override public MaterialDict materials() { return materials; }
    @Override public WorldDict worlds() { return worlds; }
    @Override public StringDict strings() { return strings; }
    public PreciousDetector preciousDetector() { return preciousDetector; }
    public PreciousStore preciousStore() { return preciousStore; }
    public TradeDetector tradeDetector() { return tradeDetector; }
    public RareResourceTracker rareTracker() { return rareTracker; }
    public ScanModule scanModule() { return scanModule; }
    public BackupModule backup() { return backup; }
    public ModuleManager modules() { return modules; }
}
