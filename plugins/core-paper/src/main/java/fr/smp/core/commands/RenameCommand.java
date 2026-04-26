package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RenameCommand implements CommandExecutor {

    private final SMPCore plugin;

    public RenameCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            p.sendMessage(Msg.err("Tu dois tenir un item en main."));
            return true;
        }

        if (args.length == 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                meta.displayName(null);
                item.setItemMeta(meta);
                p.sendMessage(Msg.ok("<gray>Nom de l'item réinitialisé.</gray>"));
            } else {
                p.sendMessage(Msg.err("Cet item n'a pas de nom custom."));
            }
            return true;
        }

        String rawName = String.join(" ", args);
        Component name = MiniMessage.miniMessage().deserialize(rawName);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            p.sendMessage(Msg.err("Impossible de modifier cet item."));
            return true;
        }
        meta.displayName(name);
        item.setItemMeta(meta);

        p.sendMessage(Msg.ok("<gray>Item renommé en </gray>" + name));
        return true;
    }
}
