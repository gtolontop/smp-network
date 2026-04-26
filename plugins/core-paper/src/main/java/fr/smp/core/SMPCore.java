package fr.smp.core;

import fr.smp.core.alchemytotem.AlchemyTotemManager;
import fr.smp.core.auth.AuthAdminCommand;
import fr.smp.core.auth.AuthCommand;
import fr.smp.core.auth.AuthListener;
import fr.smp.core.auth.AuthManager;
import fr.smp.core.commands.*;
import fr.smp.core.data.PlayerDataManager;
import fr.smp.core.discord.DiscordBridge;
import fr.smp.core.enchants.CustomEnchantListener;
import fr.smp.core.enchants.EnchantArmorTask;
import fr.smp.core.enchants.EnchantBreakListener;
import fr.smp.core.enchants.GrindstoneListener;
import fr.smp.core.gui.ServerSelectorGUI;
import fr.smp.core.holograms.HologramCommand;
import fr.smp.core.holograms.HologramManager;
import fr.smp.core.npc.NpcCommand;
import fr.smp.core.npc.NpcManager;
import fr.smp.core.voidstone.VoidstoneManager;
import fr.smp.core.listeners.ChainClimbListener;
import fr.smp.core.listeners.ChatListener;
import fr.smp.core.listeners.CombatListener;
import fr.smp.core.listeners.DeathListener;
import fr.smp.core.listeners.GodListener;
import fr.smp.core.listeners.GUIListener;
import fr.smp.core.listeners.GateListener;
import fr.smp.core.listeners.JoinListener;
import fr.smp.core.listeners.LobbyProtectionListener;
import fr.smp.core.listeners.MachineBoostListener;
import fr.smp.core.listeners.SeedSpoofListener;
import fr.smp.core.listeners.SpamGuard;
import fr.smp.core.listeners.SpawnerListener;
import fr.smp.core.listeners.VillagerBucketListener;
import fr.smp.core.listeners.VoidListener;
import fr.smp.core.listeners.VoidstoneListener;
import fr.smp.core.listeners.WeatherListener;
import fr.smp.core.listeners.WorthHoverListener;
import fr.smp.core.logging.LogManager;
import fr.smp.core.net.WorthDisplayInjector;
import fr.smp.core.managers.*;
import fr.smp.core.permissions.OpCommand;
import fr.smp.core.permissions.PermCommand;
import fr.smp.core.permissions.PermissionsManager;
import fr.smp.core.skins.SkinManager;
import fr.smp.core.storage.Database;
import fr.smp.core.sync.InventoryHistoryCommand;
import fr.smp.core.sync.InventoryHistoryManager;
import fr.smp.core.sync.SyncListener;
import fr.smp.core.sync.SyncManager;
import fr.smp.core.utils.ChatPrompt;
import fr.smp.core.utils.MessageChannel;
import fr.smp.core.utils.NetworkTabCompleter;
import fr.smp.core.utils.TeamTabCompleter;
import fr.smp.core.utils.TpsReporter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class SMPCore extends JavaPlugin {

    private static SMPCore instance;

    // Pre-existing
    private ServerSelectorGUI serverSelector;
    private MessageChannel messageChannel;
    private SyncManager syncManager;
    private InventoryHistoryManager invHistory;
    private TpsReporter tpsReporter;
    private String serverType;
    private long startedAtMillis;

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
    private LeaderboardManager leaderboards;
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
    private PhantomToggleManager phantomToggle;
    private WeatherListener weather;
    private WaypointManager waypoints;
    private MessageManager messages;
    private SitManager sit;
    private AfkManager afk;
    private VanishManager vanish;
    private FullbrightManager fullbright;
    private AdminModeManager adminMode;
    private ModerationManager moderation;
    private BountyManager bounties;
    private HuntedManager hunted;
    private AlchemyTotemManager alchemyTotem;
    private SpawnerManager spawners;
    private VoidstoneManager voidstones;
    private EnchantArmorTask enchantArmor;
    private ResourcePackManager resourcePacks;
    private GateManager gates;
    private GateWandVisualizer gateWandViz;
    private AuthManager auth;
    private JoinListener joinListener;
    private NpcManager npcs;
    private HologramManager holograms;
    private DiscordBridge discordBridge;
    private CooldownManager cooldowns;
    private WorthHoverListener worthHover;
    private WorthDisplayInjector worthDisplay;
    private SkinManager skins;
    private GodManager god;
    private volatile boolean chatLocked = false;

    @Override
    public void onEnable() {
        instance = this;
        startedAtMillis = System.currentTimeMillis();
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
        leaderboards = new LeaderboardManager(this, database, players);
        teamInvites = new TeamInviteManager();
        chatPrompt = new ChatPrompt(this);
        serverStats = new ServerStatsManager();
        tabList = new TabListManager(this);
        pendingTp = new PendingTeleportManager(this);
        roster = new NetworkRoster();
        permissions = new PermissionsManager(this, database);
        permissions.load();
        economy = new EconomyManager(this, players);
        cooldowns = new CooldownManager(this);
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
        phantomToggle = new PhantomToggleManager(this);
        waypoints = new WaypointManager(this, database);
        messages = new MessageManager();
        sit = new SitManager(this);
        afk = new AfkManager(this);
        afk.start();
        vanish = new VanishManager(this);
        fullbright = new FullbrightManager(this);
        adminMode = new AdminModeManager(this);
        god = new GodManager();
        moderation = new ModerationManager(this, database);
        bounties = new BountyManager(this, database);
        if (isMainSurvival()) {
            hunted = new HuntedManager(this, database);
            hunted.start();
        }

        // Totem d'Alchimie : uniquement sur survival (pas de combat en lobby).
        if (isMainSurvival()) {
            alchemyTotem = new AlchemyTotemManager(this);
            alchemyTotem.start();
        }

        // Spawners custom : uniquement survival. Les blocs SPAWNER dans le
        // lobby restent vanilla (interdits par le LobbyProtectionListener de toute façon).
        if (isMainSurvival()) {
            spawners = new SpawnerManager(this);
            spawners.start();
        }

        if (isMainSurvival()) {
            voidstones = new VoidstoneManager(this);
            voidstones.start();
        }

        // Per-dimension resource packs (Nether/End) — survival only.
        if (isMainSurvival()) {
            resourcePacks = new ResourcePackManager(this);
        }

        // Animated gates (both servers: usable for spawn portcullis, dungeon doors, etc.)
        gates = new GateManager(this, database);
        gates.start();
        gateWandViz = new GateWandVisualizer(this);
        gateWandViz.start();

        // NPCs (fake-player) + holograms (TextDisplay) — utilisables sur les deux serveurs
        npcs = new NpcManager(this, database);
        npcs.start();
        holograms = new HologramManager(this, database);
        holograms.start();

        // Discord bridge — WebSocket client towards the companion bot.
        discordBridge = new DiscordBridge(this);
        discordBridge.start();

        // Best-effort disable of the vanilla locator bar HUD.
        LocatorBarDisabler.apply(this);

        // Auth — must register BEFORE JoinListener so the freeze is applied
        // before any other join-side effect runs.
        auth = new AuthManager(this, database);
        auth.start();
        skins = new SkinManager(this, database);
        skins.start();

        // Listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new AuthListener(this, auth), this);
        pm.registerEvents(skins, this);
        joinListener = new JoinListener(this);
        pm.registerEvents(joinListener, this);
        pm.registerEvents(new GUIListener(this), this);
        pm.registerEvents(chatPrompt, this);
        worthHover = new WorthHoverListener(this);
        pm.registerEvents(worthHover, this);
        worthHover.start();
        worthDisplay = new WorthDisplayInjector(this);
        pm.registerEvents(worthDisplay, this);
        worthDisplay.start();
        // Death + combat tag only make sense on a survival server.
        if (isMainSurvival()) {
            pm.registerEvents(new DeathListener(this), this);
            pm.registerEvents(new CombatListener(this), this);
        }
        // Void detection — transfer to lobby before the player dies.
        if (!isLobby()) {
            pm.registerEvents(new VoidListener(this), this);
        }
        if (syncManager.isEnabled()) {
            pm.registerEvents(new SyncListener(this, syncManager), this);
        }
        // Inventory rollback history — only on servers with real inventories (skip lobby).
        if (syncManager.isEnabled() && !isLobby()) {
            invHistory = new InventoryHistoryManager(this, database, syncManager);
            invHistory.start();
        }
        if (getConfig().getBoolean("chat.enabled", true)) {
            // SpamGuard first (LOWEST) so it runs before ChatListener (NORMAL)
            // and the mute check in ModerationManager (HIGHEST).
            pm.registerEvents(new SpamGuard(this), this);
            pm.registerEvents(new ChatListener(this), this);
        }
        pm.registerEvents(endToggle, this);
        pm.registerEvents(phantomToggle, this);
        weather = new WeatherListener(this);
        pm.registerEvents(weather, this);
        weather.clearNow();
        if (isLobby()) {
            pm.registerEvents(new LobbyProtectionListener(this), this);
        }
        pm.registerEvents(new ChainClimbListener(this), this);
        pm.registerEvents(new SeedSpoofListener(this), this);
        if (!isLobby()) {
            pm.registerEvents(new MachineBoostListener(this), this);
        }
        if (isMainSurvival()) {
            pm.registerEvents(new VillagerBucketListener(this), this);
        }
        pm.registerEvents(sit, this);
        pm.registerEvents(afk, this);
        pm.registerEvents(vanish, this);
        pm.registerEvents(fullbright, this);
        pm.registerEvents(new GodListener(this), this);
        pm.registerEvents(moderation, this);
        if (spawners != null) {
            pm.registerEvents(new SpawnerListener(this), this);
        }
        if (voidstones != null) {
            pm.registerEvents(new VoidstoneListener(this), this);
        }
        if (resourcePacks != null) {
            pm.registerEvents(resourcePacks, this);
        }
        pm.registerEvents(new GateListener(this), this);
        if (npcs != null) pm.registerEvents(npcs, this);

        // Custom enchants (table + anvil + mob-drop + soulbound + area-break + armor task).
        // The gameplay listeners must run on both the live survival server and PTR;
        // only the lobby should skip them.
        pm.registerEvents(new CustomEnchantListener(this), this);
        pm.registerEvents(new GrindstoneListener(this), this);
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
        if (getCommand("leaderboard") != null) {
            LeaderboardCommand leaderboardCommand = new LeaderboardCommand(this);
            getCommand("leaderboard").setExecutor(leaderboardCommand);
            getCommand("leaderboard").setTabCompleter(leaderboardCommand);
        }
        SellCommand sellCmd = new SellCommand(this);
        getCommand("sell").setExecutor(sellCmd);
        getCommand("sellall").setExecutor(sellCmd);
        getCommand("worth").setExecutor(new WorthCommand(this));
        getCommand("shop").setExecutor(new ShopCommand(this));

        getCommand("home").setExecutor(new HomeCommand(this, "home"));
        getCommand("homes").setExecutor(new HomeCommand(this, "homes"));
        getCommand("sethome").setExecutor(new HomeCommand(this, "sethome"));
        getCommand("delhome").setExecutor(new HomeCommand(this, "delhome"));

        getCommand("team").setExecutor(new TeamCommand(this));
        getCommand("ah").setExecutor(new AuctionCommand(this));
        getCommand("end").setExecutor(new EndCommand(this));
        getCommand("phantom").setExecutor(new PhantomCommand(this));

        getCommand("heads").setExecutor(new HeadsCommand(this));
        getCommand("bounty").setExecutor(new BountyCommand(this));
        getCommand("waypoints").setExecutor(new WaypointsCommand(this));
        getCommand("msg").setExecutor(new MsgCommand(this, "msg"));
        getCommand("r").setExecutor(new MsgCommand(this, "reply"));
        getCommand("here").setExecutor(new HereCommand(this));
        getCommand("sit").setExecutor(new SitCommand(this));
        getCommand("playtime").setExecutor(new PlaytimeCommand(this));
        getCommand("stat").setExecutor(new StatCommand(this));
        getCommand("ping").setExecutor(new PingCommand(this));
        getCommand("saphir").setExecutor(new SaphirCommand(this));
        getCommand("invsee").setExecutor(new InvseeCommand(this));
        getCommand("vanish").setExecutor(new VanishCommand(this));
        getCommand("vanish").setTabCompleter(new VanishCommand(this));
        getCommand("fullbright").setExecutor(new FullbrightCommand(this));
        getCommand("admin").setExecutor(new AdminCommand(this));
        getCommand("god").setExecutor(new GodCommand(this));
        getCommand("heal").setExecutor(new HealCommand(this));
        getCommand("fly").setExecutor(new FlyCommand(this));
        getCommand("speed").setExecutor(new SpeedCommand(this));
        getCommand("furnace").setExecutor(new FurnaceCommand(this));
        getCommand("kick").setExecutor(new ModerationCommand(this, "kick"));
        getCommand("ban").setExecutor(new ModerationCommand(this, "ban"));
        getCommand("unban").setExecutor(new ModerationCommand(this, "unban"));
        getCommand("mute").setExecutor(new ModerationCommand(this, "mute"));
        getCommand("unmute").setExecutor(new ModerationCommand(this, "unmute"));
        getCommand("banlist").setExecutor(new BanlistCommand(this));

        InfoAdminCommand infoAdminCmd = new InfoAdminCommand(this);
        getCommand("infoadmin").setExecutor(infoAdminCmd);
        getCommand("infoadmin").setTabCompleter(infoAdminCmd);

        // Keyall désactivé pour l'instant (feature en pause).
        // getCommand("keyall").setExecutor(new KeyallCommand(this));
        getCommand("sb").setExecutor(new ScoreboardToggleCommand(this));

        if (getCommand("spawner") != null) {
            SpawnerCommand spawnerCmd = new SpawnerCommand(this);
            getCommand("spawner").setExecutor(spawnerCmd);
            getCommand("spawner").setTabCompleter(spawnerCmd);
        }

        if (getCommand("voidstone") != null) {
            VoidstoneCommand voidstoneCmd = new VoidstoneCommand(this);
            getCommand("voidstone").setExecutor(voidstoneCmd);
            getCommand("voidstone").setTabCompleter(voidstoneCmd);
        }

        if (getCommand("ce") != null) {
            EnchantAdminCommand ceCmd = new EnchantAdminCommand(this);
            getCommand("ce").setExecutor(ceCmd);
            getCommand("ce").setTabCompleter(ceCmd);
        }

        if (getCommand("gate") != null) {
            GateCommand gateCmd = new GateCommand(this);
            getCommand("gate").setExecutor(gateCmd);
            getCommand("gate").setTabCompleter(gateCmd);
        }

        if (getCommand("npc") != null) {
            NpcCommand npcCmd = new NpcCommand(this);
            getCommand("npc").setExecutor(npcCmd);
            getCommand("npc").setTabCompleter(npcCmd);
        }

        if (getCommand("holo") != null) {
            HologramCommand holoCmd = new HologramCommand(this);
            getCommand("holo").setExecutor(holoCmd);
            getCommand("holo").setTabCompleter(holoCmd);
        }

        if (auth != null) {
            getCommand("login").setExecutor(new AuthCommand(this, auth, AuthCommand.Mode.LOGIN));
            getCommand("register").setExecutor(new AuthCommand(this, auth, AuthCommand.Mode.REGISTER));
            getCommand("changepassword").setExecutor(new AuthCommand(this, auth, AuthCommand.Mode.CHANGE));
            AuthAdminCommand authAdmin = new AuthAdminCommand(this, auth);
            getCommand("auth").setExecutor(authAdmin);
            getCommand("auth").setTabCompleter(authAdmin);
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

        if (getCommand("skin") != null) {
            SkinCommand skinCmd = new SkinCommand(this);
            getCommand("skin").setExecutor(skinCmd);
            getCommand("skin").setTabCompleter(skinCmd);
        }

        if (getCommand("send") != null) {
            SendCommand sendCmd = new SendCommand(this);
            getCommand("send").setExecutor(sendCmd);
            getCommand("send").setTabCompleter(sendCmd);
        }

        ChatToggleCommand chatCmd = new ChatToggleCommand(this);
        getCommand("chat").setExecutor(chatCmd);
        getCommand("chat").setTabCompleter(chatCmd);

        if (getCommand("invrollback") != null && invHistory != null) {
            InventoryHistoryCommand invRollbackCmd = new InventoryHistoryCommand(this);
            getCommand("invrollback").setExecutor(invRollbackCmd);
            getCommand("invrollback").setTabCompleter(invRollbackCmd);
        }

        RepairCommand repairCmd = new RepairCommand(this);
        getCommand("repair").setExecutor(repairCmd);
        getCommand("repair").setTabCompleter(repairCmd);

        getCommand("rename").setExecutor(new RenameCommand(this));

        NickCommand nickCmd = new NickCommand(this);
        getCommand("nick").setExecutor(nickCmd);
        getCommand("nick").setTabCompleter(nickCmd);

        getCommand("link").setExecutor(new LinkCommand(this));

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
        getCommand("stat").setTabCompleter(p0Self);
        getCommand("bal").setTabCompleter(p0Self);
        getCommand("kick").setTabCompleter(p0);
        getCommand("ban").setTabCompleter(p0);
        getCommand("mute").setTabCompleter(p0);
        getCommand("unban").setTabCompleter(p0);
        getCommand("unmute").setTabCompleter(p0);
        getCommand("team").setTabCompleter(new TeamTabCompleter(this));

        getCommand("god").setTabCompleter(new GodCommand(this));
        getCommand("heal").setTabCompleter(new HealCommand(this));
        getCommand("fly").setTabCompleter(new FlyCommand(this));
        getCommand("speed").setTabCompleter(new SpeedCommand(this));

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
        // Persist player-facing state first so a later shutdown exception in a
        // non-critical subsystem can never skip the final inventory save.
        shutdownStep("snapshot online player state", this::snapshotOnlinePlayerState);
        shutdownStep("capture final inventory history", () -> {
            if (invHistory != null) {
                invHistory.snapshotAllOnlineNow(InventoryHistoryManager.Source.SHUTDOWN);
            }
        });
        shutdownStep("save sync data", () -> {
            if (syncManager != null) syncManager.saveAllOnline();
        });
        shutdownStep("save player data", () -> {
            if (players != null) players.saveAll();
        });

        shutdownStep("worth display", () -> {
            if (worthDisplay != null) worthDisplay.shutdown();
        });
        shutdownStep("skins", () -> {
            if (skins != null) skins.stop();
        });
        shutdownStep("auth", () -> {
            if (auth != null) auth.stop();
        });
        shutdownStep("discord bridge", () -> {
            if (discordBridge != null) discordBridge.shutdown();
        });
        shutdownStep("npcs", () -> {
            if (npcs != null) npcs.stop();
        });
        shutdownStep("holograms", () -> {
            if (holograms != null) holograms.stop();
        });
        shutdownStep("gate wand visualizer", () -> {
            if (gateWandViz != null) gateWandViz.stop();
        });
        shutdownStep("gates", () -> {
            if (gates != null) gates.stop();
        });
        shutdownStep("enchant armor", () -> {
            if (enchantArmor != null) enchantArmor.stop();
        });
        shutdownStep("alchemy totem", () -> {
            if (alchemyTotem != null) alchemyTotem.shutdown();
        });
        shutdownStep("voidstones", () -> {
            if (voidstones != null) voidstones.shutdown();
        });
        shutdownStep("spawners", () -> {
            if (spawners != null) spawners.stop();
        });
        shutdownStep("hunted", () -> {
            if (hunted != null) hunted.shutdown();
        });
        shutdownStep("tps reporter", () -> {
            if (tpsReporter != null) tpsReporter.stop();
        });
        shutdownStep("inventory history", () -> {
            if (invHistory != null) invHistory.stop();
        });
        shutdownStep("logs", () -> {
            if (logs != null) logs.stop();
        });
        shutdownStep("database", () -> {
            if (database != null) database.close();
        });
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
        if (lower.endsWith("/ptr") || lower.contains("/ptr/")) return "ptr";
        if (lower.endsWith("/lobby") || lower.contains("/lobby/")) return "lobby";
        return getConfig().getString("server-type", "lobby");
    }

    // Pre-existing getters
    public ServerSelectorGUI getServerSelector() { return serverSelector; }
    public MessageChannel getMessageChannel() { return messageChannel; }
    public SyncManager getSyncManager() { return syncManager; }
    public InventoryHistoryManager invHistory() { return invHistory; }
    public String getServerType() { return serverType; }
    public long getStartedAtMillis() { return startedAtMillis; }
    public boolean isLobby() { return "lobby".equals(serverType); }
    public boolean isPtr() { return "ptr".equals(serverType); }
    public boolean isMainSurvival() { return "survival".equals(serverType); }

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
    public LeaderboardManager leaderboards() { return leaderboards; }
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
    public PhantomToggleManager phantomToggle() { return phantomToggle; }
    public WaypointManager waypoints() { return waypoints; }
    public MessageManager messages() { return messages; }
    public SitManager sit() { return sit; }
    public AfkManager afk() { return afk; }
    public VanishManager vanish() { return vanish; }
    public FullbrightManager fullbright() { return fullbright; }
    public AdminModeManager adminMode() { return adminMode; }
    public ModerationManager moderation() { return moderation; }
    public BountyManager bounties() { return bounties; }
    public HuntedManager hunted() { return hunted; }
    public AlchemyTotemManager alchemyTotem() { return alchemyTotem; }
    public SpawnerManager spawners() { return spawners; }
    public VoidstoneManager voidstones() { return voidstones; }
    public ResourcePackManager resourcePacks() { return resourcePacks; }
    public GateManager gates() { return gates; }
    public CooldownManager cooldowns() { return cooldowns; }
    public AuthManager auth() { return auth; }
    public JoinListener joinListener() { return joinListener; }
    public NpcManager npcs() { return npcs; }
    public HologramManager holograms() { return holograms; }
    public WorthHoverListener worthHover() { return worthHover; }
    public SkinManager skins() { return skins; }
    public GodManager god() { return god; }
    public DiscordBridge discordBridge() { return discordBridge; }

    public boolean isChatLocked() { return chatLocked; }
    public void setChatLocked(boolean locked) { this.chatLocked = locked; }

    private void snapshotOnlinePlayerState() {
        if (players == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.loadOrCreate(player.getUniqueId(), player.getName()).setName(player.getName());
            if (!isMainSurvival()) {
                continue;
            }
            Location loc = player.getLocation();
            if (loc.getWorld() == null) {
                continue;
            }
            // NB: on ne force PAS survivalJoined=true ici. Le JoinListener le
            // met déjà à true après un RTP réussi, et si un joueur vient de
            // mourir sans lit on veut conserver survivalJoined=false pour que
            // son prochain retour déclenche un RTP (géré dans DeathListener).
            players.get(player).setLastLocation(loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }
    }

    private void shutdownStep(String label, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Shutdown step failed: " + label, t);
        }
    }
}
