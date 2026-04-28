package fr.smp.core.duels;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Admin-only orchestration of duel arena templates. Workflow:
 *
 *   1. /duelarena worldcreate <name> [void]       — make a fresh template world
 *   2. /duelarena worldtp <name>                  — go build inside it
 *   3. /duelarena wand                            — get the setup hoe
 *   4. left-click center, right-click edge        — sets center + radius
 *   5. /duelarena create <name>                   — persist the arena
 *   6. /duelarena addspawn <name> (×2 per pair)   — alternating A/B per match slot
 *   7. /duelarena info <name>                     — verify
 */
public class DuelArenaCommand implements CommandExecutor, TabCompleter {

    public static final class WandSession {
        public Location center;
        public Integer floorY;
        public Double radius;
    }

    private static final Map<UUID, WandSession> SESSIONS = new HashMap<>();

    public static WandSession session(Player p) {
        return SESSIONS.computeIfAbsent(p.getUniqueId(), u -> new WandSession());
    }

    private static final List<String> SUBS = List.of(
            "wand", "worldcreate", "worldtp", "worldlist",
            "create", "delete", "list", "info", "tp",
            "setcenter", "setradius", "setfloor", "setdig", "setceiling",
            "addspawn", "clearspawns",
            "enable", "disable");

    private final SMPCore plugin;

    public DuelArenaCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> handleWand(sender);
            case "worldcreate" -> handleWorldCreate(sender, args);
            case "worldtp" -> handleWorldTp(sender, args);
            case "worldlist" -> handleWorldList(sender);
            case "create" -> handleCreate(sender, args);
            case "delete", "del", "remove" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "tp" -> handleTp(sender, args);
            case "setcenter" -> handleSetCenter(sender, args);
            case "setradius" -> handleSetRadius(sender, args);
            case "setfloor" -> handleSetFloor(sender, args);
            case "setdig" -> handleSetDig(sender, args);
            case "setceiling" -> handleSetCeiling(sender, args);
            case "addspawn" -> handleAddSpawn(sender, args);
            case "clearspawns" -> handleClearSpawns(sender, args);
            case "enable" -> handleEnable(sender, args, true);
            case "disable" -> handleEnable(sender, args, false);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.info("<gold>/duelarena</gold> <gray>— gestion des arènes de duel</gray>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena wand</white> — pioche de sélection (centre + bord)"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena worldcreate <nom> [void]</white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena worldtp <nom></white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena worldlist</white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena create <nom></white> — utilise wand ou ta position"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena setcenter|setradius|setfloor|setdig|setceiling</white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena addspawn <nom></white> — alterne A/B (paire = 1 match)"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena clearspawns <nom></white>"));
        s.sendMessage(Msg.mm("<gray> • <white>/duelarena info|list|tp|delete|enable|disable</white>"));
    }

