package fr.smp.ptr.command;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.block.PtrBlock;
import fr.smp.ptr.item.PtrItem;
import fr.smp.ptr.mob.PtrMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PtrCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = Arrays.asList(
            "give", "spawn", "block", "biome", "tour", "setup", "pack", "list", "help");

    private final PtrShowcase plugin;

    public PtrCommand(PtrShowcase plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) return help(sender);
        if (!sender.hasPermission("ptr.use")) {
            sender.sendMessage(Component.text("Permission requise: ptr.use", NamedTextColor.RED));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help" -> help(sender);
            case "list" -> listAll(sender);
            case "give" -> handleGive(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            case "block" -> handleBlock(sender, args);
            case "biome" -> handleBiome(sender, args);
            case "tour" -> handleTour(sender);
            case "setup" -> handleSetup(sender);
            case "pack" -> handlePack(sender);
            default -> help(sender);
        }
        return true;
    }

    private boolean help(CommandSender s) {
        s.sendMessage(Component.text("PTR Showcase commands:", NamedTextColor.AQUA));
        s.sendMessage(Component.text("/showcase give <item>", NamedTextColor.GRAY));
        s.sendMessage(Component.text("/showcase spawn <mob>", NamedTextColor.GRAY));
        s.sendMessage(Component.text("/showcase block <id>", NamedTextColor.GRAY));
        s.sendMessage(Component.text("/showcase biome [name]", NamedTextColor.GRAY));
        s.sendMessage(Component.text("/showcase tour - visite guidee de la plaza", NamedTextColor.GRAY));
        s.sendMessage(Component.text("/showcase setup - construit la plaza autour du joueur", NamedTextColor.GRAY));
        s.sendMessage(Component.text("/showcase list", NamedTextColor.GRAY));
        return true;
    }

    private void listAll(CommandSender s) {
        s.sendMessage(Component.text("Items:", NamedTextColor.AQUA));
        for (PtrItem i : plugin.items().all().values())
            s.sendMessage(Component.text(" - " + i.id() + " (" + i.displayName() + ")", NamedTextColor.GRAY));
        s.sendMessage(Component.text("Blocks:", NamedTextColor.AQUA));
        for (PtrBlock b : plugin.blocks().all().values())
            s.sendMessage(Component.text(" - " + b.id() + " (" + b.displayName() + ")", NamedTextColor.GRAY));
        s.sendMessage(Component.text("Mobs:", NamedTextColor.AQUA));
        for (PtrMob m : plugin.mobs().all().values())
            s.sendMessage(Component.text(" - " + m.id() + " (" + m.displayName() + ")", NamedTextColor.GRAY));
    }

    private void handleGive(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        if (args.length < 2) { p.sendMessage("Usage: /showcase give <id>"); return; }
        PtrItem item = plugin.items().get(args[1]);
        if (item == null) { p.sendMessage(Component.text("Unknown item: " + args[1], NamedTextColor.RED)); return; }
        p.getInventory().addItem(item.create());
        p.sendMessage(Component.text("Donne: " + item.displayName(), NamedTextColor.AQUA));
    }

    private void handleSpawn(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        if (args.length < 2) { p.sendMessage("Usage: /showcase spawn <id>"); return; }
        if (!plugin.mobs().spawn(args[1], p.getTargetBlock(null, 16).getLocation().add(0, 1, 0), p)) {
            p.sendMessage(Component.text("Unknown mob: " + args[1], NamedTextColor.RED));
        }
    }

    private void handleBlock(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        if (args.length < 2) { p.sendMessage("Usage: /showcase block <id>"); return; }
        PtrBlock b = plugin.blocks().get(args[1]);
        if (b == null) { p.sendMessage(Component.text("Unknown block: " + args[1], NamedTextColor.RED)); return; }
        // Give the placeable block-item to the player; they place it by right-clicking
        p.getInventory().addItem(b.createItem());
        p.sendMessage(Component.text("Item bloc donne: " + b.displayName() + ". Clic droit pour poser.",
                NamedTextColor.AQUA));
    }

    private void handleBiome(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        if (args.length < 2) {
            p.sendMessage(Component.text("Usage: /showcase biome <paint|locate> <id> [radius]", NamedTextColor.GRAY));
            p.sendMessage(Component.text("Biomes datapack: ptr:prismatic_dunes, ptr:glitchwood, ptr:abyss_caves",
                    NamedTextColor.AQUA));
            return;
        }
        String mode = args[1].toLowerCase();
        String id = args.length > 2 ? args[2] : "ptr:prismatic_dunes";
        int radius = args.length > 3 ? safeInt(args[3], 32) : 48;
        switch (mode) {
            case "paint" -> plugin.biome().paint(p, id, radius);
            case "locate" -> plugin.biome().locateNearest(p, id, radius * 16);
            default -> p.sendMessage(Component.text("Mode inconnu. paint|locate", NamedTextColor.RED));
        }
    }

    private int safeInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

    private void handleTour(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        plugin.tour().runTour(p);
    }

    private void handleSetup(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        var stops = plugin.tour().setup(p.getLocation(), p);
        p.sendMessage(Component.text("Showcase plaza built (" + stops.size() + " zones).", NamedTextColor.AQUA));
        p.sendMessage(Component.text("Tape /showcase tour pour la visite guidee.", NamedTextColor.GRAY));
    }

    private void handlePack(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        String url = "http://127.0.0.1:8765/ptr_resourcepack.zip";
        try {
            p.setResourcePack(java.util.UUID.fromString("3b731b8c-62b3-4d36-825d-614dc35bca56"),
                    url,
                    new byte[0],
                    Component.text("PTR Showcase pack", NamedTextColor.AQUA),
                    true);
            p.sendMessage(Component.text("Pack envoye: " + url, NamedTextColor.AQUA));
        } catch (Throwable t) {
            try {
                p.setResourcePack(url);
                p.sendMessage(Component.text("Pack envoye (fallback): " + url, NamedTextColor.YELLOW));
            } catch (Throwable t2) {
                p.sendMessage(Component.text("Echec: " + t2.getMessage(), NamedTextColor.RED));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] args) {
        if (args.length == 1) return ROOT.stream().filter(r -> r.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) return new ArrayList<>(plugin.items().all().keySet());
            if (sub.equals("spawn")) return new ArrayList<>(plugin.mobs().all().keySet());
            if (sub.equals("block")) return new ArrayList<>(plugin.blocks().all().keySet());
            if (sub.equals("biome")) return Arrays.asList("paint", "locate");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("biome")) {
            return Arrays.asList("ptr:prismatic_dunes", "ptr:glitchwood", "ptr:abyss_caves");
        }
        return List.of();
    }
}
