package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {

    public record ShopItem(Material material, double buyPrice, double sellPrice, int stack) {}

    public record Category(String id, String displayName, Material icon, int slot,
                           String description, List<ShopItem> items) {}

    private final SMPCore plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public ShopManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        FileConfiguration c = YamlConfiguration.loadConfiguration(file);
        categories.clear();

        ConfigurationSection cats = c.getConfigurationSection("categories");
        if (cats == null) return;
        for (String id : cats.getKeys(false)) {
            ConfigurationSection sec = cats.getConfigurationSection(id);
            if (sec == null) continue;
            Material icon = Material.matchMaterial(sec.getString("icon", "STONE"));
            if (icon == null) icon = Material.STONE;
            int slot = sec.getInt("slot", 0);
            String name = sec.getString("name", id);
            String desc = sec.getString("description", "");
            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemsSec = sec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String key : itemsSec.getKeys(false)) {
                    Material m = Material.matchMaterial(key);
                    if (m == null) continue;
                    double buy = itemsSec.getDouble(key + ".buy", -1);
                    double sell = itemsSec.getDouble(key + ".sell", -1);
                    int amount = itemsSec.getInt(key + ".stack", 1);
                    items.add(new ShopItem(m, buy, sell, amount));
                }
            }
            categories.put(id, new Category(id, name, icon, slot, desc, items));
        }
        plugin.getLogger().info("Shop loaded: " + categories.size() + " categories.");
    }

    public Map<String, Category> categories() { return categories; }
    public Category category(String id) { return categories.get(id); }
}
