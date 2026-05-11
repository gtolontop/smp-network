package fr.smp.ptr.foundation;

import fr.smp.ptr.foundation.api.PtrFoundationApi;
import fr.smp.ptr.foundation.boss.GroundSlamRingTelegraph;
import fr.smp.ptr.foundation.boss.LaserBeamPretraceTelegraph;
import fr.smp.ptr.foundation.boss.LootDispatcher;
import fr.smp.ptr.foundation.boss.MusicOrchestrator;
import fr.smp.ptr.foundation.boss.RingSeismPulseTelegraph;
import fr.smp.ptr.foundation.boss.Telegraph;
import fr.smp.ptr.foundation.boss.VoidPortalSwirlTelegraph;
import fr.smp.ptr.foundation.command.PtrFoundationCommand;
import fr.smp.ptr.foundation.config.PtrConfigService;
import fr.smp.ptr.foundation.config.PtrFoundationConfig;
import fr.smp.ptr.foundation.disguise.DisguiseGuardListener;
import fr.smp.ptr.foundation.disguise.DisplayBlockCarrier;
import fr.smp.ptr.foundation.disguise.DisplayMobCarrier;
import fr.smp.ptr.foundation.disguise.LeafLitterCarrier;
import fr.smp.ptr.foundation.disguise.MushroomCarrier;
import fr.smp.ptr.foundation.disguise.NoteBlockCarrier;
import fr.smp.ptr.foundation.disguise.TripwireCarrier;
import fr.smp.ptr.foundation.model.PtrModelRegistry;
import fr.smp.ptr.foundation.platform.FoliaSchedulerService;
import fr.smp.ptr.foundation.platform.PaperVersionGuard;
import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.registry.PtrBlockRegistry;
import fr.smp.ptr.foundation.registry.PtrDamageTypeRegistry;
import fr.smp.ptr.foundation.registry.PtrEnchantRegistry;
import fr.smp.ptr.foundation.registry.PtrItemRegistry;
import fr.smp.ptr.foundation.registry.PtrMobRegistry;
import fr.smp.ptr.foundation.skill.PtrSkillRegistry;
import fr.smp.ptr.foundation.storage.PtrDatabase;
import fr.smp.ptr.foundation.telemetry.PtrAuditLog;
import fr.smp.ptr.foundation.telemetry.PtrMetrics;
import fr.smp.ptr.foundation.telemetry.PtrTelemetryService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point.
 *
 * <p>Init order matters: each layer depends on the ones above it. Shutdown
 * is LIFO via {@link PtrServices#shutdownAll()}.
 *
 * <ol>
 *   <li>Folia version check (refuse to enable on Paper).
 *   <li>{@link SchedulerService} (depended on by every async / timed service).
 *   <li>{@link PtrConfigService} (config drives the storage + telemetry params).
 *   <li>{@link PtrDatabase} (SQLite, Flyway migrations).
 *   <li>Empty registries (one per content type).
 *   <li>Disguise carriers + guard listener.
 *   <li>{@link PtrMetrics} + {@link PtrTelemetryService} + {@link PtrAuditLog}.
 *   <li>Boss helpers (telegraph catalogue, music, loot).
 *   <li>{@code /ptrf} Brigadier root registered via Paper {@link LifecycleEvents#COMMANDS}.
 * </ol>
 */
public final class PtrFoundationPlugin extends JavaPlugin {

    private PtrServices services;

    @Override
    public void onEnable() {
        PaperVersionGuard.verifyFolia(getLogger());

        services = new PtrServices();
        services.register(SchedulerService.class, new FoliaSchedulerService(this));

        // -- Config ----------------------------------------------------
        PtrConfigService config = new PtrConfigService(getDataFolder().toPath(), getLogger());
        try {
            config.initialise();
        } catch (Exception e) {
            getLogger().severe("PtrFoundation config init failed: " + e.getMessage());
            setEnabled(false);
            return;
        }
        services.register(PtrConfigService.class, config);
        PtrFoundationConfig cfg = config.get();

        // -- Storage ---------------------------------------------------
        PtrDatabase database;
        try {
            database =
                    PtrDatabase.open(
                            getDataFolder().toPath().resolve(cfg.storage().sqliteFile()),
                            cfg.storage().poolSize(),
                            cfg.storage().runMigrations(),
                            getLogger());
        } catch (Exception e) {
            getLogger().severe("PtrFoundation storage init failed: " + e.getMessage());
            setEnabled(false);
            return;
        }
        services.register(PtrDatabase.class, database, PtrDatabase::close);

        // -- Empty registries -----------------------------------------
        services.register(PtrBlockRegistry.class, new PtrBlockRegistry());
        services.register(PtrItemRegistry.class, new PtrItemRegistry());
        services.register(PtrMobRegistry.class, new PtrMobRegistry());
        services.register(PtrEnchantRegistry.class, new PtrEnchantRegistry());
        services.register(PtrDamageTypeRegistry.class, new PtrDamageTypeRegistry());
        services.register(PtrSkillRegistry.class, new PtrSkillRegistry());
        services.register(PtrModelRegistry.class, new PtrModelRegistry());

        // -- Disguise carriers ----------------------------------------
        services.register(NoteBlockCarrier.class, new NoteBlockCarrier());
        services.register(MushroomCarrier.class, new MushroomCarrier());
        services.register(TripwireCarrier.class, new TripwireCarrier());
        services.register(LeafLitterCarrier.class, new LeafLitterCarrier());
        services.register(DisplayBlockCarrier.class, new DisplayBlockCarrier());
        services.register(DisplayMobCarrier.class, new DisplayMobCarrier());
        getServer().getPluginManager().registerEvents(new DisguiseGuardListener(), this);

        // -- Telemetry / audit ----------------------------------------
        PtrMetrics metrics = new PtrMetrics();
        services.register(PtrMetrics.class, metrics);
        PtrTelemetryService telemetry =
                new PtrTelemetryService(
                        metrics,
                        services.get(SchedulerService.class),
                        getLogger(),
                        cfg.telemetry().jsonLogs(),
                        cfg.telemetry().metricDumpIntervalSeconds());
        services.register(PtrTelemetryService.class, telemetry, PtrTelemetryService::stop);
        telemetry.start();

        PtrAuditLog audit =
                new PtrAuditLog(
                        database,
                        services.get(SchedulerService.class),
                        getLogger(),
                        cfg.audit().flushBatchSize(),
                        cfg.audit().flushIntervalSeconds());
        services.register(PtrAuditLog.class, audit, PtrAuditLog::stop);
        audit.start();

        // -- Boss helpers ---------------------------------------------
        List<Telegraph> telegraphs =
                List.of(
                        new GroundSlamRingTelegraph(),
                        new LaserBeamPretraceTelegraph(),
                        new RingSeismPulseTelegraph(),
                        new VoidPortalSwirlTelegraph());
        TelegraphCatalogue catalogue = new TelegraphCatalogue(telegraphs);
        services.register(TelegraphCatalogue.class, catalogue);
        services.register(MusicOrchestrator.class, new MusicOrchestrator());
        services.register(LootDispatcher.class, new LootDispatcher());

        // -- Brigadier /ptrf ------------------------------------------
        PtrFoundationCommand commandRoot = new PtrFoundationCommand(services);
        getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> {
                            event.registrar()
                                    .register(
                                            commandRoot.build(),
                                            "PtrFoundation root command",
                                            java.util.List.of("ptrf"));
                        });

        // -- Reload listener: telemetry / audit honour fresh values ---
        config.addReloadListener(
                fresh ->
                        getLogger()
                                .info(
                                        () ->
                                                "Reload applied: telemetry.json="
                                                        + fresh.telemetry().jsonLogs()
                                                        + " telemetry.intervalSeconds="
                                                        + fresh.telemetry()
                                                                .metricDumpIntervalSeconds()));

        // -- Public API entry point — facade statics resolve through this
        PtrFoundationApi.init(services);

        getLogger()
                .info(
                        () ->
                                "PtrFoundation v"
                                        + getPluginMeta().getVersion()
                                        + " enabled — "
                                        + services.size()
                                        + " services registered");
    }

    @Override
    public void onDisable() {
        PtrFoundationApi.shutdown();
        if (services != null) {
            try {
                services.shutdownAll();
            } catch (Throwable t) {
                getLogger().warning("PtrFoundation shutdown error: " + t.getMessage());
            }
            services = null;
        }
    }

    /** Exposed for tests. */
    public PtrServices services() {
        return services;
    }
}
