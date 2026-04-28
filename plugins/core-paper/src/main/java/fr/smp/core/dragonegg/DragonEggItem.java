package fr.smp.core.dragonegg;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Tag PDC + builder pour l'œuf du dragon "tracké". Un DRAGON_EGG vanilla sans
 * ce tag est ignoré par le système — admin doit obligatoirement passer par
 * {@link #build()} pour créer un œuf actif.
 */
public final class DragonEggItem {

    private final NamespacedKey markerKey;
    private final NamespacedKey trackerKey;

    public DragonEggItem(SMPCore plugin) {
        this.markerKey = new NamespacedKey(plugin, "dragon_egg_tracked");
        this.trackerKey = new NamespacedKey(plugin, "dragon_egg_tracker");
    }

    public NamespacedKey markerKey() { return markerKey; }
    public NamespacedKey trackerKey() { return trackerKey; }

    public ItemStack build() {
        ItemStack item = new ItemStack(Material.DRAGON_EGG);
        applyMeta(item);
        return item;
    }

    public void applyMeta(ItemStack item) {
        if (item == null || item.getType() != Material.DRAGON_EGG) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(Msg.mm("<gradient:#a78bfa:#67e8f9><bold>Œuf du Dragon</bold></gradient>")
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Msg.mm("<dark_purple>Relique légendaire — </dark_purple><gray>traquée à travers le monde.</gray>")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Msg.mm("<gray>• <aqua>+2.5 ❤</aqua> d'absorption permanente").decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>• Effets de <gold>balise</gold> pour toi et ta team").decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>• <yellow>Glow</yellow> visible pour tout le monde").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Msg.mm("<red>⚠ Impossible à stocker en coffre.</red>").decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<red>⚠ Te suit à la déco — gros faisceau au sol.</red>").decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<red>⚠ Brûle / void / explosion → respawn à l'autel.</red>").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
    }

    public boolean isTracked(ItemStack item) {
        if (item == null || item.getType() != Material.DRAGON_EGG || !item.hasItemMeta()) return false;
        return Byte.valueOf((byte) 1).equals(
                item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE));
    }

    public ItemStack buildTracker() {
        ItemStack tracker = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = tracker.getItemMeta();
        if (meta == null) return tracker;

        meta.displayName(Msg.mm("<gradient:#a78bfa:#67e8f9><bold>Traqueur de l'Œuf</bold></gradient>")
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Msg.mm("<gray>Pointe vers le dernier signal de l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient>.</gray>")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<dark_gray>Le signal devient instable entre les dimensions.</dark_gray>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(trackerKey, PersistentDataType.BYTE, (byte) 1);
        tracker.setItemMeta(meta);
        return tracker;
    }

    public boolean isTracker(ItemStack item) {
        if (item == null || item.getType() != Material.RECOVERY_COMPASS || !item.hasItemMeta()) return false;
        return Byte.valueOf((byte) 1).equals(
                item.getItemMeta().getPersistentDataContainer().get(trackerKey, PersistentDataType.BYTE));
    }

    public void pointTracker(ItemStack item, org.bukkit.Location target) {
        if (!isTracker(item) || target == null || target.getWorld() == null) return;
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof CompassMeta meta)) return;
        meta.setLodestone(target.clone());
        meta.setLodestoneTracked(false);
        item.setItemMeta(meta);
    }
}
