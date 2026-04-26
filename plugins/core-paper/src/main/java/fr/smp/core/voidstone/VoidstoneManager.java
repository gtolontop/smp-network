package fr.smp.core.voidstone;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VoidstoneManager {

    public static final int MAX_PER_MATERIAL = 100_000;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int TOOLTIP_MATERIAL_LINES = 5;

    private static final Comparator<Map.Entry<Material, Integer>> ENTRY_ORDER =
            Comparator.<Map.Entry<Material, Integer>>comparingInt(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(e -> e.getKey().name());

    private static final Set<Material> ALLOWED = Collections.unmodifiableSet(EnumSet.of(
            Material.STONE,
            Material.COBBLESTONE,
            Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE,
            Material.NETHERRACK,
            Material.END_STONE,
            Material.BLACKSTONE,
            Material.BASALT,
            Material.SMOOTH_BASALT,
            Material.TUFF,
            Material.CALCITE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.GRAVEL,
            Material.SAND,
            Material.RED_SAND,
            Material.SANDSTONE,
            Material.RED_SANDSTONE,
            Material.MUD,
            Material.CLAY,
            Material.SOUL_SAND,
            Material.SOUL_SOIL,
            Material.MAGMA_BLOCK,
            Material.DRIPSTONE_BLOCK,
            Material.MOSS_BLOCK,
            Material.TERRACOTTA,
            Material.ICE,
            Material.PACKED_ICE
    ));

    private final NamespacedKey markerKey;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey storageKey;
    private final NamespacedKey recipeKey;

    public VoidstoneManager(SMPCore plugin) {
        this.markerKey = new NamespacedKey(plugin, "voidstone_marker");
        this.itemIdKey = new NamespacedKey(plugin, "voidstone_id");
        this.storageKey = new NamespacedKey(plugin, "voidstone_storage");
        this.recipeKey = new NamespacedKey(plugin, "voidstone_recipe");
    }

    public void start() {
        registerRecipe();
    }

    public void shutdown() {
        Bukkit.removeRecipe(recipeKey);
    }

    public boolean isVoidstoneRecipe(Recipe recipe) {
        if (!(recipe instanceof org.bukkit.Keyed keyed)) return false;
        return recipeKey.equals(keyed.getKey());
    }

    public ItemStack createCraftResult() {
        return createItem();
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createCraftResult());
        recipe.shape("DDD", "DED", "DDD");
        recipe.setIngredient('D', Material.DIRT);
        recipe.setIngredient('E', Material.ECHO_SHARD);
        Bukkit.addRecipe(recipe);
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        applyStorage(item, Map.of());
        item.setAmount(1);
        return item;
    }

    public boolean isVoidstone(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) return false;
        return Byte.valueOf((byte) 1).equals(
                item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE));
    }

    public String readId(ItemStack item) {
        if (!isVoidstone(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public boolean isAllowed(Material material) {
        return material != null && ALLOWED.contains(material);
    }

    public List<Material> allowedMaterials() {
        List<Material> materials = new ArrayList<>(ALLOWED);
        materials.sort(Comparator.comparing(Material::name));
        return Collections.unmodifiableList(materials);
    }

    public boolean hasVoidstone(PlayerInventory inventory) {
        return inventory != null && findFirst(inventory.getContents()) != null;
    }

    public Map<Material, Integer> readStorage(ItemStack item) {
        if (!isVoidstone(item)) return new EnumMap<>(Material.class);
        String raw = item.getItemMeta().getPersistentDataContainer().get(storageKey, PersistentDataType.STRING);
        return deserialize(raw);
    }

    public List<Map.Entry<Material, Integer>> snapshot(ItemStack item) {
        List<Map.Entry<Material, Integer>> out = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : readStorage(item).entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                out.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        out.sort(ENTRY_ORDER);
        return Collections.unmodifiableList(out);
    }

    public int storedAmount(ItemStack item, Material material) {
        if (!isAllowed(material)) return 0;
        return readStorage(item).getOrDefault(material, 0);
    }

    public int totalStored(ItemStack item) {
        int total = 0;
        for (int amount : readStorage(item).values()) {
            total += amount;
        }
        return total;
    }

    public int absorbInto(ItemStack item, Material material, int amount) {
        if (!isVoidstone(item) || !isAllowed(material) || amount <= 0) return 0;
        Map<Material, Integer> storage = readStorage(item);
        int current = storage.getOrDefault(material, 0);
        int room = Math.max(0, MAX_PER_MATERIAL - current);
        int absorbed = Math.min(room, amount);
        if (absorbed <= 0) return 0;
        storage.put(material, current + absorbed);
        applyStorage(item, storage);
        return absorbed;
    }

    public int absorbIntoInventory(PlayerInventory inventory, ItemStack stack) {
        if (inventory == null || stack == null || stack.getType().isAir() || !isAllowed(stack.getType())) return 0;
        int original = stack.getAmount();
        if (original <= 0) return 0;

        int remaining = original;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack candidate = contents[slot];
            if (!isVoidstone(candidate)) continue;
            int absorbed = absorbInto(candidate, stack.getType(), remaining);
            if (absorbed <= 0) continue;
            inventory.setItem(slot, candidate);
            remaining -= absorbed;
        }

        stack.setAmount(remaining);
        return original - remaining;
    }

    public int absorbDrops(PlayerInventory inventory, List<Item> drops) {
        if (inventory == null || drops == null || drops.isEmpty()) return 0;
        int absorbedTotal = 0;
        for (var it = drops.listIterator(); it.hasNext(); ) {
            Item drop = it.next();
            if (drop == null) {
                it.remove();
                continue;
            }
            ItemStack stack = drop.getItemStack();
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                it.remove();
                continue;
            }
            int absorbed = absorbIntoInventory(inventory, stack);
            if (absorbed <= 0) continue;
            absorbedTotal += absorbed;
            if (stack.getAmount() <= 0) {
                it.remove();
            } else {
                drop.setItemStack(stack);
            }
        }
        return absorbedTotal;
    }

    public int removeStored(ItemStack item, Material material, int amount) {
        if (!isVoidstone(item) || !isAllowed(material) || amount <= 0) return 0;
        Map<Material, Integer> storage = readStorage(item);
        int current = storage.getOrDefault(material, 0);
        int removed = Math.min(current, amount);
        if (removed <= 0) return 0;

        int next = current - removed;
        if (next <= 0) {
            storage.remove(material);
        } else {
            storage.put(material, next);
        }
        applyStorage(item, storage);
        return removed;
    }

    public Extraction peekFirstExtractable(ItemStack item) {
        for (Map.Entry<Material, Integer> entry : snapshot(item)) {
            int amount = Math.min(entry.getValue(), Math.max(1, entry.getKey().getMaxStackSize()));
            if (amount > 0) {
                return new Extraction(entry.getKey(), amount);
            }
        }
        return null;
    }

    public int findSlot(Inventory inventory, String itemId) {
        if (inventory == null || itemId == null || itemId.isBlank()) return -1;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isVoidstone(item)) continue;
            if (Objects.equals(itemId, readId(item))) return slot;
        }
        return -1;
    }

    public InventoryItemRef find(PlayerInventory inventory, String itemId) {
        int slot = findSlot(inventory, itemId);
        if (slot < 0) return null;
        ItemStack item = inventory.getItem(slot);
        return item == null ? null : new InventoryItemRef(slot, item);
    }

    public int pushNextStack(Inventory source, int slot, Inventory destination) {
        if (source == null || destination == null || slot < 0) return 0;
        ItemStack item = source.getItem(slot);
        if (!isVoidstone(item)) return 0;

        Extraction extraction = peekFirstExtractable(item);
        if (extraction == null) return 0;

        ItemStack payload = new ItemStack(extraction.material(), extraction.amount());
        Map<Integer, ItemStack> overflow = destination.addItem(payload);
        int left = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        int moved = extraction.amount() - left;
        if (moved <= 0) return 0;

        removeStored(item, extraction.material(), moved);
        source.setItem(slot, item);
        return moved;
    }

    public static String prettyName(Material material) {
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                upper = true;
                out.append(c);
                continue;
            }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }

    private void applyStorage(ItemStack item, Map<Material, Integer> storage) {
        if (!isVoidstone(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(MM.deserialize("<!italic><gradient:#0f2027:#203a43:#2c5364><bold>Voidstone</bold></gradient>"));
        meta.lore(buildLore(storage));
        meta.getPersistentDataContainer().set(storageKey, PersistentDataType.STRING, serialize(storage));
        item.setItemMeta(meta);
    }

    private List<Component> buildLore(Map<Material, Integer> storage) {
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic><gray>Stores repetitive mining blocks automatically.</gray>"));
        lore.add(MM.deserialize("<!italic><gray>Capacity: <yellow>" + formatFull(MAX_PER_MATERIAL)
                + "</yellow> of each material.</gray>"));
        lore.add(MM.deserialize("<!italic><gray>Total stored: <yellow>" + formatFull(total(storage))
                + "</yellow></gray>"));
        lore.add(Component.empty());

        List<Map.Entry<Material, Integer>> snapshot = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : storage.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                snapshot.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        snapshot.sort(ENTRY_ORDER);

        if (snapshot.isEmpty()) {
            lore.add(MM.deserialize("<!italic><dark_gray>Empty.</dark_gray>"));
        } else {
            int shown = Math.min(TOOLTIP_MATERIAL_LINES, snapshot.size());
            for (int i = 0; i < shown; i++) {
                Map.Entry<Material, Integer> entry = snapshot.get(i);
                lore.add(MM.deserialize("<!italic><gray>" + prettyName(entry.getKey())
                        + ": <yellow>" + formatCompact(entry.getValue()) + "</yellow></gray>"));
            }
            if (snapshot.size() > shown) {
                lore.add(MM.deserialize("<!italic><dark_gray>+" + (snapshot.size() - shown)
                        + " more material(s)</dark_gray>"));
            }
        }

        lore.add(Component.empty());
        lore.add(MM.deserialize("<!italic><dark_gray>Right-click to open.</dark_gray>"));
        lore.add(MM.deserialize("<!italic><dark_gray>Works with Magnet and hoppers.</dark_gray>"));
        return lore.stream()
                .map(component -> component.decoration(TextDecoration.ITALIC, false))
                .toList();
    }

    private Map<Material, Integer> deserialize(String raw) {
        Map<Material, Integer> storage = new EnumMap<>(Material.class);
        if (raw == null || raw.isBlank()) return storage;

        for (String token : raw.split(";")) {
            if (token.isBlank()) continue;
            int sep = token.indexOf('=');
            if (sep <= 0 || sep >= token.length() - 1) continue;

            Material material = Material.matchMaterial(token.substring(0, sep));
            if (!isAllowed(material)) continue;

            int amount;
            try {
                amount = Integer.parseInt(token.substring(sep + 1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (amount <= 0) continue;
            storage.put(material, Math.min(MAX_PER_MATERIAL, amount));
        }
        return storage;
    }

    private String serialize(Map<Material, Integer> storage) {
        return storage.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0 && isAllowed(entry.getKey()))
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Material::name)))
                .map(entry -> entry.getKey().name() + "=" + Math.min(MAX_PER_MATERIAL, entry.getValue()))
                .collect(Collectors.joining(";"));
    }

    private int total(Map<Material, Integer> storage) {
        int total = 0;
        for (int amount : storage.values()) {
            total += amount;
        }
        return total;
    }

    private String formatFull(int amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private String formatCompact(int amount) {
        if (amount >= 1_000_000) return String.format(Locale.US, "%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format(Locale.US, "%.1fK", amount / 1_000.0);
        return formatFull(amount);
    }

    private ItemStack findFirst(ItemStack[] contents) {
        if (contents == null) return null;
        for (ItemStack item : contents) {
            if (isVoidstone(item)) return item;
        }
        return null;
    }

    public record Extraction(Material material, int amount) {}

    public record InventoryItemRef(int slot, ItemStack item) {}
}
