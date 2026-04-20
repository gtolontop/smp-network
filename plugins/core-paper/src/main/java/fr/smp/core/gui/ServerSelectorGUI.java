package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ServerSelectorGUI {

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final String title;
    private final Map<Integer, ServerItem> items = new HashMap<>();

    // Track open inventories to identify GUI clicks
    private final Set<UUID> viewers = new HashSet<>();

    public ServerSelectorGUI(SMPCore plugin) {
        this.plugin = plugin;
        this.title = plugin.getConfig().getString("menu.title", "Serveurs");
        loadItems();
    }

    private void loadItems() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("menu.items");
        if (section == null) return;

        for (String serverName : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(serverName);
            if (item == null) continue;

            int slot = item.getInt("slot", 0);
            Material material;
            try {
                material = Material.valueOf(item.getString("material", "STONE").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material for server '" + serverName + "', defaulting to STONE.");
                material = Material.STONE;
            }

            String name = item.getString("name", serverName);
            List<String> lore = item.getStringList("lore");

            items.put(slot, new ServerItem(serverName, material, name, lore));
        }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, mm.deserialize(title));

        // Fill background with gray stained glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(mm.deserialize("<!italic><dark_gray> </dark_gray>"));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Place server items
        for (Map.Entry<Integer, ServerItem> entry : items.entrySet()) {
            ServerItem si = entry.getValue();
            ItemStack item = new ItemStack(si.material());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(mm.deserialize("<!italic>" + si.displayName()));

            int count = -1;
            if (plugin.serverStats() != null) {
                var s = plugin.serverStats().get(si.serverName());
                if (s != null) count = s.online();
            }
            if (count < 0 && si.serverName().equalsIgnoreCase(plugin.getServerType())) {
                count = org.bukkit.Bukkit.getOnlinePlayers().size();
            }
            String shown = count < 0 ? "?" : String.valueOf(count);

            List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
            for (String line : si.lore()) {
                String resolved = line.isEmpty() ? "<!italic> " : "<!italic>" + line.replace("%players%", shown);
                loreComponents.add(mm.deserialize(resolved));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
            inv.setItem(entry.getKey(), item);
        }

        viewers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    public boolean isViewer(UUID uuid) {
        return viewers.contains(uuid);
    }

    public void removeViewer(UUID uuid) {
        viewers.remove(uuid);
    }

    public String getServerAtSlot(int slot) {
        ServerItem item = items.get(slot);
        return item != null ? item.serverName() : null;
    }

    private record ServerItem(String serverName, Material material, String displayName, List<String> lore) {}
}