    /* ----------------------------- Wand ----------------------------- */

    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return;
        }
        p.getInventory().addItem(DuelArenaListener.createWand());
        p.sendMessage(Msg.ok("Pioche <gold>Duel Arena Setup</gold> reçue. <gray>clic-gauche = centre, clic-droit = bord.</gray>"));
    }

    /* ----------------------------- World CRUD ----------------------------- */

    @SuppressWarnings("removal") // GameRule constants will be replaced by registry lookups in a future Paper release.
    private void handleWorldCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("/duelarena worldcreate <nom> [void]"));
            return;
        }
        String name = "duel_" + sanitize(args[1]);
        if (Bukkit.getWorld(name) != null) {
            sender.sendMessage(Msg.err("Un monde nommé <white>" + name + "</white> existe déjà."));
            return;
        }
        boolean voidGen = args.length >= 3 && args[2].equalsIgnoreCase("void");

        WorldCreator wc = new WorldCreator(name);
        wc.type(WorldType.FLAT);
        wc.generateStructures(false);
        if (voidGen) {
            wc.generator(new VoidGenerator());
        }
        World w = wc.createWorld();
        if (w == null) {
            sender.sendMessage(Msg.err("Impossible de créer le monde."));
            return;
        }
        // Make the build comfortable: peaceful + always day, no weather nags.
        w.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
        w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        w.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);

        sender.sendMessage(Msg.ok("Monde <white>" + name + "</white> créé. <gray>/duelarena worldtp " + args[1] + "</gray>"));
        plugin.logs().log(LogCategory.ADMIN, sender instanceof Player p ? p : null,
                "duelarena worldcreate " + name + (voidGen ? " (void)" : ""));
    }

    private void handleWorldTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (args.length < 2) {
            p.sendMessage(Msg.err("/duelarena worldtp <nom>"));
            return;
        }
        String name = "duel_" + sanitize(args[1]);
        World w = Bukkit.getWorld(name);
        if (w == null) {
            p.sendMessage(Msg.err("Monde <white>" + name + "</white> introuvable."));
            return;
        }
        Location spawn = w.getSpawnLocation();
        // VoidGenerator means spawn could be at y=0 in the void — push the admin
        // up to a safe altitude so they don't immediately fall out.
        if (spawn.getY() < 64) spawn.setY(64);
        p.teleportAsync(spawn);
        p.setGameMode(org.bukkit.GameMode.CREATIVE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.sendMessage(Msg.ok("TP vers <white>" + name + "</white>."));
    }

    private void handleWorldList(CommandSender sender) {
        sender.sendMessage(Msg.info("<gold>Mondes de duel chargés</gold>"));
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().startsWith("duel_") || w.getName().startsWith("match_")) {
                sender.sendMessage(Msg.mm("<gray> • <white>" + w.getName() + "</white> <dark_gray>(" +
                        w.getPlayers().size() + " joueurs)</dark_gray>"));
            }
        }
    }

    /* ----------------------------- Arena CRUD ----------------------------- */

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (args.length < 2) {
            p.sendMessage(Msg.err("/duelarena create <nom>"));
            return;
        }
        String name = args[1];
        if (!name.matches("[a-zA-Z0-9_\\-]{1,32}")) {
            p.sendMessage(Msg.err("Nom invalide (1-32 caractères, a-z 0-9 _ -)."));
            return;
        }
        if (plugin.duelArenas().exists(name)) {
            p.sendMessage(Msg.err("Une arène avec ce nom existe déjà."));
            return;
        }
        WandSession s = session(p);
        Location center = s.center != null ? s.center : p.getLocation();
        DuelArena a = plugin.duelArenas().create(name, center);
        if (a == null) {
            p.sendMessage(Msg.err("Création échouée."));
            return;
        }
        if (s.radius != null) a.setRadius(s.radius);
        if (s.floorY != null) a.setFloorY(s.floorY);
        plugin.duelArenas().save(a);
        p.sendMessage(Msg.ok("Arène <gold>" + a.name() + "</gold> créée. " +
                "<gray>centre=" + fmtLoc(a.center()) + " r=" + a.radius() + " floor=" + a.floorY() + "</gray>"));
        p.sendMessage(Msg.mm("<gray>Ajoute des spawns avec <white>/duelarena addspawn " + a.name() + "</white> (×2 par paire).</gray>"));
        plugin.logs().log(LogCategory.ADMIN, p, "duelarena create " + a.name());
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Msg.err("/duelarena delete <nom>")); return; }
        if (plugin.duelArenas().delete(args[1])) {
            sender.sendMessage(Msg.ok("Arène supprimée."));
        } else {
            sender.sendMessage(Msg.err("Arène introuvable."));
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(Msg.info("<gold>Arènes de duel</gold> <gray>(" + plugin.duelArenas().all().size() + ")</gray>"));
        for (DuelArena a : plugin.duelArenas().all()) {
            int slots = plugin.duelArenas().usableSpawns(a).size();
            sender.sendMessage(Msg.mm("<gray> • <white>" + a.name() + "</white> " +
                    (a.enabled() ? "<green>●</green>" : "<red>●</red>") +
                    " <dark_gray>" + a.world() + " r=" + a.radius() + " " + slots + " slot(s)</dark_gray>"));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Msg.err("/duelarena info <nom>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        sender.sendMessage(Msg.info("<gold>Arène " + a.name() + "</gold> " +
                (a.enabled() ? "<green>(active)</green>" : "<red>(désactivée)</red>")));
        sender.sendMessage(Msg.mm("<gray> monde: <white>" + a.world() + "</white></gray>"));
        sender.sendMessage(Msg.mm("<gray> centre: <white>" + a.centerX() + ", " + a.centerY() + ", " + a.centerZ() +
                "</white> rayon <white>" + a.radius() + "</white></gray>"));
        sender.sendMessage(Msg.mm("<gray> floor Y=<white>" + a.floorY() + "</white> dig=<white>" +
                a.digDepth() + "</white> ceiling=<white>" + a.ceiling() + "</white></gray>"));
        sender.sendMessage(Msg.mm("<gray> spawns: <white>" + plugin.duelArenas().usableSpawns(a).size() +
                " paire(s)</white> <dark_gray>(" + a.spawns().size() + " brutes)</dark_gray></gray>"));
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (args.length < 2) { p.sendMessage(Msg.err("/duelarena tp <nom>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { p.sendMessage(Msg.err("Arène introuvable.")); return; }
        Location c = a.center();
        if (c == null || c.getWorld() == null) {
            p.sendMessage(Msg.err("Monde template <white>" + a.world() + "</white> non chargé."));
            return;
        }
        p.teleportAsync(c);
    }

    /* ----------------------------- Setters ----------------------------- */

    private void handleSetCenter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (args.length < 2) { p.sendMessage(Msg.err("/duelarena setcenter <nom>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { p.sendMessage(Msg.err("Arène introuvable.")); return; }
        Location l = p.getLocation();
        if (!l.getWorld().getName().equals(a.world())) {
            // Re-bind the arena to whatever world the admin is in — they may
            // have rebuilt the template in a fresh world.
            a.setWorld(l.getWorld().getName());
        }
        a.setCenter(l.getX(), l.getY(), l.getZ());
        plugin.duelArenas().save(a);
        p.sendMessage(Msg.ok("Centre fixé à <white>" + fmtLoc(l) + "</white>."));
    }

    private void handleSetRadius(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(Msg.err("/duelarena setradius <nom> <r>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        double r;
        try { r = Math.max(2.0, Math.min(200.0, Double.parseDouble(args[2]))); }
        catch (NumberFormatException e) { sender.sendMessage(Msg.err("Valeur invalide.")); return; }
        a.setRadius(r);
        plugin.duelArenas().save(a);
        sender.sendMessage(Msg.ok("Rayon = <white>" + r + "</white>."));
    }

    private void handleSetFloor(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Msg.err("/duelarena setfloor <nom> [y]")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        int y;
        if (args.length >= 3) {
            try { y = Integer.parseInt(args[2]); }
            catch (NumberFormatException e) { sender.sendMessage(Msg.err("Y invalide.")); return; }
        } else if (sender instanceof Player p) {
            y = p.getLocation().getBlockY();
        } else { sender.sendMessage(Msg.err("Précise une valeur Y.")); return; }
        a.setFloorY(y);
        plugin.duelArenas().save(a);
        sender.sendMessage(Msg.ok("Floor Y = <white>" + y + "</white>."));
    }

    private void handleSetDig(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(Msg.err("/duelarena setdig <nom> <profondeur>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        int d;
        try { d = Math.max(0, Math.min(64, Integer.parseInt(args[2]))); }
        catch (NumberFormatException e) { sender.sendMessage(Msg.err("Valeur invalide.")); return; }
        a.setDigDepth(d);
        plugin.duelArenas().save(a);
        sender.sendMessage(Msg.ok("Dig depth = <white>" + d + "</white>."));
    }

    private void handleSetCeiling(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(Msg.err("/duelarena setceiling <nom> <h>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        int h;
        try { h = Math.max(4, Math.min(256, Integer.parseInt(args[2]))); }
        catch (NumberFormatException e) { sender.sendMessage(Msg.err("Valeur invalide.")); return; }
        a.setCeiling(h);
        plugin.duelArenas().save(a);
        sender.sendMessage(Msg.ok("Ceiling = <white>" + h + "</white>."));
    }

    private void handleAddSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return; }
        if (args.length < 2) { p.sendMessage(Msg.err("/duelarena addspawn <nom>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { p.sendMessage(Msg.err("Arène introuvable.")); return; }
        if (!p.getWorld().getName().equals(a.world())) {
            p.sendMessage(Msg.err("Tu dois être dans le monde <white>" + a.world() + "</white> pour poser un spawn."));
            return;
        }
        Location l = p.getLocation();
        if (!a.withinCylinderXZ(l.getX(), l.getZ())) {
            p.sendMessage(Msg.err("Position hors du cylindre de l'arène."));
            return;
        }
        plugin.duelArenas().addSpawn(a, l);
        int pairs = plugin.duelArenas().usableSpawns(a).size();
        boolean isA = a.spawns().get(a.spawns().size() - 1).b() == null;
        p.sendMessage(Msg.ok("Spawn <white>" + (isA ? "A" : "B") + "</white> ajouté. " +
                "<gray>Paires complètes: " + pairs + ".</gray>"));
    }

    private void handleClearSpawns(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Msg.err("/duelarena clearspawns <nom>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        plugin.duelArenas().clearSpawns(a);
        sender.sendMessage(Msg.ok("Spawns réinitialisés."));
    }

    private void handleEnable(CommandSender sender, String[] args, boolean enabled) {
        if (args.length < 2) { sender.sendMessage(Msg.err("/duelarena " + (enabled ? "enable" : "disable") + " <nom>")); return; }
        DuelArena a = plugin.duelArenas().get(args[1]);
        if (a == null) { sender.sendMessage(Msg.err("Arène introuvable.")); return; }
        a.setEnabled(enabled);
        plugin.duelArenas().save(a);
        sender.sendMessage(Msg.ok("Arène " + (enabled ? "<green>activée</green>" : "<red>désactivée</red>") + "."));
    }

    /* ----------------------------- Helpers ----------------------------- */

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    private static String fmtLoc(Location l) {
        if (l == null) return "?";
        return String.format("%.1f, %.1f, %.1f", l.getX(), l.getY(), l.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUBS) if (s.startsWith(pref)) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String pref = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("worldcreate") || sub.equals("worldtp")) {
                // World names come from /duelarena worldlist; we suggest from
                // the loaded "duel_*" worlds without their prefix for ergonomics.
                List<String> out = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) {
                    if (w.getName().startsWith("duel_")) {
                        String tail = w.getName().substring(5);
                        if (tail.startsWith(pref)) out.add(tail);
                    }
                }
                return out;
            }
            List<String> out = new ArrayList<>();
            for (DuelArena a : plugin.duelArenas().all()) {
                if (a.name().startsWith(pref)) out.add(a.name());
            }
            return out;
        }
        return List.of();
    }

    /** Generates flat-bottom void chunks. Used when worldcreate gets the [void] flag. */
    public static class VoidGenerator extends ChunkGenerator {
        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            // no-op — leave the chunk filled with air
        }

        @Override
        public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0, 64, 0);
        }
    }
}
