package fr.smp.core.sign;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Opens a temporary real sign editor for single-line text input.
 * Falls back to {@link fr.smp.core.utils.ChatPrompt} when no air block is reachable above the player.
 * <p>
 * The first line the player types is passed to the callback. An empty line or vanilla
 * "cancel" triggers {@code onCancel} so the calling GUI can re-open itself.
 */
public class SignInput implements Listener {

    private record PendingSign(
            Location loc,
            Material origType,
            Consumer<String> callback,
            Runnable onCancel,
            long expiresAt) {}

    private final SMPCore plugin;
    private final Map<UUID, PendingSign> pending = new ConcurrentHashMap<>();

    public SignInput(SMPCore plugin) {
        this.plugin = plugin;
    }

    /**
     * @param player   recipient
     * @param prompt   short hint shown on line 0 of the sign (≤ 15 chars displayed)
     * @param callback called with the typed text (main thread, never null/empty)
     * @param onCancel called when the player submits nothing or types "annuler" (main thread, may be null)
     */
    public void open(Player player, String prompt, Consumer<String> callback, Runnable onCancel) {
        Location loc = findAirAbove(player);
        if (loc == null) {
            // Fallback: chat input
            plugin.chatPrompt().ask(player, prompt + " <gray>(dans le chat)", 60, text -> {
                if (text.equalsIgnoreCase("annuler") || text.equalsIgnoreCase("cancel")) {
                    if (onCancel != null) Bukkit.getScheduler().runTask(plugin, onCancel);
                } else {
                    callback.accept(text);
                }
            });
            return;
        }

        Block block = loc.getBlock();
        Material orig = block.getType();
        block.setType(Material.OAK_SIGN, false);

        if (!(block.getState() instanceof Sign sign)) {
            block.setType(orig, false);
            plugin.chatPrompt().ask(player, prompt + " <gray>(dans le chat)", 60, callback);
            return;
        }

        // Write a small hint on lines 0-1 so the player knows what to type.
        sign.getSide(Side.FRONT).line(0, Msg.mm("<gray>" + shorten(prompt, 14) + "</gray>"));
        sign.getSide(Side.FRONT).line(1, Msg.mm("<dark_gray>▸ tapez ligne 2</dark_gray>"));
        sign.update(true, false);

        pending.put(player.getUniqueId(),
                new PendingSign(loc, orig, callback, onCancel, System.currentTimeMillis() + 60_000L));

        player.openSign(sign, Side.FRONT);

        // Auto-cleanup after 60 s in case the player never edits the sign.
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingSign ps = pending.remove(uuid);
            if (ps != null) restoreBlock(ps);
        }, 1200L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        PendingSign ps = pending.remove(event.getPlayer().getUniqueId());
        if (ps == null) return;

        event.setCancelled(true);
        restoreBlock(ps);

        // Read the first line the player typed (line index 0 = top of sign).
        var comp = event.line(0);
        String text = comp != null
                ? PlainTextComponentSerializer.plainText().serialize(comp).trim()
                : "";

        if (text.isEmpty() || text.equalsIgnoreCase("annuler") || text.equalsIgnoreCase("cancel")) {
            if (ps.onCancel() != null) Bukkit.getScheduler().runTask(plugin, ps.onCancel());
            return;
        }

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { ps.callback().accept(text); }
            catch (Exception ex) {
                plugin.getLogger().warning("[SignInput] callback error: " + ex.getMessage());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PendingSign ps = pending.remove(event.getPlayer().getUniqueId());
        if (ps != null) restoreBlock(ps);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void restoreBlock(PendingSign ps) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block b = ps.loc().getBlock();
            if (b.getType() == Material.OAK_SIGN) b.setType(ps.origType(), false);
        });
    }

    private Location findAirAbove(Player player) {
        double maxY = player.getWorld().getMaxHeight() - 2;
        for (int dy = 2; dy <= 6; dy++) {
            Location candidate = player.getLocation().clone().add(0, dy, 0);
            if (candidate.getY() > maxY) break;
            if (candidate.getBlock().getType().isAir()) return candidate;
        }
        return null;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
