package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.EventWorldManager;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EventWorldCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("create", "tp", "list", "setwarp", "platform", "spawn");
    private final SMPCore plugin;

    public EventWorldCommand(SMPCore plugin) {
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
            case "create" -> handleCreate(sender, args);
            case "tp" -> handleTp(sender, args);
            case "list" -> handleList(sender);
            case "setwarp" -> handleSetWarp(sender, args);
            case "platform" -> handlePlatform(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Msg.info("<aqua>/eventworld</aqua> <gray>- mondes isolés pour events</gray>"));
        sender.sendMessage(Msg.mm("<gray> • <white>/eventworld create <nom> <void|flat></white>"));
        sender.sendMessage(Msg.mm("<gray> • <white>/eventworld tp <nom></white>"));
        sender.sendMessage(Msg.mm("<gray> • <white>/eventworld platform <nom> [radius] [material]</white>"));
        sender.sendMessage(Msg.mm("<gray> • <white>/eventworld setwarp <warp> <nom></white>"));
        sender.sendMessage(Msg.mm("<gray> • <white>/eventworld spawn <nom></white>"));
        sender.sendMessage(Msg.mm("<gray> • <white>/eventworld list</white>"));
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/eventworld create <nom> <void|flat>"));
            return;
        }

        EventWorldManager.Type type = EventWorldManager.Type.parse(args[2]);
        EventWorldManager.EventWorld eventWorld = plugin.eventWorlds().create(args[1], type);
        if (eventWorld == null) {
            sender.sendMessage(Msg.err("Impossible de créer ce monde."));
            return;
        }

        sender.sendMessage(Msg.ok("Monde <aqua>" + eventWorld.worldName() + "</aqua> créé en mode <white>"
                + type.name().toLowerCase(Locale.ROOT) + "</white>."));
        sender.sendMessage(Msg.info("<gray>TP: <white>/eventworld tp " + eventWorld.id()
                + "</white> | Warp: <white>/eventworld setwarp event " + eventWorld.id() + "</white></gray>"));
        plugin.logs().log(LogCategory.ADMIN, sender instanceof Player p ? p : null,
                "eventworld create " + eventWorld.worldName() + " " + type.name().toLowerCase(Locale.ROOT));
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Joueurs uniquement.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Msg.err("/eventworld tp <nom>"));
            return;
        }

        EventWorldManager.EventWorld eventWorld = plugin.eventWorlds().get(args[1]);
        if (eventWorld == null) {
            player.sendMessage(Msg.err("Monde event introuvable."));
            return;
        }

        World world = Bukkit.getWorld(eventWorld.worldName());
        if (world == null) {
            player.sendMessage(Msg.err("Monde event non chargé."));
            return;
        }

        Location spawn = eventWorld.spawn();
        if (spawn == null) spawn = new Location(world, 0.5, 64.0, 0.5);
        player.teleportAsync(spawn);
        player.sendMessage(Msg.ok("Téléportation vers <aqua>" + eventWorld.worldName() + "</aqua>."));
    }

    private void handleList(CommandSender sender) {
        var worlds = plugin.eventWorlds().all();
        if (worlds.isEmpty()) {
            sender.sendMessage(Msg.info("<gray>Aucun monde event.</gray>"));
            return;
        }

        sender.sendMessage(Msg.info("<aqua>Mondes event (" + worlds.size() + ")</aqua>"));
        for (EventWorldManager.EventWorld eventWorld : worlds) {
            sender.sendMessage(Msg.mm("<gray> • <white>" + eventWorld.id() + "</white> <dark_gray>("
                    + eventWorld.worldName() + ", " + eventWorld.type().name().toLowerCase(Locale.ROOT)
                    + ")</dark_gray>"));
        }
    }

    private void handleSetWarp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Msg.err("/eventworld setwarp <warp> <nom>"));
            return;
        }

        EventWorldManager.EventWorld eventWorld = plugin.eventWorlds().get(args[2]);
        if (eventWorld == null) {
            sender.sendMessage(Msg.err("Monde event introuvable."));
            return;
        }

        Location spawn = eventWorld.spawn();
        if (spawn == null) {
            sender.sendMessage(Msg.err("Spawn du monde event introuvable."));
            return;
        }

        plugin.warps().set(args[1], spawn, Material.FIREWORK_ROCKET,
                "Event world: " + eventWorld.id(), sender instanceof Player p ? p.getUniqueId() : null);
        sender.sendMessage(Msg.ok("Warp <aqua>" + args[1] + "</aqua> créé vers <white>"
                + eventWorld.worldName() + "</white>."));
    }

    private void handlePlatform(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Msg.err("/eventworld platform <nom> [radius] [material]"));
            return;
        }

        EventWorldManager.EventWorld eventWorld = plugin.eventWorlds().get(args[1]);
        if (eventWorld == null) {
            sender.sendMessage(Msg.err("Monde event introuvable."));
            return;
        }

        int radius = 2;
        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Msg.err("Rayon invalide."));
                return;
            }
        }

        Material material = Material.BARRIER;
        if (args.length >= 4) {
            Material parsed = Material.matchMaterial(args[3]);
            if (parsed == null || !parsed.isBlock()) {
                sender.sendMessage(Msg.err("Matériau invalide."));
                return;
            }
            material = parsed;
        }

        boolean ok = plugin.eventWorlds().createPlatform(eventWorld, material, radius);
        sender.sendMessage(ok
                ? Msg.ok("Plateforme créée dans <aqua>" + eventWorld.worldName() + "</aqua>.")
                : Msg.err("Impossible de créer la plateforme."));
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Joueurs uniquement.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Msg.err("/eventworld spawn <nom>"));
            return;
        }

        EventWorldManager.EventWorld eventWorld = plugin.eventWorlds().get(args[1]);
        if (eventWorld == null || player.getWorld() == null
                || !player.getWorld().getName().equalsIgnoreCase(eventWorld.worldName())) {
            player.sendMessage(Msg.err("Tu dois être dans ce monde event."));
            return;
        }

        Location loc = player.getLocation();
        player.getWorld().setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        player.sendMessage(Msg.ok("Spawn de <aqua>" + eventWorld.worldName() + "</aqua> défini ici."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smp.admin")) return List.of();
        if (args.length == 1) return match(SUBS, args[0]);
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("create")) return List.of("course");
            if (sub.equals("tp") || sub.equals("platform") || sub.equals("spawn")) return eventNames(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) return List.of("void", "flat");
        if (args.length == 3 && args[0].equalsIgnoreCase("setwarp")) return eventNames(args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("platform")) return List.of("BARRIER", "GLASS", "BEDROCK");
        return List.of();
    }

    private List<String> eventNames(String prefix) {
        ArrayList<String> out = new ArrayList<>();
        String pref = prefix.toLowerCase(Locale.ROOT);
        for (EventWorldManager.EventWorld eventWorld : plugin.eventWorlds().all()) {
            if (eventWorld.id().startsWith(pref)) out.add(eventWorld.id());
        }
        return out;
    }

    private static List<String> match(List<String> values, String prefix) {
        ArrayList<String> out = new ArrayList<>();
        String pref = prefix.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.startsWith(pref)) out.add(value);
        }
        return out;
    }
}
