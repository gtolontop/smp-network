package fr.smp.core.net;

import com.mojang.datafixers.util.Pair;
import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Injects a "Worth: $X" lore line into outgoing container packets so players
 * see the value of items client-side — without ever touching the server-side
 * ItemStack. The real items in the player's inventory and in chests stay
 * pristine (no custom_data, no extra lore), which means the enchanting table,
 * vaults/trial keys, recipes, merchants and every other vanilla mechanic that
 * compares item components keep working.
 *
 * Installed as a Netty channel handler on each player's outbound pipeline via
 * {@link WorthDisplayInjector}. The handler runs on the Netty IO thread — it
 * only reads/writes the WorthManager map (read-only lookup) and allocates NMS
 * ItemStack copies, which is safe off the main thread.
 */
public final class WorthOutboundHandler extends ChannelDuplexHandler {

    private static final TextColor LABEL = TextColor.fromRgb(0x555555);  // dark_gray
    private static final TextColor VALUE = TextColor.fromRgb(0xFFFF55);  // yellow
    private static final Style LABEL_STYLE = Style.EMPTY.withColor(LABEL).withItalic(false);
    private static final Style VALUE_STYLE = Style.EMPTY.withColor(VALUE).withItalic(false);

    // Item → Material is stable at runtime; cache so we avoid allocating a Bukkit
    // copy per item per packet (chest opens push 60+ items at a time).
    private static final ConcurrentMap<Item, Material> MAT_CACHE = new ConcurrentHashMap<>();

    // Reflection fallbacks for packet field access. The NMS packets expose getters
    // with different names across versions; going through the fields directly is
    // the most stable option.
    private static final PacketAccess CSC = PacketAccess.of(
            ClientboundContainerSetContentPacket.class, "containerId", "stateId", "items", "carriedItem");
    private static final PacketAccess CSS = PacketAccess.of(
            ClientboundContainerSetSlotPacket.class, "containerId", "stateId", "slot", "itemStack");
    private static final PacketAccess EQ = PacketAccess.of(
            ClientboundSetEquipmentPacket.class, "entity", "slots");

    private final SMPCore plugin;
    private final Player player;

    public WorthOutboundHandler(SMPCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof Packet<?>) || !player.isOnline()) {
            super.write(ctx, msg, promise);
            return;
        }

        Object out = msg;
        try {
            if (msg instanceof ClientboundContainerSetContentPacket pkt) {
                out = rewriteContent(pkt);
            } else if (msg instanceof ClientboundContainerSetSlotPacket pkt) {
                out = rewriteSlot(pkt);
            } else if (msg instanceof ClientboundSetEquipmentPacket pkt) {
                out = rewriteEquipment(pkt);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("[worth-display] rewrite failed: " + t);
            out = msg;
        }

        super.write(ctx, out, promise);
    }

    // ── Packet rewriters ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object rewriteContent(ClientboundContainerSetContentPacket pkt) throws Exception {
        List<ItemStack> items = (List<ItemStack>) CSC.get(pkt, 2);
        ItemStack carried = (ItemStack) CSC.get(pkt, 3);

        List<ItemStack> newItems = new ArrayList<>(items.size());
        boolean changed = false;
        for (ItemStack s : items) {
            ItemStack d = decorate(s);
            if (d != s) changed = true;
            newItems.add(d);
        }
        ItemStack newCarried = decorate(carried);
        if (!changed && newCarried == carried) return pkt;

        int containerId = (int) CSC.get(pkt, 0);
        int stateId = (int) CSC.get(pkt, 1);
        return new ClientboundContainerSetContentPacket(containerId, stateId, newItems, newCarried);
    }

    private Object rewriteSlot(ClientboundContainerSetSlotPacket pkt) throws Exception {
        ItemStack item = (ItemStack) CSS.get(pkt, 3);
        ItemStack decorated = decorate(item);
        if (decorated == item) return pkt;

        int containerId = (int) CSS.get(pkt, 0);
        int stateId = (int) CSS.get(pkt, 1);
        int slot = (int) CSS.get(pkt, 2);
        return new ClientboundContainerSetSlotPacket(containerId, stateId, slot, decorated);
    }

    @SuppressWarnings("unchecked")
    private Object rewriteEquipment(ClientboundSetEquipmentPacket pkt) throws Exception {
        List<Pair<EquipmentSlot, ItemStack>> slots = (List<Pair<EquipmentSlot, ItemStack>>) EQ.get(pkt, 1);
        List<Pair<EquipmentSlot, ItemStack>> rewritten = new ArrayList<>(slots.size());
        boolean changed = false;
        for (Pair<EquipmentSlot, ItemStack> p : slots) {
            ItemStack d = decorate(p.getSecond());
            if (d != p.getSecond()) changed = true;
            rewritten.add(new Pair<>(p.getFirst(), d));
        }
        if (!changed) return pkt;
        int entityId = (int) EQ.get(pkt, 0);
        return new ClientboundSetEquipmentPacket(entityId, rewritten);
    }

    // ── Decoration ─────────────────────────────────────────────────────

    private ItemStack decorate(ItemStack nms) {
        if (nms == null || nms.isEmpty()) return nms;
        Material mat = materialOf(nms);
        if (mat == null) return nms;

        double unit = worthPerItem(nms, mat);

        if (unit <= 0) return nms;

        List<Component> extra = new ArrayList<>();
        extra.add(Component.literal("Worth: ").withStyle(LABEL_STYLE)
                .append(Component.literal("$" + Msg.money(unit)).withStyle(VALUE_STYLE)));

        ItemStack copy = nms.copy();
        ItemLore existing = copy.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(existing.lines());
        lines.addAll(extra);
        copy.set(DataComponents.LORE, new ItemLore(lines));
        return copy;
    }


    private Material materialOf(ItemStack nms) {
        return MAT_CACHE.computeIfAbsent(nms.getItem(), item -> {
            try {
                return CraftItemStack.asBukkitCopy(new ItemStack(item)).getType();
            } catch (Throwable t) {
                return Material.AIR;
            }
        });
    }

    private double worthPerItem(ItemStack nms, Material mat) {
        return plugin.worth().worth(mat);
    }

    // ── Field access helper ────────────────────────────────────────────

    /** Caches declared-field lookups so every packet rewrite is one array get. */
    private static final class PacketAccess {
        private final Field[] fields;

        private PacketAccess(Field[] fields) {
            this.fields = fields;
        }

        static PacketAccess of(Class<?> type, String... names) {
            Field[] fs = new Field[names.length];
            for (int i = 0; i < names.length; i++) {
                Field f = findField(type, names[i]);
                if (f != null) f.setAccessible(true);
                fs[i] = f;
            }
            return new PacketAccess(fs);
        }

        private static Field findField(Class<?> type, String name) {
            Class<?> c = type;
            while (c != null && c != Object.class) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            return null;
        }

        Object get(Object target, int index) throws IllegalAccessException {
            return fields[index].get(target);
        }
    }
}
