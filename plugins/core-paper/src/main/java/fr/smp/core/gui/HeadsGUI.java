package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Heads explorer — simple browse of online players + vanilla mob heads
 * available as items. Buying a head costs 100 saphirs (configurable) and
 * gives the caller the head stack.
 */
public class HeadsGUI extends GUIHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int COST_SAPHIRS = 100;

    private enum Tab { PLAYERS, MOBS }

    private static final List<Material> MOB_HEADS = List.of(
            Material.ZOMBIE_HEAD, Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.DRAGON_HEAD, Material.PIGLIN_HEAD);

    private final SMPCore plugin;
    private Tab tab = Tab.PLAYERS;
    private final Map<Integer, UUID> slotPlayer = new HashMap<>();
    private final Map<Integer, Material> slotMob = new HashMap<>();

    public HeadsGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        tab = Tab.PLAYERS;
        render(viewer);
    }

    private void render(Player viewer) {
        Inventory inv = Bukkit.createInventory(this, 54,
                GUIUtil.title("<gradient:#fddb92:#d1fdff><bold>Heads</bold></gradient>"));
        GUIUtil.fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);
        slotPlayer.clear();
        slotMob.clear();

        inv.setItem(3, GUIUtil.item(
                tab == Tab.PLAYERS ? Material.NETHER_STAR : Material.PLAYER_HEAD,
                (tab == Tab.PLAYERS ? "<yellow><bold>" : "<gray>") + "Joueurs en ligne</>",
                "<gray>Cliquer sur une tête.</gray>"));
        inv.setItem(5, GUIUtil.item(
                tab == Tab.MOBS ? Material.NETHER_STAR : Material.ZOMBIE_HEAD,
                (tab == Tab.MOBS ? "<yellow><bold>" : "<gray>") + "Mobs</>",
                "<gray>Têtes de créatures vanilla.</gray>"));

        int[] inner = innerSlots(6);
        int i = 0;
        if (tab == Tab.PLAYERS) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (i >= inner.length) break;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta sm) {
                    sm.setOwningPlayer(other);
                    sm.displayName(MM.deserialize("<!italic><white><bold>" + other.getName() + "</bold></white>"));
                    sm.lore(List.of(
                            MM.deserialize("<!italic><aqua>◆ " + COST_SAPHIRS + " saphirs</aqua>"),
                            MM.deserialize("<!italic><yellow>▶ Clic: Récupérer</yellow>")));
                    head.setItemMeta(sm);
                }
                inv.setItem(inner[i], head);
                slotPlayer.put(inner[i], other.getUniqueId());
                i++;
            }
        } else {
            for (Material m : MOB_HEADS) {
                if (i >= inner.length) break;
                inv.setItem(inner[i], GUIUtil.item(m,
                        "<white><bold>" + pretty(m) + "</bold></white>",
                        "<aqua>◆ " + COST_SAPHIRS + " saphirs</aqua>",
                        "<yellow>▶ Clic: Récupérer</yellow>"));
                slotMob.put(inner[i], m);
                i++;
            }
        }

        this.inventory = inv;
        viewer.openInventory(inv);
    }

    private int[] innerSlots(int rows) {
        int[] out = new int[(rows - 2) * 7];
        int idx = 0;
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < 8; c++) out[idx++] = r * 9 + c;
        }
        return out;
    }

    private String pretty(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        if (raw == 3) { tab = Tab.PLAYERS; render(p); return; }
        if (raw == 5) { tab = Tab.MOBS; render(p); return; }

        var d = plugin.players().get(p);
        if (d == null) return;
        if (d.shards() < COST_SAPHIRS) {
            p.sendMessage(Msg.err("<red>Il te faut " + COST_SAPHIRS + " saphirs.</red>"));
            return;
        }

        ItemStack giving = null;
        UUID playerUuid = slotPlayer.get(raw);
        Material mob = slotMob.get(raw);

        if (playerUuid != null) {
            Player target = Bukkit.getPlayer(playerUuid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (target != null && head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(target);
                sm.displayName(MM.deserialize("<!italic><white><bold>" + target.getName() + "</bold></white>"));
                head.setItemMeta(sm);
            }
            giving = head;
        } else if (mob != null) {
            giving = new ItemStack(mob);
        }
        if (giving == null) return;

        d.addShards(-COST_SAPHIRS);
        p.getInventory().addItem(giving).forEach((slot, left) -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.sendMessage(Msg.ok("<green>Tête obtenue pour " + COST_SAPHIRS + " saphirs.</green>"));
    }
}
