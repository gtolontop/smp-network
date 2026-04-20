package fr.smp.core.alchemytotem;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabrique et lit le Totem d'Alchimie. L'item est un TOTEM_OF_UNDYING avec:
 *   - tag PDC "alchemy_totem" (byte 1) pour l'identifier
 *   - tag "totem_effect"   (string, id de AlchemyEffect)
 *   - tag "totem_charges"  (int, charges restantes)
 * Le glint vanilla est forcé via setEnchantmentGlintOverride(true).
 */
public final class AlchemyTotemItem {

    public static final int MAX_CHARGES = 5;

    private final NamespacedKey markerKey;
    private final NamespacedKey effectKey;
    private final NamespacedKey chargesKey;

    public AlchemyTotemItem(SMPCore plugin) {
        this.markerKey = new NamespacedKey(plugin, "alchemy_totem");
        this.effectKey = new NamespacedKey(plugin, "totem_effect");
        this.chargesKey = new NamespacedKey(plugin, "totem_charges");
    }

    public NamespacedKey markerKey() { return markerKey; }

    public ItemStack build(AlchemyEffect effect, int charges) {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Msg.mm("<" + effect.colorHex() + "><bold>Totem d'Alchimie</bold></" + effect.colorHex() + ">")
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Msg.mm("<gray>Effet : <" + effect.colorHex() + ">" + effect.display() + "</" + effect.colorHex() + ">")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<gray>Charges : <yellow>" + charges + "</yellow><dark_gray>/</dark_gray><yellow>" + MAX_CHARGES + "</yellow>")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Msg.mm("<dark_gray>Sous <red>4 ❤</red><dark_gray> : effet actif en continu")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Msg.mm("<dark_gray>Au-dessus : rémanence de <yellow>45s</yellow>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);

        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(effectKey, PersistentDataType.STRING, effect.id());
        pdc.set(chargesKey, PersistentDataType.INTEGER, charges);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        if (!item.hasItemMeta()) return false;
        return Byte.valueOf((byte) 1).equals(
                item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE));
    }

    public AlchemyEffect readEffect(ItemStack item) {
        if (!isTotem(item)) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
        return AlchemyEffect.byId(id);
    }

    public int readCharges(ItemStack item) {
        if (!isTotem(item)) return 0;
        Integer c = item.getItemMeta().getPersistentDataContainer().get(chargesKey, PersistentDataType.INTEGER);
        return c == null ? 0 : c;
    }

    /** Décrémente les charges sur l'item fourni, met à jour le lore et renvoie le nombre restant. */
    public int decrementCharges(ItemStack item) {
        if (!isTotem(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        Integer cur = pdc.get(chargesKey, PersistentDataType.INTEGER);
        int next = Math.max(0, (cur == null ? 0 : cur) - 1);
        pdc.set(chargesKey, PersistentDataType.INTEGER, next);

        AlchemyEffect effect = AlchemyEffect.byId(pdc.get(effectKey, PersistentDataType.STRING));
        if (effect != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.mm("<gray>Effet : <" + effect.colorHex() + ">" + effect.display() + "</" + effect.colorHex() + ">")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Msg.mm("<gray>Charges : <yellow>" + next + "</yellow><dark_gray>/</dark_gray><yellow>" + MAX_CHARGES + "</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Msg.mm("<dark_gray>Sous <red>4 ❤</red><dark_gray> : effet actif en continu")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Msg.mm("<dark_gray>Au-dessus : rémanence de <yellow>45s</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return next;
    }
}
