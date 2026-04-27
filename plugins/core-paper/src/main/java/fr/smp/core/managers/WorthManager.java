package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.enchants.CustomEnchant;
import fr.smp.core.enchants.EnchantEngine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WorthManager {

    private static final int MAX_PASSES = 64;

    private record WorthEntry(double value, String source) {}

    private final SMPCore plugin;
    private final Map<Material, Double> worth = new EnumMap<>(Material.class);
    private final Map<Material, WorthEntry> entries = new EnumMap<>(Material.class);
    private final Map<Material, Double> manualBase = new EnumMap<>(Material.class);
    private final Map<Material, Double> overrides = new EnumMap<>(Material.class);
    private final Set<Material> configuredBlocked = EnumSet.noneOf(Material.class);
    private File configFile;
    private File overridesFile;
    private File generatedFile;
    private File missingFile;
    private double recipeMultiplier = 1.0;
    private double roundingStep = 0.01;

    private boolean enchantPricingEnabled = false;
    private final Map<String, Double> vanillaEnchantPrices = new HashMap<>();
    private final Map<String, Double> customEnchantPrices = new HashMap<>();

    public WorthManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), "worth.yml");
        overridesFile = new File(plugin.getDataFolder(), "worth.overrides.yml");
        generatedFile = new File(plugin.getDataFolder(), "worth.generated.yml");
        missingFile = new File(plugin.getDataFolder(), "worth.missing.yml");

        plugin.saveResource("worth.yml", true);
        plugin.saveResource("worth.overrides.yml", true);

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        loadConfiguration(config);
        loadOverridesBackfill();
        loadEnchantPricing();
        recalculate();
        exportGeneratedWorths();
        exportMissingWorths();

        int itemMaterials = 0;
        int resolvedItems = 0;
        int blockedItems = 0;
        for (Material material : Material.values()) {
            if (!isRuntimeItem(material)) continue;
            itemMaterials++;
            if (isBlocked(material)) {
                blockedItems++;
                continue;
            }
            if (worth(material) > 0) {
                resolvedItems++;
            }
        }
        int missingItems = Math.max(0, itemMaterials - resolvedItems - blockedItems);
        plugin.getLogger().info("Worth resolved: " + resolvedItems + "/" + itemMaterials +
                " item materials, blocked=" + blockedItems + ", missing=" + missingItems + ".");
    }

    public double worth(Material material) {
        return roundForDisplay(rawWorth(material));
    }

    public double worth(ItemStack stack) {
        return roundForDisplay(rawWorth(stack));
    }

    public boolean hasWorth(Material material) {
        return worth(material) > 0;
    }

    private double rawWorth(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0;
        Material material = stack.getType();
        int amount = Math.max(1, stack.getAmount());
        double perItem = rawWorth(material);

        if (material == Material.ENCHANTED_BOOK) {
            double bookWorth = bookEnchantWorth(stack);
            if (bookWorth > 0) perItem = bookWorth;
        }

        perItem += itemEnchantWorth(stack);

        if (isShulkerBox(material)) {
            perItem += rawShulkerContents(stack);
        }
        return perItem * amount;
    }

    private double rawShulkerContents(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof BlockStateMeta stateMeta)) return 0;
        if (!(stateMeta.getBlockState() instanceof ShulkerBox shulker)) return 0;

        double total = 0;
        for (ItemStack content : shulker.getInventory().getContents()) {
            total += rawWorth(content);
        }
        return total;
    }

    private boolean isShulkerBox(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    public void set(Material material, double value) {
        if (material == null) return;
        overrides.put(material, value);
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (usesStructuredSchema(config)) {
            config.set("overrides." + material.name(), roundForDisplay(value));
        } else {
            config.set(material.name(), roundForDisplay(value));
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("worth.save: " + e.getMessage());
        }
        recalculate();
        exportGeneratedWorths();
        exportMissingWorths();
    }

    public Map<Material, Double> all() {
        return Collections.unmodifiableMap(worth);
    }

    private void loadConfiguration(FileConfiguration config) {
        worth.clear();
        entries.clear();
        manualBase.clear();
        overrides.clear();
        configuredBlocked.clear();

        recipeMultiplier = normalizeMultiplier(config.getDouble("settings.recipe-multiplier", 1.0));
        roundingStep = normalizeRoundingStep(config.getDouble("settings.rounding-step", 0.01));

        if (usesStructuredSchema(config)) {
            loadValues(config.getConfigurationSection("base-worths"), manualBase);
            loadValues(config.getConfigurationSection("overrides"), overrides);
            loadBlocked(config);
        } else {
            loadLegacyRoot(config, manualBase);
        }
    }

    private void loadOverridesBackfill() {
        if (overridesFile == null || !overridesFile.exists()) return;
        FileConfiguration extra = YamlConfiguration.loadConfiguration(overridesFile);
        if (extra.isConfigurationSection("overrides")) {
            loadValues(extra.getConfigurationSection("overrides"), overrides);
            return;
        }
        loadLegacyRoot(extra, overrides);
    }

    private boolean usesStructuredSchema(FileConfiguration config) {
        return config.isConfigurationSection("base-worths")
                || config.isConfigurationSection("overrides")
                || config.contains("blocked-items")
                || config.isConfigurationSection("settings");
    }

    private void loadValues(ConfigurationSection section, Map<Material, Double> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material == null) continue;
            double value = normalize(section.getDouble(key));
            if (value <= 0) continue;
            target.put(material, value);
        }
    }

    private void loadBlocked(FileConfiguration config) {
        for (String raw : config.getStringList("blocked-items")) {
            Material material = parseMaterial(raw);
            if (material != null) configuredBlocked.add(material);
        }
        ConfigurationSection blockedSection = config.getConfigurationSection("blocked-items");
        if (blockedSection == null) return;
        for (String key : blockedSection.getKeys(false)) {
            if (!blockedSection.getBoolean(key, false)) continue;
            Material material = parseMaterial(key);
            if (material != null) configuredBlocked.add(material);
        }
    }

    private void loadLegacyRoot(FileConfiguration config, Map<Material, Double> target) {
        for (String key : config.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material == null) continue;
            double value = normalize(config.getDouble(key));
            if (value <= 0) continue;
            target.put(material, value);
        }
    }

    private void recalculate() {
        worth.clear();
        entries.clear();

        Set<Material> locked = EnumSet.noneOf(Material.class);
        for (Map.Entry<Material, Double> entry : manualBase.entrySet()) {
            store(entry.getKey(), entry.getValue(), "manual.base", locked);
        }
        for (Map.Entry<Material, Double> entry : overrides.entrySet()) {
            store(entry.getKey(), entry.getValue(), "manual.override", locked);
        }

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            changed |= applyRecipePass(locked);
            changed |= applyAliasPass(locked);
            if (!changed) break;
        }

        for (Map.Entry<Material, Double> entry : overrides.entrySet()) {
            store(entry.getKey(), entry.getValue(), "manual.override", locked);
        }
    }

    private boolean applyRecipePass(Set<Material> locked) {
        boolean changed = false;
        var iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            ItemStack result = recipe.getResult();
            if (result == null) continue;
            Material output = result.getType();
            if (!isRuntimeItem(output) || isBlocked(output) || locked.contains(output)) continue;

            Double cost = switch (recipe) {
                case ShapedRecipe shaped -> shapedRecipeCost(shaped);
                case ShapelessRecipe shapeless -> shapelessRecipeCost(shapeless);
                case FurnaceRecipe furnace -> cookingRecipeCost(furnace);
                case SmokingRecipe smoking -> cookingRecipeCost(smoking);
                case BlastingRecipe blasting -> cookingRecipeCost(blasting);
                case CampfireRecipe campfire -> cookingRecipeCost(campfire);
                case StonecuttingRecipe stonecutting -> stonecuttingCost(stonecutting);
                case SmithingTransformRecipe smithingTransform -> smithingTransformCost(smithingTransform);
                case SmithingRecipe smithing -> smithingCost(smithing);
                case TransmuteRecipe transmute -> transmuteCost(transmute);
                default -> null;
            };
            if (cost == null || cost <= 0) continue;

            double perItem = (cost * recipeMultiplier) / Math.max(1, result.getAmount());
            changed |= storeDerived(output, perItem, "recipe:" + recipe.getClass().getSimpleName() + ":" + recipeKey(recipe));
        }
        return changed;
    }

    private boolean applyAliasPass(Set<Material> locked) {
        boolean changed = false;
        for (Material material : Material.values()) {
            if (!isRuntimeItem(material) || isBlocked(material) || locked.contains(material)) continue;
            DerivedAlias alias = aliasFor(material);
            if (alias == null || alias.value() <= 0) continue;
            changed |= storeDerived(material, alias.value(), alias.source());
        }
        return changed;
    }

    private Double shapedRecipeCost(ShapedRecipe recipe) {
        Map<Character, Integer> counts = new HashMap<>();
        for (String row : recipe.getShape()) {
            for (char symbol : row.toCharArray()) {
                if (symbol == ' ') continue;
                counts.merge(symbol, 1, Integer::sum);
            }
        }

        double total = 0;
        Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        Map<Character, ItemStack> ingredientMap = recipe.getIngredientMap();

        for (Map.Entry<Character, Integer> entry : counts.entrySet()) {
            Double unitCost = null;
            RecipeChoice choice = choiceMap.get(entry.getKey());
            if (choice != null) {
                unitCost = choiceCost(choice, true);
            }
            if (unitCost == null) {
                ItemStack stack = ingredientMap.get(entry.getKey());
                unitCost = stackCost(stack, true);
            }
            if (unitCost == null) return null;
            total += unitCost * entry.getValue();
        }
        return total;
    }

    private Double shapelessRecipeCost(ShapelessRecipe recipe) {
        double total = 0;
        List<RecipeChoice> choices = recipe.getChoiceList();
        if (choices != null && !choices.isEmpty()) {
            for (RecipeChoice choice : choices) {
                Double unitCost = choiceCost(choice, true);
                if (unitCost == null) return null;
                total += unitCost;
            }
            return total;
        }

        for (ItemStack stack : recipe.getIngredientList()) {
            Double unitCost = stackCost(stack, true);
            if (unitCost == null) return null;
            total += unitCost;
        }
        return total;
    }

    private Double cookingRecipeCost(CookingRecipe<?> recipe) {
        RecipeChoice choice = recipe.getInputChoice();
        if (choice != null) return choiceCost(choice, false);
        return stackCost(recipe.getInput(), false);
    }

    private Double stonecuttingCost(StonecuttingRecipe recipe) {
        RecipeChoice choice = recipe.getInputChoice();
        if (choice != null) return choiceCost(choice, false);
        return stackCost(recipe.getInput(), false);
    }

    private Double smithingTransformCost(SmithingTransformRecipe recipe) {
        Double template = choiceCost(recipe.getTemplate(), false);
        Double base = choiceCost(recipe.getBase(), false);
        Double addition = choiceCost(recipe.getAddition(), false);
        if (template == null || base == null || addition == null) return null;
        return template + base + addition;
    }

    private Double smithingCost(SmithingRecipe recipe) {
        Double base = choiceCost(recipe.getBase(), false);
        Double addition = choiceCost(recipe.getAddition(), false);
        if (base == null || addition == null) return null;
        return base + addition;
    }

    private Double transmuteCost(TransmuteRecipe recipe) {
        Double input = choiceCost(recipe.getInput(), true);
        Double material = choiceCost(recipe.getMaterial(), true);
        if (input == null || material == null) return null;
        return input + material;
    }

    private Double choiceCost(RecipeChoice choice, boolean subtractCraftRemainders) {
        if (choice == null) return null;
        double best = Double.MAX_VALUE;

        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            for (Material material : materialChoice.getChoices()) {
                Double candidate = materialCost(material, 1, subtractCraftRemainders);
                if (candidate != null) best = Math.min(best, candidate);
            }
        } else if (choice instanceof RecipeChoice.ItemTypeChoice itemTypeChoice) {
            for (var itemType : itemTypeChoice.itemTypes().resolve(Registry.ITEM)) {
                Double candidate = materialCost(itemType.asMaterial(), 1, subtractCraftRemainders);
                if (candidate != null) best = Math.min(best, candidate);
            }
        } else if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            for (ItemStack stack : exactChoice.getChoices()) {
                Double candidate = stackCost(stack, subtractCraftRemainders);
                if (candidate != null) best = Math.min(best, candidate);
            }
        } else {
            Double candidate = stackCost(choice.getItemStack(), subtractCraftRemainders);
            if (candidate != null) best = Math.min(best, candidate);
        }

        return best == Double.MAX_VALUE ? null : best;
    }

    private Double stackCost(ItemStack stack, boolean subtractCraftRemainders) {
        if (stack == null || stack.getType().isAir()) return null;
        return materialCost(stack.getType(), Math.max(1, stack.getAmount()), subtractCraftRemainders);
    }

    private Double materialCost(Material material, int amount, boolean subtractCraftRemainders) {
        if (material == null) return null;
        double unit = rawWorth(material);
        if (unit <= 0) return null;

        double total = unit * Math.max(1, amount);
        if (subtractCraftRemainders) {
            Material remainder = craftingRemainder(material);
            if (remainder != null) {
                double returned = rawWorth(remainder) * Math.max(1, amount);
                total = Math.max(0, total - returned);
            }
        }
        return total;
    }

    private Material craftingRemainder(Material material) {
        return switch (material) {
            case HONEY_BOTTLE -> Material.GLASS_BOTTLE;
            case LAVA_BUCKET, WATER_BUCKET, MILK_BUCKET, POWDER_SNOW_BUCKET,
                    COD_BUCKET, SALMON_BUCKET, PUFFERFISH_BUCKET, TROPICAL_FISH_BUCKET,
                    AXOLOTL_BUCKET, TADPOLE_BUCKET -> Material.BUCKET;
            default -> null;
        };
    }

    private boolean storeDerived(Material material, double value, String source) {
        if (value <= 0) return false;
        WorthEntry current = entries.get(material);
        if (current == null || value < current.value()) {
            entries.put(material, new WorthEntry(value, source));
            worth.put(material, value);
            return true;
        }
        return false;
    }

    private void store(Material material, double value, String source, Set<Material> locked) {
        if (material == null || value <= 0) return;
        entries.put(material, new WorthEntry(value, source));
        worth.put(material, value);
        locked.add(material);
    }

    private DerivedAlias aliasFor(Material material) {
        String name = material.name();

        DerivedAlias direct = switch (material) {
            case GRASS_BLOCK, DIRT_PATH, FARMLAND, ROOTED_DIRT, PODZOL, MYCELIUM -> sameWorth(material, Material.DIRT);
            case CARVED_PUMPKIN -> sameWorth(material, Material.PUMPKIN);
            case CHIPPED_ANVIL, DAMAGED_ANVIL -> sameWorth(material, Material.ANVIL);
            case INFESTED_COBBLESTONE -> sameWorth(material, Material.COBBLESTONE);
            case INFESTED_DEEPSLATE -> sameWorth(material, Material.DEEPSLATE);
            case INFESTED_STONE -> sameWorth(material, Material.STONE);
            case INFESTED_STONE_BRICKS -> sameWorth(material, Material.STONE_BRICKS);
            case INFESTED_MOSSY_STONE_BRICKS -> sameWorth(material, Material.MOSSY_STONE_BRICKS);
            case INFESTED_CRACKED_STONE_BRICKS -> sameWorth(material, Material.CRACKED_STONE_BRICKS);
            case INFESTED_CHISELED_STONE_BRICKS -> sameWorth(material, Material.CHISELED_STONE_BRICKS);
            case STRIPPED_BAMBOO_BLOCK -> sameWorth(material, Material.BAMBOO_BLOCK);
            case COD_BUCKET -> additiveWorth(material, "alias:bucketed_fish", Material.COD, Material.BUCKET);
            case SALMON_BUCKET -> additiveWorth(material, "alias:bucketed_fish", Material.SALMON, Material.BUCKET);
            case PUFFERFISH_BUCKET -> additiveWorth(material, "alias:bucketed_fish", Material.PUFFERFISH, Material.BUCKET);
            case TROPICAL_FISH_BUCKET -> additiveWorth(material, "alias:bucketed_fish", Material.TROPICAL_FISH, Material.BUCKET);
            default -> null;
        };
        if (direct != null) return direct;

        if (name.endsWith("_CONCRETE")) {
            Material source = parseMaterial(name.replace("_CONCRETE", "_CONCRETE_POWDER"));
            return sameWorth(material, source);
        }

        if (name.startsWith("STRIPPED_")) {
            Material source = parseMaterial(name.substring("STRIPPED_".length()));
            return sameWorth(material, source);
        }

        if (name.startsWith("WAXED_")) {
            Material unwaxed = parseMaterial(name.substring("WAXED_".length()));
            return additiveWorth(material, "alias:waxed", unwaxed, Material.HONEYCOMB);
        }

        if (name.startsWith("EXPOSED_")) {
            return sameWorth(material, parseMaterial(name.substring("EXPOSED_".length())));
        }
        if (name.startsWith("WEATHERED_")) {
            return sameWorth(material, parseMaterial(name.substring("WEATHERED_".length())));
        }
        if (name.startsWith("OXIDIZED_")) {
            return sameWorth(material, parseMaterial(name.substring("OXIDIZED_".length())));
        }

        if (name.endsWith("_BUNDLE") && material != Material.BUNDLE) {
            Material dye = parseMaterial(name.replace("_BUNDLE", "_DYE"));
            return additiveWorth(material, "alias:dyed_bundle", Material.BUNDLE, dye);
        }

        if (name.endsWith("_HARNESS")) {
            Material dye = parseMaterial(name.replace("_HARNESS", "_DYE"));
            Material harness = parseMaterial("HARNESS");
            return additiveWorth(material, "alias:dyed_harness", harness, dye);
        }

        return null;
    }

    private DerivedAlias sameWorth(Material target, Material source) {
        if (source == null) return null;
        double value = rawWorth(source);
        if (value <= 0) return null;
        return new DerivedAlias(value, "alias:same_as:" + source.name());
    }

    private DerivedAlias additiveWorth(Material target, String sourceName, Material... ingredients) {
        double total = 0;
        List<String> parts = new ArrayList<>();
        for (Material ingredient : ingredients) {
            if (ingredient == null) return null;
            double value = rawWorth(ingredient);
            if (value <= 0) return null;
            total += value;
            parts.add(ingredient.name());
        }
        return new DerivedAlias(total, sourceName + ":" + String.join("+", parts));
    }

    private boolean isRuntimeItem(Material material) {
        return material != null && material.isItem() && !material.name().startsWith("LEGACY_");
    }

    private boolean isBlocked(Material material) {
        if (material == null) return true;
        if (manualBase.containsKey(material) || overrides.containsKey(material)) return false;
        if (configuredBlocked.contains(material)) return true;

        String name = material.name();
        if (!isRuntimeItem(material)) return true;
        if (name.endsWith("_SPAWN_EGG")) return true;

        return switch (material) {
            case AIR, CAVE_AIR, VOID_AIR,
                    BARRIER, BEDROCK, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                    COMMAND_BLOCK_MINECART, DEBUG_STICK, JIGSAW, KNOWLEDGE_BOOK, LIGHT,
                    STRUCTURE_BLOCK, STRUCTURE_VOID, TEST_BLOCK, TEST_INSTANCE_BLOCK,
                    PETRIFIED_OAK_SLAB, REINFORCED_DEEPSLATE, BUDDING_AMETHYST,
                    END_PORTAL_FRAME, END_PORTAL, END_GATEWAY,
                    SPAWNER, TRIAL_SPAWNER, VAULT,
                    SUSPICIOUS_SAND, SUSPICIOUS_GRAVEL,
                    PLAYER_HEAD -> true;
            default -> false;
        };
    }

    private void loadEnchantPricing() {
        vanillaEnchantPrices.clear();
        customEnchantPrices.clear();
        enchantPricingEnabled = false;

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("enchant-pricing");
        if (section == null) return;

        enchantPricingEnabled = section.getBoolean("enabled", true);
        if (!enchantPricingEnabled) return;

        ConfigurationSection vanilla = section.getConfigurationSection("vanilla");
        if (vanilla != null) {
            for (String key : vanilla.getKeys(false)) {
                double price = vanilla.getDouble(key, 0);
                if (price > 0) vanillaEnchantPrices.put(key.toUpperCase(Locale.ROOT), price);
            }
        }

        ConfigurationSection custom = section.getConfigurationSection("custom");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                double price = custom.getDouble(key, 0);
                if (price > 0) customEnchantPrices.put(key.toLowerCase(Locale.ROOT), price);
            }
        }

        plugin.getLogger().info("Enchant pricing loaded: " + vanillaEnchantPrices.size() + " vanilla, " +
                customEnchantPrices.size() + " custom enchants.");
    }

    private double itemEnchantWorth(ItemStack stack) {
        if (!enchantPricingEnabled || stack == null || !stack.hasItemMeta()) return 0;
        double total = 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;

        if (stack.getType() != Material.ENCHANTED_BOOK) {
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                String key = entry.getKey().getKey().getKey().toUpperCase(Locale.ROOT);
                double base = vanillaEnchantPrices.getOrDefault(key, 0.0);
                total += base * entry.getValue();
            }
        }

        if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta)) {
            Map<CustomEnchant, Integer> customs = EnchantEngine.customOn(stack);
            for (Map.Entry<CustomEnchant, Integer> entry : customs.entrySet()) {
                double base = customEnchantPrices.getOrDefault(entry.getKey().id(), 0.0);
                total += base * entry.getValue();
            }
        }

        return total;
    }

    private double bookEnchantWorth(ItemStack stack) {
        if (!enchantPricingEnabled || stack == null || stack.getType() != Material.ENCHANTED_BOOK || !stack.hasItemMeta()) return 0;

        EnchantEngine.BookPayload payload = EnchantEngine.readBook(stack);
        if (payload != null) {
            CustomEnchant ce = payload.enchant();
            int level = payload.level();
            if (ce.isOvercap() && ce.vanilla() != null) {
                String key = ce.vanilla().getKey().getKey().toUpperCase(Locale.ROOT);
                double base = vanillaEnchantPrices.getOrDefault(key, 0.0);
                return base * level;
            }
            double base = customEnchantPrices.getOrDefault(ce.id(), 0.0);
            return base * level;
        }

        if (stack.getItemMeta() instanceof EnchantmentStorageMeta esm) {
            double total = 0;
            for (Map.Entry<Enchantment, Integer> entry : esm.getStoredEnchants().entrySet()) {
                String key = entry.getKey().getKey().getKey().toUpperCase(Locale.ROOT);
                double base = vanillaEnchantPrices.getOrDefault(key, 0.0);
                total += base * entry.getValue();
            }
            return total;
        }

        return 0;
    }

    private void exportGeneratedWorths() {
        YamlConfiguration generated = new YamlConfiguration();
        generated.options().header("""
                Generated by SMPCore.
                Do not edit this file by hand: edit worth.yml instead.
                """);
        generated.set("summary.recipe-multiplier", recipeMultiplier);
        generated.set("summary.rounding-step", roundingStep);

        int count = 0;
        for (Material material : Material.values()) {
            if (!isRuntimeItem(material)) continue;
            double value = worth(material);
            if (value <= 0) continue;
            count++;
            generated.set("worths." + material.name(), roundForDisplay(value));
            WorthEntry entry = entries.get(material);
            if (entry != null) {
                generated.set("sources." + material.name(), entry.source());
            }
        }
        generated.set("summary.generated-items", count);

        try {
            generated.save(generatedFile);
        } catch (IOException e) {
            plugin.getLogger().warning("worth.generated.save: " + e.getMessage());
        }
    }

    private void exportMissingWorths() {
        List<String> missing = new ArrayList<>();
        List<String> blocked = new ArrayList<>();

        for (Material material : Material.values()) {
            if (!isRuntimeItem(material)) continue;
            if (isBlocked(material)) {
                blocked.add(material.name());
                continue;
            }
            if (worth(material) <= 0) {
                missing.add(material.name());
            }
        }

        Collections.sort(missing);
        Collections.sort(blocked);

        YamlConfiguration generated = new YamlConfiguration();
        generated.options().header("""
                Generated by SMPCore.
                `missing` contains item materials with no sell worth.
                `blocked` contains items intentionally excluded from the economy.
                """);
        generated.set("summary.missing-count", missing.size());
        generated.set("summary.blocked-count", blocked.size());
        generated.set("missing", missing);
        generated.set("blocked", blocked);

        try {
            generated.save(missingFile);
        } catch (IOException e) {
            plugin.getLogger().warning("worth.missing.save: " + e.getMessage());
        }
    }

    private String recipeKey(Recipe recipe) {
        try {
            return recipe instanceof org.bukkit.Keyed keyed ? keyed.getKey().toString() : "unknown";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private double normalize(double value) {
        if (value <= 0) return 0;
        return value;
    }

    private double normalizeRoundingStep(double value) {
        return value <= 0 ? 0.01 : value;
    }

    private double normalizeMultiplier(double value) {
        return value <= 0 ? 1.0 : value;
    }

    private double rawWorth(Material material) {
        return worth.getOrDefault(material, 0.0);
    }

    private double roundForDisplay(double value) {
        if (value <= 0) return 0;
        return Math.round(value / roundingStep) * roundingStep;
    }

    private Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            Material matched = Material.matchMaterial(raw);
            if (matched != null && !matched.name().startsWith("LEGACY_")) return matched;
            return null;
        }
    }

    private record DerivedAlias(double value, String source) {}
}
