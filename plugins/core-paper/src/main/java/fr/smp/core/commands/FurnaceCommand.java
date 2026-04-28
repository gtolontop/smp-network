package fr.smp.core.commands;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public class FurnaceCommand implements CommandExecutor {

    private final SMPCore plugin;

    public FurnaceCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smp.admin")) {
            sender.sendMessage(Msg.err("Permission refusée."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Joueurs uniquement.");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.isEmpty()) {
            player.sendMessage(Msg.err("Tu dois tenir un item en main."));
            return true;
        }

        ItemStack result = findSmeltResult(hand);
        if (result == null) {
            player.sendMessage(Msg.err("Cet item ne peut pas être cuit fondu."));
            return true;
        }

        int amount = hand.getAmount();
        result.setAmount(amount);
        player.getInventory().setItemInMainHand(result);
        player.sendMessage(Msg.ok("<gray><yellow>" + amount + "x</yellow> " + displayName(hand) + " <green>cuit(s)</green> en <yellow>" + displayName(result) + "</yellow>.</gray>"));
        return true;
    }

    private ItemStack findSmeltResult(ItemStack input) {
        java.util.Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof FurnaceRecipe furnace) {
                if (furnace.getInput().getType() == input.getType()) {
                    return furnace.getResult().clone();
                }
            }
        }
        return null;
    }

    private String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            net.kyori.adventure.text.Component comp = item.getItemMeta().displayName();
            if (comp != null) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
            }
        }
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
