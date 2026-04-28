package fr.smp.core.sellstick;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class SellStickManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final NamespacedKey markerKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey[] recipeKeys;

    public SellStickManager(SMPCore plugin) {
        this.markerKey = new NamespacedKey(plugin, "sellstick_marker");
        this.levelKey = new NamespacedKey(plugin, "sellstick_level");
        this.recipeKeys = new NamespacedKey[]{
                new NamespacedKey(plugin, "sellstick_recipe_1"),
                new NamespacedKey(plugin, "sellstick_recipe_2"),
                new NamespacedKey(plugin, "sellstick_recipe_3")
        };
    }

    public void start() {
        for (int level = 1; level <= 3; level++) {
            registerRecipe(level);
        }
    }

    public void shutdown() {
        for (NamespacedKey key : recipeKeys) {
            Bukkit.removeRecipe(key);
        }
    }

    public double multiplier(int level) {
        return switch (level) {
            case 1 -> 1.5;
            case 2 -> 2.0;
            case 3 -> 2.5;
            default -> 1.0;
        };
    }

    public boolean isSellStick(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return false;
        return Byte.valueOf((byte) 1).equals(
                item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE));
    }

    public int getLevel(ItemStack item) {
        if (!isSellStick(item)) return 0;
        Integer lvl = item.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return lvl != null ? Math.max(1, Math.min(3, lvl)) : 1;
    }

    public ItemStack createItem(int level) {
        level = Math.max(1, Math.min(3, level));
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        meta.setEnchantmentGlintOverride(true);

        String tierName = switch (level) {
            case 1 -> "<gradient:#f6d365:#fda085>Sell Stick</gradient>";
            case 2 -> "<gradient:#a8edea:#fed6e3>Sell Stick</gradient>";
            case 3 -> "<gradient:#67e8f9:#a78bfa>Sell Stick</gradient>";
            default -> "<gradient:#f6d365:#fda085>Sell Stick</gradient>";
        };
        meta.displayName(MM.deserialize("<!italic><bold>" + tierName + " " + roman(level) + "</bold>"));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        double mult = multiplier(level);
        lore.add(MM.deserialize("<!italic><gray>Vend le contenu d'un coffre</gray>").decoration(TextDecoration.ITALIC, false));
        lore.add(MM.deserialize("<!italic><gray>au <yellow>x" + mult + "</yellow> du prix normal.</gray>").decoration(TextDecoration.ITALIC, false));
        lore.add(MM.deserialize("<!italic> "));
        lore.add(MM.deserialize("<!italic><dark_gray>Clic droit sur un coffre pour vendre.</dark_gray>").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        item.setAmount(1);
        return item;
    }

    private void registerRecipe(int level) {
        NamespacedKey key = recipeKeys[level - 1];
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, createItem(level));
        switch (level) {
            case 1 -> {
                recipe.shape(" GS", " SG", "S  ");
                recipe.setIngredient('G', Material.GOLD_NUGGET);
                recipe.setIngredient('S', Material.STICK);
            }
            case 2 -> {
                recipe.shape(" GS", " SG", "S  ");
                recipe.setIngredient('G', Material.GOLD_INGOT);
                recipe.setIngredient('S', Material.STICK);
            }
            case 3 -> {
                recipe.shape(" GS", " SG", "S  ");
                recipe.setIngredient('G', Material.GOLD_BLOCK);
                recipe.setIngredient('S', Material.STICK);
            }
        }
        Bukkit.addRecipe(recipe);
    }

    private String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }
}
