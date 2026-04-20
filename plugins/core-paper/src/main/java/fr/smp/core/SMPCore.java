package fr.smp.core;

import fr.smp.core.alchemytotem.AlchemyTotemManager;
import fr.smp.core.commands.*;
import fr.smp.core.data.PlayerDataManager;
import fr.smp.core.enchants.CustomEnchantListener;
import fr.smp.core.enchants.EnchantArmorTask;
import fr.smp.core.enchants.EnchantBreakListener;
import fr.smp.core.gui.ServerSelectorGUI;
import fr.smp.core.listeners.ChainClimbListener;
import fr.smp.core.listeners.ChatListener;
import fr.smp.core.listeners.CombatListener;
import fr.smp.core.listeners.DeathListener;
import fr.smp.core.listeners.GUIListener;
import fr.smp.core.listeners.JoinListener;
import fr.smp.core.listeners.LobbyProtectionListener;
import fr.smp.core.listeners.SpawnerListener;
import fr.smp.core.listeners.WeatherListener;
import fr.smp.core.listeners.WorthHoverListener;
import fr.smp.core.logging.LogManager;
import fr.smp.core.managers.*;
import fr.smp.core.permissions.OpCommand;
import fr.smp.core.permissions.PermCommand;
import fr.smp.core.permissions.PermissionsManager;
import fr.smp.core.storage.Database;
import fr.smp.core.sync.SyncListener;
import fr.smp.core.sync.SyncManager;
import fr.smp.core.utils.ChatPrompt;
import fr.smp.core.utils.MessageChannel;
import fr.smp.core.utils.NetworkTabCompleter;
import fr.smp.core.utils.TeamTabCompleter;
import fr.smp.core.utils.TpsReporter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class SMPCore extends JavaPlugin {

    private static SMPCore instance;

    // Pre-existing
    private ServerSelectorGUI serverSelector;
    private MessageChannel messageChannel;
    private SyncManager syncManager;
    private TpsReporter tpsReporter;
    private String serverType;

    // New systems
    private Database database;
    private LogManager logs;
    private PlayerDataManager players;
    private SpawnManager spawns;
    private WorldBorderManager worldborders;
    private RtpManager rtp;
    private CombatTagManager combat;
    private HomeManager homes;
    private WarpManager warps;
    private TpaManager tpa;
    private TeamManager teams;
    private EconomyManager economy;
    private WorthManager worth;
    private ShopManager shop;
    private AuctionManager auction;
    private ScoreboardManager scoreboard;
    private PlaytimeManager playtime;
    private TeamInviteManager teamInvites;
    private ChatPrompt chatPrompt;
    private ServerStatsManager serverStats;
    private TabListManager tabList;
    private PendingTeleportManager pendingTp;
    private PermissionsManager permissions;
    private NetworkRoster roster;
    private NametagManager nametags;
    private EndToggleManager endToggle;
    private WeatherListener weather;
    private WaypointManager waypoints;
    private MessageManager messages;
    private SitManager sit;
    private AfkManager afk;
    private VanishManager vanish;
    private ModerationManager moderation;
    private BountyManager bounties;
    private HuntedManager hunted;
    private AlchemyTotemManager alchemyTotem;
    private SpawnerManager spawners;
    private EnchantArmorTask enchantArmor;
    private ResourcePackManager resourcePacks;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        serverType = resolveServerType();
        getLogger().info("Server type resolved to: " + serverType);

        // Storage + logging
        database = new Database(this);
        database.connect();
        logs = new LogManager(this);
        logs.start();

        // Core
        messageChannel = new MessageChannel(this);
        syncManager = new SyncManager(this);
        serverSelector = new ServerSelectorGUI(this);

        // Managers
        players = new PlayerDataManager(this, database);
        spawns = new SpawnManager(this);
        worldborders = new WorldBorderManager(this);
        rtp = new RtpManager(this, worldborders);
        combat = new CombatTagManager(this);
        combat.start();
        homes = new HomeManager(this, database);
        warps = new WarpManager(this, database);
        tpa = new TpaManager(this);
        teams = new TeamManager(this, database, players);
        teamInvites = new TeamInviteManager();
        chatPrompt = new ChatPrompt(this);
        serverStats = new ServerStatsManager();
        tabList = new TabListManager(this);
        pendingTp = new PendingTeleportManager(this);
        roster = new NetworkRoster();
        permissions = new PermissionsManager(this, database);
        permissions.load();
        economy = new EconomyManager(this, players);
        worth = new WorthManager(this);
        worth.load();
        shop = new ShopManager(this);
        shop.load();
        auction = new AuctionManager(this, database);

        // Apply configured world-borders (no-op on lobby if the worlds aren't loaded)
        worldborders.applyAll();

        // Scoreboard + playtime/shard sync are available on every server now.
        scoreboard = new ScoreboardManager(this, players, teams);
        scoreboard.start();
        playtime = new PlaytimeManager(this, players);
        playtime.start();
        tabList.start();
        nametags = new NametagManager(this);
        nametags.start();
        endToggle = new EndToggleManager(this);
        waypoints = new WaypointManager(this, database);
        messages = new MessageManager();
        sit = new SitManager(this);
        afk = new AfkManager(this);
        afk.start();
        vanish = new VanishManager(this);
        moderation = new ModerationManager(this, database);
        bounties = new BountyManager(this, database);
        if (!isLobby()) {
            hunted = new HuntedManager(this, database);
            hunted.start();
        }

        // Totem d'Alchimie : uniquement sur survival (pas de combat en lobby).
        if (!isLobby()) {
            alchemyTotem = new AlchemyTotemManager(this);
            alchemyTotem.start();
        }

        // Spawners custom : uniquement survival. Les blocs SPAWNER dans le
        // lobby restent vanilla (interdits par le LobbyProtectionListener de toute façon).
        if (!isLobby()) {
            spawners = new SpawnerManager(this);
            spawners.start();
        }

        // Per-dimension resource packs (Nether/End) — survival only.
        if (!isLobby()) {
            resourcePacks = new ResourcePackManager(this);
        }

        // Best-effort disable of the vanilla locator bar HUD.
        LocatorBarDisabler.apply(this);

        // Listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new JoinListener(this), this);
        pm.registerEvents(new GUIListener(this), this);
        pm.registerEvents(chatPrompt, this);
        WorthHoverListener worthHover = new WorthHoverListener(this);
        pm.registerEvents(worthHover, this);
        worthHover.start();
        // Death + combat tag only make sense on a survival server.
        if (!isLobby()) {
            pm.registerEvents(new DeathListener(this), this);
            pm.registerEvents(new CombatListener(this), this);
        }
        if (syncManager.isEnabled()) {
            pm.registerEvents(new SyncListener(this, syncManager), this);
        }
        if (getConfig().getBoolean("chat.enabled", true)) {
            pm.registerEvents(new ChatListener(this), this);
        }
        pm.registerEvents(endToggle, this);
        weather = new WeatherListener(this);
        pm.registerEvents(weather, this);
        weather.clearNow();
        if (isLobby()) {
            pm.registerEvents(new LobbyProtectionListener(this), this);
        }
        pm.registerEvents(new ChainClimbListener(this), this);
        pm.registerEvents(sit, this);
        pm.registerEvents(afk, this);
        pm.registerEvents(vanish, this);
        pm.registerEvents(moderation, this);
        if (spawners != null) {
            pm.registerEvents(new SpawnerListener(this), this);
        }
        if (resourcePacks != null) {
            pm.registerEvents(resourcePacks, this);
        }

        // Custom enchants (table + anvil + mob-drop + soulbound + area-break + armor task).
        pm.registerEvents(new CustomEnchantListener(this), this);
        if (!isLobby()) {
            pm.registerEvents(new EnchantBreakListener(this), this);
            enchantArmor = new EnchantArmorTask(this);
            enchantArmor.start();
        }

        // Commands
        registerCommands();

        tpsReporter = new TpsReporter(this);
        tpsReporter.start();

        // Diagnostic: list loaded worlds + their environments so we can tell
        // whether Paper exposes the nether/end as separate Bukkit Worlds or
        // as sub-dimensions of the primary world (affects portal / RTP fixes).
        for (World w : Bukkit.getWorlds()) {
            getLogger().info("World loaded: name='" + w.getName() + "' env=" + w.getEnvironment() + " key=" + w.getKey());
        }

        getLogger().info("SMPCore loaded (" + serverType + ").");
    }

    /**
     * Resolve a world by name, falling back to the first loaded world whose
     * Environment matches. Handles Paper 26.x unified-world layout where the
     * nether/end may not be named world_nether / world_the_end anymore.
     */
    public World resolveWorld(String configuredName, World.Environment envFallback) {
        if (configuredName != null) {
            World w = Bukkit.getWorld(configuredName);
            if (w != null) return w;
        }
        if (envFallback != null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == envFallback) return w;
            }
        }
        return null;
    }

    private void registerCommands() {
        getCommand("menu").setExecutor(new MenuCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("setspawn").setExecutor(new SetSpawnCommand(this, false));
        getCommand("sethubspawn").setExecutor(new SetSpawnCommand(this, true));

        // All gameplay commands are now registered on both servers so the
        // network behaves like a single server from the player's POV.

        getCommand("rtp").setExecutor(new RtpCommand(this));
        getCommand("wb").setExecutor(new WorldBorderCommand(this));

        getCommand("tp").setExecutor(new TpCommand(this));
        getCommand("tpa").setExecutor(new TpaCommand(this, "to"));
        getCommand("tpahere").setExecutor(new TpaCommand(this, "here"));
        getCommand("tpaccept").setExecutor(new TpaCommand(this, "accept"));
        getCommand("tpdeny").setExecutor(new TpaCommand(this, "deny"));
        getCommand("tpacancel").setExecutor(new TpaCommand(this, "cancel"));

        getCommand("warp").setExecutor(new WarpCommand(this, "warp"));
        getCommand("warps").setExecutor(new WarpCommand(this, "warps"));
        getCommand("setwarp").setExecutor(new WarpCommand(this, "setwarp"));
        getCommand("delwarp").setExecutor(new WarpCommand(this, "delwarp"));

        getCommand("find").setExecutor(new FindCommand(this));

        getCommand("bal").setExecutor(new EconomyCommand(this, "bal"));
        getCommand("pay").setExecutor(new EconomyCommand(this, "pay"));
        getCommand("shards").setExecutor(new EconomyCommand(this, "shards"));
        getCommand("eco").setExecutor(new EconomyCommand(this, "eco"));
        getCommand("baltop").setExecutor(new EconomyCommand(this, "baltop"));
        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("shop").setExecutor(new ShopCommand(this));

        getCommand("home").setExecutor(new HomeCommand(this, "home"));
        getCommand("homes").setExecutor(new HomeCommand(this, "homes"));
        getCommand("sethome").setExecutor(new HomeCommand(this, "sethome"));
        getCommand("delhome").setExecutor(new HomeCommand(this, "delhome"));

        getCommand("team").setExecutor(new TeamCommand(this));
        getCommand("ah").setExecutor(new AuctionCommand(this));
        getCommand("end").setExecutor(new EndCommand(this));

        getCommand("heads").setExecutor(new HeadsCommand(this));
        getCommand("bounty").setExecutor(new BountyCommand(this));
        getCommand("waypoints").setExecutor(new WaypointsCommand(this));
        getCommand("msg").setExecutor(new MsgCommand(this, "msg"));
        getCommand("r").setExecutor(new MsgCommand(this, "reply"));
        getCommand("here").setExecutor(new HereCommand(this));
        getCommand("sit").setExecutor(new SitCommand(this));
        getCommand("playtime").setExecutor(new PlaytimeCommand(this));
        getCommand("ping").setExecutor(new PingCommand(this));
        getCommand("saphir").setExecutor(new SaphirCommand(this));
        getCommand("invsee").setExecutor(new InvseeCommand(this));
        getCommand("vanish").setExecutor(new VanishCommand(this));
        getCommand("kick").setExecutor(new ModerationCommand(this, "kick"));
        getCommand("ban").setExecutor(new ModerationCommand(this, "ban"));
        getCommand("unban").setExecutor(new ModerationCommand(this, "unban"));
        getCommand("mute").setExecutor(new ModerationCommand(this, "mute"));
        getCommand("unmute").setExecutor(new ModerationCommand(this, "unmute"));

        // Keyall désactivé pour l'instant (feature en pause).
        // getCommand("keyall").setExecutor(new KeyallCommand(this));
        getCommand("sb").setExecutor(new ScoreboardToggleCommand(this));

        if (getCommand("spawner") != null) {
            SpawnerCommand spawnerCmd = new SpawnerCommand(this);
            getCommand("spawner").setExecutor(spawnerCmd);
            getCommand("spawner").setTabCompleter(spawnerCmd);
        }

        if (getCommand("ce") != null) {
            EnchantAdminCommand ceCmd = new EnchantAdminCommand(this);
            getCommand("ce").setExecutor(ceCmd);
            getCommand("ce").setTabCompleter(ceCmd);
        }

        PermCommand permCmd = new PermCommand(this);
        getCommand("perm").setExecutor(permCmd);
        getCommand("perm").setTabCompleter(permCmd);
        OpCommand opCmd = new OpCommand(this, true);
        getCommand("op").setExecutor(opCmd);
        getCommand("op").setTabCompleter(opCmd);
        OpCommand deopCmd = new OpCommand(this, false);
        getCommand("deop").setExecutor(deopCmd);
        getCommand("deop").setTabCompleter(deopCmd);

        if (getCommand("online") != null) {
            getCommand("online").setExecutor(new OnlineCommand(this));
        }

        // Network-wide tab completion for commands taking a player name.
        NetworkTabCompleter p0 = new NetworkTabCompleter(this, 0, false);
        NetworkTabCompleter p0Self = new NetworkTabCompleter(this, 0, true);
        // /tp has two player-name positions: index 0 (target), or index 0+1 for /tp <player> <target>.
        NetworkTabCompleter tpCompleter = new NetworkTabCompleter(this, 0, true);
        getCommand("tp").setTabCompleter((sender, cmd, alias, args) -> {
            if (args.length == 1 || args.length == 2) {
                return tpCompleter.networkPlayerNames(sender, args[args.length - 1].toLowerCase());
            }
            return java.util.List.of();
        });
        getCommand("tpa").setTabCompleter(p0);
        getCommand("tpahere").setTabCompleter(p0);
        getCommand("msg").setTabCompleter(p0);
        getCommand("pay").setTabCompleter(p0);
        getCommand("find").setTabCompleter(p0);
        getCommand("invsee").setTabCompleter(p0);
        getCommand("ping").setTabCompleter(p0Self);
        getCommand("playtime").setTabCompleter(p0Self);
        getCommand("bal").setTabCompleter(p0Self);
        getCommand("kick").setTabCompleter(p0);
        getCommand("ban").setTabCompleter(p0);
        getCommand("mute").setTabCompleter(p0);
        getCommand("unban").setTabCompleter(p0);
        getCommand("unmute").setTabCompleter(p0);
        getCommand("team").setTabCompleter(new TeamTabCompleter(this));

        // /waypoints: only "player" subcommand takes a name at index 1.
        getCommand("waypoints").setTabCompleter((sender, cmd, alias, args) -> {
            if (args.length == 1) {
                var subs = java.util.List.of("list", "set", "del", "here", "tp", "player");
                var out = new java.util.ArrayList<String>();
                String pref = args[0].toLowerCase();
                for (String s : subs) if (pref.isEmpty() || s.startsWith(pref)) out.add(s);
                return out;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
                return new NetworkTabCompleter(this, 1, false).networkPlayerNames(sender, args[1].toLowerCase());
            }
            return java.util.List.of();
        });

        // /bounty <list|check|set|remove> [player] [amount]
        getCommand("bounty").setTabCompleter((sender, cmd, alias, args) -> {
            if (args.length == 1) {
                var subs = new java.util.ArrayList<String>(java.util.List.of("list", "check", "set"));
                if (sender.hasPermission("smp.admin")) subs.add("remove");
                var out = new java.util.ArrayList<String>();
                String pref = args[0].toLowerCase();
                for (String s : subs) if (pref.isEmpty() || s.startsWith(pref)) out.add(s);
                return out;
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("set") || sub.equals("add") || sub.equals("place")
                        || sub.equals("check") || sub.equals("info")
                        || sub.equals("remove") || sub.equals("cancel")) {
                    return new NetworkTabCompleter(this, 1, false).networkPlayerNames(sender, args[1].toLowerCase());
                }
            }
            return java.util.List.of();
        });

        // /eco <give|take|set> <player> <amount> — player at index 1.
        getCommand("eco").setTabCompleter((sender, cmd, alias, args) -> {
            if (args.length == 1) {
                var subs = java.util.List.of("give", "take", "set");
                var out = new java.util.ArrayList<String>();
                String pref = args[0].toLowerCase();
                for (String s : subs) if (pref.isEmpty() || s.startsWith(pref)) out.add(s);
                return out;
            }
            if (args.length == 2) {
                return new NetworkTabCompleter(this, 1, true).networkPlayerNames(sender, args[1].toLowerCase());
            }
            return java.util.List.of();
        });
    }

    @Override
    public void onDisable() {
        if (enchantArmor != null) enchantArmor.stop();
        if (alchemyTotem != null) alchemyTotem.shutdown();
        if (spawners != null) spawners.stop();
        if (hunted != null) hunted.shutdown();
        if (tpsReporter != null) tpsReporter.stop();
        if (syncManager != null) syncManager.saveAllOnline();
        if (players != null) players.saveAll();
        if (logs != null) logs.stop();
        if (database != null) database.close();
        instance = null;
    }

    public static SMPCore getInstance() { return instance; }

    /**
     * Resolve server type in priority order:
     *   1. JVM property `-Dsmp.server.type=...`
     *   2. Env var `SMP_SERVER_TYPE`
     *   3. Working directory name (".../survival/" → "survival")
     *   4. config.yml `server-type`
     *   5. default "lobby"
     */
    private String resolveServerType() {
        String prop = System.getProperty("smp.server.type");
        if (prop != null && !prop.isBlank()) return prop.toLowerCase();
        String env = System.getenv("SMP_SERVER_TYPE");
        if (env != null && !env.isBlank()) return env.toLowerCase();
        String cwd = new java.io.File("").getAbsolutePath().replace('\\', '/');
        String lower = cwd.toLowerCase();
        if (lower.endsWith("/survival") || lower.contains("/survival/")) return "survival";
        if (lower.endsWith("/lobby") || lower.contains("/lobby/")) return "lobby";
        return getConfig().getString("server-type", "lobby");
    }

    // Pre-existing getters
    public ServerSelectorGUI getServerSelector() { return serverSelector; }
    public MessageChannel getMessageChannel() { return messageChannel; }
    public SyncManager getSyncManager() { return syncManager; }
    public String getServerType() { return serverType; }
    public boolean isLobby() { return "lobby".equals(serverType); }

    // New getters
    public Database database() { return database; }
    public LogManager logs() { return logs; }
    public PlayerDataManager players() { return players; }
    public SpawnManager spawns() { return spawns; }
    public WorldBorderManager worldborders() { return worldborders; }
    public RtpManager rtp() { return rtp; }
    public CombatTagManager combat() { return combat; }
    public HomeManager homes() { return homes; }
    public WarpManager warps() { return warps; }
    public TpaManager tpa() { return tpa; }
    public TeamManager teams() { return teams; }
    public EconomyManager economy() { return economy; }
    public WorthManager worth() { return worth; }
    public ShopManager shop() { return shop; }
    public AuctionManager auction() { return auction; }
    public ScoreboardManager scoreboard() { return scoreboard; }
    public PlaytimeManager playtime() { return playtime; }
    public TeamInviteManager teamInvites() { return teamInvites; }
    public ChatPrompt chatPrompt() { return chatPrompt; }
    public ServerStatsManager serverStats() { return serverStats; }
    public TabListManager tabList() { return tabList; }
    public PendingTeleportManager pendingTp() { return pendingTp; }
    public PermissionsManager permissions() { return permissions; }
    public NetworkRoster roster() { return roster; }
    public NametagManager nametags() { return nametags; }
    public EndToggleManager endToggle() { return endToggle; }
    public WaypointManager waypoints() { return waypoints; }
    public MessageManager messages() { return messages; }
    public SitManager sit() { return sit; }
    public AfkManager afk() { return afk; }
    public VanishManager vanish() { return vanish; }
    public ModerationManager moderation() { return moderation; }
    public BountyManager bounties() { return bounties; }
    public HuntedManager hunted() { return hunted; }
    public AlchemyTotemManager alchemyTotem() { return alchemyTotem; }
    public SpawnerManager spawners() { return spawners; }
    public ResourcePackManager resourcePacks() { return resourcePacks; }
}
