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

    public enum Currency {
        MONEY,
        SHARDS;

        public static Currency from(String raw) {
            if (raw == null || raw.isBlank()) return MONEY;
            try {
                return Currency.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return MONEY;
            }
        }
    }

    public record ShopItem(String id, Material material, Material displayMaterial, String displayName,
                           double buyPrice, Currency buyCurrency, double sellPrice, int stack,
                           SpawnerType spawnerType) {}

    public record Category(String id, String displayName, Material icon, int slot,
                           String description, List<ShopItem> items) {}

    private final SMPCore plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private boolean enabled = true;

    public ShopManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean v) {
        this.enabled = v;
        plugin.getConfig().set("shop.enabled", v);
        plugin.saveConfig();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        FileConfiguration c = YamlConfiguration.loadConfiguration(file);
        categories.clear();
        this.enabled = plugin.getConfig().getBoolean("shop.enabled", true);

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
                    ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
                    String materialName = itemSec != null ? itemSec.getString("material", key) : key;
                    Material m = Material.matchMaterial(materialName);
                    if (m == null) continue;
                    SpawnerType spawnerType = itemSec != null
                            ? SpawnerType.fromId(itemSec.getString("spawner-type"))
                            : null;
                    Material displayMaterial = null;
                    if (itemSec != null) {
                        displayMaterial = Material.matchMaterial(itemSec.getString("display-material", ""));
                    }
                    if (displayMaterial == null && spawnerType != null) {
                        displayMaterial = spawnerType.icon();
                    }
                    if (displayMaterial == null) displayMaterial = m;

                    double buy = itemSec != null ? itemSec.getDouble("buy", -1) : itemsSec.getDouble(key + ".buy", -1);
                    double sell = itemSec != null ? itemSec.getDouble("sell", -1) : itemsSec.getDouble(key + ".sell", -1);
                    int amount = itemSec != null ? itemSec.getInt("stack", 1) : itemsSec.getInt(key + ".stack", 1);
                    String itemName = itemSec != null ? itemSec.getString("name") : null;
                    Currency currency = itemSec != null
                            ? Currency.from(itemSec.getString("currency", "MONEY"))
                            : Currency.MONEY;
                    items.add(new ShopItem(
                            key,
                            m,
                            displayMaterial,
                            itemName,
                            buy,
                            currency,
                            sell,
                            Math.max(1, amount),
                            spawnerType
                    ));
                }
            }
            categories.put(id, new Category(id, name, icon, slot, desc, items));
        }
        plugin.getLogger().info("Shop loaded: " + categories.size() + " categories.");
    }

    public Map<String, Category> categories() { return categories; }
    public Category category(String id) { return categories.get(id); }
}
