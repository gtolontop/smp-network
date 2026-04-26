package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.gui.AuctionGUI;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AuctionCommand implements CommandExecutor {

    private final SMPCore plugin;

    public AuctionCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Joueurs uniquement."); return true; }
        if (args.length == 0) { new AuctionGUI(plugin).open(p, 0); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell" -> {
                if (args.length < 2) {
                    p.sendMessage(Msg.err("/ah sell <price> <gray>(examples: 1000, 1k, 10k, 1m, 1b)</gray>"));
                    return true;
                }
                double price = Msg.parseAmount(args[1]);
                if (price <= 0) { p.sendMessage(Msg.err("Prix invalide.")); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) { p.sendMessage(Msg.err("Rien en main.")); return true; }
                int current = plugin.auction().countActive(p.getUniqueId());
                int max = plugin.auction().maxPerPlayer();
                if (current >= max) {
                    p.sendMessage(Msg.err("Max " + max + " annonces actives.")); return true;
                }
                long id = plugin.auction().list(p.getUniqueId(), p.getName(), hand.clone(), price);
                if (id < 0) { p.sendMessage(Msg.err("Échec.")); return true; }
                p.getInventory().setItemInMainHand(null);
                p.sendMessage(Msg.ok("<green>Annonce #" + id + " créée pour <yellow>$" + Msg.money(price) + "</yellow>.</green>"));
                plugin.logs().log(LogCategory.AUCTION, p, "sell id=" + id + " price=" + price);
            }
            case "cancel" -> {
                if (args.length < 2) { p.sendMessage(Msg.err("/ah cancel <id>")); return true; }
                long id;
                try { id = Long.parseLong(args[1]); } catch (NumberFormatException e) {
                    p.sendMessage(Msg.err("ID invalide.")); return true;
                }
                var l = plugin.auction().get(id);
                if (l == null || !l.seller().equals(p.getUniqueId())) {
                    p.sendMessage(Msg.err("Pas ton annonce.")); return true;
                }
                plugin.auction().remove(id);
                var overflow = p.getInventory().addItem(l.item().clone());
                overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
                p.sendMessage(Msg.ok("<red>Annonce retirée.</red>"));
            }
            default -> new AuctionGUI(plugin).open(p, 0);
        }
        return true;
    }
}
