package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Random;

public class SeedSpoofListener implements Listener {

    private static final String PERM = "smp.admin";

    private final SMPCore plugin;

    public SeedSpoofListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!isSeedCommand(e.getMessage())) return;
        Player p = e.getPlayer();
        if (p.hasPermission(PERM)) return;
        e.setCancelled(true);
        p.sendMessage(Msg.info("<gray>Seed: <yellow>" + fakeSeedFor(p.getWorld()) + "</yellow></gray>"));
    }

    private boolean isSeedCommand(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        if (raw.charAt(0) != '/') return false;
        int end = raw.indexOf(' ');
        String head = (end == -1 ? raw.substring(1) : raw.substring(1, end)).toLowerCase();
        return head.equals("seed")
                || head.equals("minecraft:seed")
                || head.equals("mc:seed")
                || head.equals("bukkit:seed");
    }

    private long fakeSeedFor(World world) {
        String name = world != null ? world.getName() : "world";
        return new Random(name.hashCode() * 2654435761L ^ 0x5EED5A17CAFEBABEL).nextLong();
    }
}
