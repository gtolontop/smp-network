package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Permet de capturer un villageois avec un seau vide (comme les axolotls)
 * et de le reposer au clic-droit sur un bloc.
 *
 * L'item-bucket obtenu est un MILK_BUCKET marqué via PDC, qui sérialise :
 *  profession, type, niveau, XP, âge, santé, custom name et tous les trades.
 */
public class VillagerBucketListener implements Listener {

    private static final byte FORMAT_VERSION = 1;

    private final SMPCore plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey dataKey;
    private final NamespacedKey professionKey;

    public VillagerBucketListener(SMPCore plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "villager_bucket");
        this.dataKey = new NamespacedKey(plugin, "villager_bucket_data");
        this.professionKey = new NamespacedKey(plugin, "villager_bucket_profession");
    }

    // ======================= CAPTURE =======================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractVillager(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager villager)) return;
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.BUCKET) return;

        // Empêche l'ouverture du trade GUI et toute autre interaction.
        e.setCancelled(true);

        byte[] payload;
        try {
            payload = serialize(villager);
        } catch (IOException ex) {
            plugin.getLogger().warning("Capture villageois: échec sérialisation: " + ex.getMessage());
            p.sendMessage(Msg.err("Impossible de capturer ce villageois."));
            return;
        }

        ItemStack bucket = buildBucket(payload, villager);

        // Retire le villageois du monde. Coupe court à une éventuelle entrée
        // en mémoire (perdition par chunk-unload) en le marquant persistant.
        villager.remove();

        // Consomme 1 seau vide de la main (sauf créatif : on ne touche à rien
        // côté inventaire, on donne juste le bucket plein).
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (hand.getAmount() <= 1) {
                p.getInventory().setItemInMainHand(bucket);
            } else {
                hand.setAmount(hand.getAmount() - 1);
                var leftover = p.getInventory().addItem(bucket);
                for (ItemStack it : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), it);
                }
            }
        } else {
            var leftover = p.getInventory().addItem(bucket);
            for (ItemStack it : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), it);
            }
        }

        p.getWorld().playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL_FISH, 1f, 1f);
        p.swingMainHand();
    }

    // ======================= PLACEMENT =======================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !isVillagerBucket(item)) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();

        BlockFace face = e.getBlockFace();
        Block target = clicked.getRelative(face);
        Location spawnLoc = target.getLocation().add(0.5, 0.0, 0.5);
        spawnLoc.setYaw(p.getLocation().getYaw());

        byte[] payload = item.getItemMeta().getPersistentDataContainer()
                .get(dataKey, PersistentDataType.BYTE_ARRAY);
        if (payload == null) {
            p.sendMessage(Msg.err("Ce seau de villageois est corrompu."));
            return;
        }

        try {
            spawnLoc.getWorld().spawn(spawnLoc, Villager.class, v -> {
                try {
                    applySerialized(v, payload);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Spawn villageois: échec désérialisation: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Spawn villageois: " + ex.getMessage());
            p.sendMessage(Msg.err("Impossible de poser ce villageois ici."));
            return;
        }

        // Restaure un seau vide (créatif : on ne touche à rien).
        if (p.getGameMode() != GameMode.CREATIVE) {
            ItemStack empty = new ItemStack(Material.BUCKET);
            if (item.getAmount() <= 1) {
                p.getInventory().setItemInMainHand(empty);
            } else {
                item.setAmount(item.getAmount() - 1);
                var leftover = p.getInventory().addItem(empty);
                for (ItemStack it : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), it);
                }
            }
        }

        p.getWorld().playSound(spawnLoc, Sound.ITEM_BUCKET_EMPTY_FISH, 1f, 1f);
        p.swingMainHand();
    }

    // Empêche la consommation/potion-drink du milk bucket marqué.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (isVillagerBucket(e.getItem())) e.setCancelled(true);
    }

    // ======================= ITEM =======================

    private ItemStack buildBucket(byte[] payload, Villager v) {
        ItemStack item = new ItemStack(Material.MILK_BUCKET);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String profLabel = prettyProfession(v.getProfession());

        meta.displayName(Component.text("Seau de Villageois", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Profession : ", NamedTextColor.GRAY)
                .append(Component.text(profLabel, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Niveau : ", NamedTextColor.GRAY)
                .append(Component.text(v.getVillagerLevel(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        if (v.isAdult()) {
            lore.add(Component.text("Adulte", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Bébé", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Clic-droit sur un bloc pour le poser.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(dataKey, PersistentDataType.BYTE_ARRAY, payload);
        pdc.set(professionKey, PersistentDataType.STRING, profLabel);

        item.setItemMeta(meta);
        return item;
    }

    private boolean isVillagerBucket(ItemStack item) {
        if (item == null || item.getType() != Material.MILK_BUCKET) return false;
        if (!item.hasItemMeta()) return false;
        Byte b = item.getItemMeta().getPersistentDataContainer()
                .get(markerKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    // ======================= SERIALIZATION =======================

    private byte[] serialize(Villager v) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeByte(FORMAT_VERSION);

            out.writeUTF(keyOf(v.getProfession()));
            out.writeUTF(keyOf(v.getVillagerType()));
            out.writeInt(v.getVillagerLevel());
            out.writeInt(v.getVillagerExperience());
            out.writeInt(v.getAge());
            out.writeDouble(v.getHealth());

            Component name = v.customName();
            if (name != null) {
                out.writeByte(1);
                out.writeUTF(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(name));
            } else {
                out.writeByte(0);
            }

            List<MerchantRecipe> recipes = v.getRecipes();
            out.writeInt(recipes.size());
            for (MerchantRecipe r : recipes) {
                writeItem(out, r.getResult());
                List<ItemStack> ings = r.getIngredients();
                int count = Math.min(2, ings.size());
                out.writeByte(count);
                for (int i = 0; i < count; i++) writeItem(out, ings.get(i));
                out.writeInt(r.getUses());
                out.writeInt(r.getMaxUses());
                out.writeInt(r.getVillagerExperience());
                out.writeFloat(r.getPriceMultiplier());
                out.writeInt(r.getDemand());
                out.writeInt(r.getSpecialPrice());
                out.writeBoolean(r.hasExperienceReward());
            }
        }
        return baos.toByteArray();
    }

    private void applySerialized(Villager v, byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte version = in.readByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("Version non supportée: " + version);
            }

            String profKey = in.readUTF();
            String typeKey = in.readUTF();
            int level = in.readInt();
            int xp = in.readInt();
            int age = in.readInt();
            double health = in.readDouble();

            byte hasName = in.readByte();
            Component customName = null;
            if (hasName == 1) {
                String json = in.readUTF();
                try {
                    customName = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                            .deserialize(json);
                } catch (Exception ignored) {}
            }

            Villager.Profession prof = lookupProfession(profKey);
            Villager.Type type = lookupType(typeKey);
            if (type != null) v.setVillagerType(type);
            if (prof != null) v.setProfession(prof);
            v.setVillagerLevel(Math.max(1, Math.min(5, level)));
            v.setVillagerExperience(Math.max(0, xp));
            v.setAge(age);
            double maxHp = v.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                    ? v.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                    : 20.0;
            v.setHealth(Math.max(0.1, Math.min(maxHp, health)));
            if (customName != null) v.customName(customName);

            int recipeCount = in.readInt();
            List<MerchantRecipe> recipes = new ArrayList<>(recipeCount);
            for (int i = 0; i < recipeCount; i++) {
                ItemStack result = readItem(in);
                int ingCount = in.readByte() & 0xFF;
                List<ItemStack> ings = new ArrayList<>(ingCount);
                for (int k = 0; k < ingCount; k++) ings.add(readItem(in));
                int uses = in.readInt();
                int maxUses = in.readInt();
                int villagerXp = in.readInt();
                float priceMult = in.readFloat();
                int demand = in.readInt();
                int specialPrice = in.readInt();
                boolean expReward = in.readBoolean();

                MerchantRecipe mr = new MerchantRecipe(result, uses, maxUses, expReward,
                        villagerXp, priceMult, demand, specialPrice);
                mr.setIngredients(ings);
                recipes.add(mr);
            }
            if (!recipes.isEmpty()) v.setRecipes(recipes);
        }
    }

    private void writeItem(DataOutputStream out, ItemStack item) throws IOException {
        if (item == null || item.isEmpty()) {
            out.writeInt(0);
            return;
        }
        byte[] bytes = item.serializeAsBytes();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private ItemStack readItem(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0) return new ItemStack(Material.AIR);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return ItemStack.deserializeBytes(bytes);
    }

    // ======================= REGISTRY =======================

    private static String keyOf(Villager.Profession p) {
        if (p == null) return "minecraft:none";
        NamespacedKey k = Registry.VILLAGER_PROFESSION.getKey(p);
        return k != null ? k.toString() : "minecraft:none";
    }

    private static String keyOf(Villager.Type t) {
        if (t == null) return "minecraft:plains";
        NamespacedKey k = Registry.VILLAGER_TYPE.getKey(t);
        return k != null ? k.toString() : "minecraft:plains";
    }

    private static Villager.Profession lookupProfession(String key) {
        try {
            NamespacedKey k = NamespacedKey.fromString(key);
            if (k == null) return null;
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_PROFESSION).get(k);
        } catch (Exception e) {
            return null;
        }
    }

    private static Villager.Type lookupType(String key) {
        try {
            NamespacedKey k = NamespacedKey.fromString(key);
            if (k == null) return null;
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_TYPE).get(k);
        } catch (Exception e) {
            return null;
        }
    }

    private static String prettyProfession(Villager.Profession p) {
        if (p == null) return "Aucune";
        NamespacedKey k = Registry.VILLAGER_PROFESSION.getKey(p);
        if (k == null) return "Aucune";
        String path = k.getKey();
        if (path.isEmpty()) return "Aucune";
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }
}
