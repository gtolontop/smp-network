package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends a configured set of resource packs to every player joining the
 * survival server. Packs are pushed on join and removed on quit; they are
 * not tied to dimensions.
 *
 * <p>Each pack has a stable UUID derived from its config key, so
 * {@code removeResourcePack(uuid)} works reliably across restarts.
 */
public class ResourcePackManager implements Listener {

    private final SMPCore plugin;
    private final List<PackEntry> packs = new ArrayList<>();
    private boolean enabled;

    public ResourcePackManager(SMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        packs.clear();
        enabled = plugin.getConfig().getBoolean("resource-packs.enabled", false);
        if (!enabled) return;

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("resource-packs");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            if (key.equals("enabled")) continue;
            ConfigurationSection slot = section.getConfigurationSection(key);
            if (slot == null) continue;

            String url = slot.getString("url", "");
            String sha1Hex = slot.getString("sha1", "");
            if (url.isBlank() || sha1Hex.isBlank()) continue;

            byte[] hash = parseSha1(sha1Hex);
            if (hash == null) {
                plugin.getLogger().warning("[rp] invalid sha1 for '" + key + "' (expected 40 hex chars), skipping");
                continue;
            }

            boolean required = slot.getBoolean("required", false);
            String promptMm = slot.getString("prompt", "");
            String promptLegacy = promptMm.isBlank() ? null
                    : LegacyComponentSerializer.legacySection().serialize(
                            MiniMessage.miniMessage().deserialize(promptMm));

            UUID id = UUID.nameUUIDFromBytes(("smp-core:rp:" + key).getBytes(StandardCharsets.UTF_8));
            packs.add(new PackEntry(id, url, hash, promptLegacy, required));
        }

        plugin.getLogger().info("[rp] loaded " + packs.size() + " resource pack(s)");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || packs.isEmpty()) return;
        // Defer so the player's connection is fully settled before we push packs.
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled || packs.isEmpty()) return;
        Player p = event.getPlayer();
        for (PackEntry pack : packs) {
            p.removeResourcePack(pack.id);
        }
    }

    public void apply(Player p) {
        if (!enabled || !p.isOnline()) return;
        for (PackEntry pack : packs) {
            p.addResourcePack(pack.id, pack.url, pack.hash, pack.prompt, pack.required);
        }
    }

    private static byte[] parseSha1(String hex) {
        if (hex.length() != 40) return null;
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private record PackEntry(UUID id, String url, byte[] hash, String prompt, boolean required) {}
}
