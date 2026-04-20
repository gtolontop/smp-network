package fr.smp.anticheat.config;

import fr.smp.anticheat.movement.MovementProfile;
import fr.smp.anticheat.xray.XrayProfile;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class AntiCheatConfig {

    private final JavaPlugin plugin;

    private boolean xrayEnabled;
    private List<Material> overworldReplacements;
    private List<Material> netherReplacements;
    private List<Material> endReplacements;
    private XrayProfile defaultXray;
    private XrayProfile netherXray;
    private XrayProfile endXray;

    private boolean containersEnabled;
    private Set<NamespacedKey> hiddenBlockEntityTypes;
    private double containerRevealDistance;

    private boolean entityEspEnabled;
    private double entityHideDistance;
    private Set<String> alwaysVisibleEntities;
    private int entityCheckIntervalTicks;

    private boolean movementEnabled;
    private MovementProfile defaultMovement;
    private MovementProfile netherMovement;
    private MovementProfile endMovement;

    private int maxRaytracePerPlayerPerTick;
    private long cacheTtlMs;
    private int xrayReconcileTicks;

    public AntiCheatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var c = plugin.getConfig();

        // xray
        var xray = c.getConfigurationSection("xray");
        xrayEnabled = xray != null && xray.getBoolean("enabled", true);
        var rep = xray != null ? xray.getConfigurationSection("replacement-blocks") : null;
        overworldReplacements = parseMaterialList(rep, "overworld", List.of(Material.STONE, Material.DEEPSLATE));
        netherReplacements = parseMaterialList(rep, "nether", List.of(Material.NETHERRACK));
        endReplacements = parseMaterialList(rep, "end", List.of(Material.END_STONE));

        Set<Material> baseHidden = parseMaterials(xray, "hidden-blocks");
        defaultXray = readXrayProfile(xray, baseHidden, null);
        netherXray = readXrayProfile(
                xray != null ? xray.getConfigurationSection("nether-overrides") : null,
                baseHidden, defaultXray);
        endXray = readXrayProfile(
                xray != null ? xray.getConfigurationSection("end-overrides") : null,
                baseHidden, defaultXray);

        // containers
        var cont = c.getConfigurationSection("containers");
        containersEnabled = cont != null && cont.getBoolean("enabled", true);
        hiddenBlockEntityTypes = new HashSet<>();
        if (cont != null) {
            for (String s : cont.getStringList("hidden-types")) {
                hiddenBlockEntityTypes.add(NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT)));
            }
        }
        containerRevealDistance = cont != null ? cont.getDouble("reveal-distance", 6.0) : 6.0;

        // entity esp
        var ent = c.getConfigurationSection("entity-esp");
        entityEspEnabled = ent != null && ent.getBoolean("enabled", true);
        entityHideDistance = ent != null ? ent.getDouble("hide-distance", 12.0) : 12.0;
        alwaysVisibleEntities = new HashSet<>();
        if (ent != null) {
            for (String s : ent.getStringList("always-visible")) {
                alwaysVisibleEntities.add(s.toLowerCase(Locale.ROOT));
            }
        }
        entityCheckIntervalTicks = ent != null ? ent.getInt("check-interval-ticks", 10) : 10;

        // movement
        var mv = c.getConfigurationSection("movement");
        movementEnabled = mv != null && mv.getBoolean("enabled", true);
        defaultMovement = readMovementProfile(mv, null);
        netherMovement = readMovementProfile(
                mv != null ? mv.getConfigurationSection("nether-overrides") : null, defaultMovement);
        endMovement = readMovementProfile(
                mv != null ? mv.getConfigurationSection("end-overrides") : null, defaultMovement);

        // raytrace
        var rt = c.getConfigurationSection("raytrace");
        maxRaytracePerPlayerPerTick = rt != null ? rt.getInt("max-per-player-per-tick", 64) : 64;
        cacheTtlMs = rt != null ? rt.getLong("cache-ttl-ms", 750L) : 750L;
        xrayReconcileTicks = rt != null ? rt.getInt("tick-reveal-interval", 4) : 4;
    }

    private Set<Material> parseMaterials(ConfigurationSection sec, String key) {
        var out = EnumSet.noneOf(Material.class);
        if (sec == null) return out;
        for (String s : sec.getStringList(key)) {
            Material m = Material.matchMaterial(s);
            if (m != null) out.add(m);
        }
        return out;
    }

    private List<Material> parseMaterialList(ConfigurationSection sec, String key, List<Material> fallback) {
        if (sec == null) return fallback;
        List<Material> out = new ArrayList<>();
        for (String s : sec.getStringList(key)) {
            Material m = Material.matchMaterial(s);
            if (m != null) out.add(m);
        }
        return out.isEmpty() ? fallback : out;
    }

    public boolean xrayEnabled() { return xrayEnabled; }
    public List<Material> overworldReplacements() { return overworldReplacements; }
    public List<Material> netherReplacements() { return netherReplacements; }
    public List<Material> endReplacements() { return endReplacements; }

    public XrayProfile xrayProfile(World.Environment env) {
        return switch (env) {
            case NETHER -> netherXray;
            case THE_END -> endXray;
            default -> defaultXray;
        };
    }

    private XrayProfile readXrayProfile(ConfigurationSection sec, Set<Material> baseHidden, XrayProfile fallback) {
        boolean useFb = fallback != null;
        boolean enabled = sec != null ? sec.getBoolean("enabled", useFb ? fallback.enabled : true)
                : (useFb ? fallback.enabled : true);
        int maxY = sec != null ? sec.getInt("max-obfuscate-y", useFb ? fallback.maxY : 96)
                : (useFb ? fallback.maxY : 96);
        int minY = sec != null ? sec.getInt("min-obfuscate-y", useFb ? fallback.minY : -64)
                : (useFb ? fallback.minY : -64);
        boolean fakeInj = sec != null ? sec.getBoolean("fake-ore-injection", useFb ? fallback.fakeOreInjection : true)
                : (useFb ? fallback.fakeOreInjection : true);
        double fakeDens = sec != null ? sec.getDouble("fake-ore-density", useFb ? fallback.fakeOreDensity : 0.002)
                : (useFb ? fallback.fakeOreDensity : 0.002);
        double reveal = sec != null ? sec.getDouble("reveal-distance", useFb ? fallback.revealDistance : 6.0)
                : (useFb ? fallback.revealDistance : 6.0);
        boolean maskCave = sec != null ? sec.getBoolean("mask-cave-ores", useFb ? fallback.maskCaveOres : false)
                : (useFb ? fallback.maskCaveOres : false);

        Set<Material> hidden = EnumSet.noneOf(Material.class);
        // Inherit base hidden-blocks from the global xray section.
        hidden.addAll(baseHidden);
        // Inherit fallback's hidden-blocks too (so override profiles add, not replace).
        if (useFb) hidden.addAll(fallback.hiddenBlocks);
        // Override may declare extra-hidden-blocks to ADD to the inherited set.
        if (sec != null) {
            for (String s : sec.getStringList("extra-hidden-blocks")) {
                Material m = Material.matchMaterial(s);
                if (m != null) hidden.add(m);
            }
            // If "hidden-blocks" is given in the override, REPLACE entirely (not merge).
            if (sec.isList("hidden-blocks")) {
                hidden = EnumSet.noneOf(Material.class);
                for (String s : sec.getStringList("hidden-blocks")) {
                    Material m = Material.matchMaterial(s);
                    if (m != null) hidden.add(m);
                }
            }
        }

        return new XrayProfile(enabled, hidden, maxY, minY, fakeInj, fakeDens, reveal, maskCave);
    }

    public boolean containersEnabled() { return containersEnabled; }
    public Set<NamespacedKey> hiddenBlockEntityTypes() { return hiddenBlockEntityTypes; }
    public double containerRevealDistance() { return containerRevealDistance; }

    public boolean entityEspEnabled() { return entityEspEnabled; }
    public double entityHideDistance() { return entityHideDistance; }
    public Set<String> alwaysVisibleEntities() { return alwaysVisibleEntities; }
    public int entityCheckIntervalTicks() { return entityCheckIntervalTicks; }

    public boolean movementEnabled() { return movementEnabled; }
    public MovementProfile movementProfile(World.Environment env) {
        return switch (env) {
            case NETHER -> netherMovement;
            case THE_END -> endMovement;
            default -> defaultMovement;
        };
    }

    private MovementProfile readMovementProfile(ConfigurationSection sec, MovementProfile fallback) {
        boolean useFallback = fallback != null;
        boolean enabled = sec != null ? sec.getBoolean("enabled", useFallback ? fallback.enabled : true)
                : (useFallback ? fallback.enabled : true);

        var nc = sec != null ? sec.getConfigurationSection("noclip") : null;
        boolean noclipEnabled = nc != null ? nc.getBoolean("enabled", useFallback ? fallback.noclipEnabled : true)
                : (useFallback ? fallback.noclipEnabled : true);
        double noclipMaxStep = nc != null ? nc.getDouble("max-step", useFallback ? fallback.noclipMaxStep : 10.0)
                : (useFallback ? fallback.noclipMaxStep : 10.0);

        var sp = sec != null ? sec.getConfigurationSection("speed") : null;
        boolean speedEnabled = sp != null ? sp.getBoolean("enabled", useFallback ? fallback.speedEnabled : true)
                : (useFallback ? fallback.speedEnabled : true);
        double walkBps = sp != null ? sp.getDouble("walk-bps", useFallback ? fallback.speedWalkBps : 6.0)
                : (useFallback ? fallback.speedWalkBps : 6.0);
        double sprintBps = sp != null ? sp.getDouble("sprint-bps", useFallback ? fallback.speedSprintBps : 7.5)
                : (useFallback ? fallback.speedSprintBps : 7.5);
        double sprintJumpBps = sp != null ? sp.getDouble("sprint-jump-bps",
                useFallback ? fallback.speedSprintJumpBps : 11.0)
                : (useFallback ? fallback.speedSprintJumpBps : 11.0);

        var fl = sec != null ? sec.getConfigurationSection("fly") : null;
        boolean flyEnabled = fl != null ? fl.getBoolean("enabled", useFallback ? fallback.flyEnabled : true)
                : (useFallback ? fallback.flyEnabled : true);
        int flyMaxTicks = fl != null ? fl.getInt("max-airborne-ticks", useFallback ? fallback.flyMaxAirborneTicks : 80)
                : (useFallback ? fallback.flyMaxAirborneTicks : 80);

        String action = sec != null ? sec.getString("action", useFallback ? fallback.action : "teleport")
                : (useFallback ? fallback.action : "teleport");
        int threshold = sec != null ? sec.getInt("violation-threshold",
                useFallback ? fallback.violationThreshold : 5)
                : (useFallback ? fallback.violationThreshold : 5);

        return new MovementProfile(enabled, noclipEnabled, noclipMaxStep, speedEnabled,
                walkBps, sprintBps, sprintJumpBps, flyEnabled, flyMaxTicks, action, threshold);
    }

    public int maxRaytracePerPlayerPerTick() { return maxRaytracePerPlayerPerTick; }
    public long cacheTtlMs() { return cacheTtlMs; }
    public int xrayReconcileTicks() { return xrayReconcileTicks; }
}
