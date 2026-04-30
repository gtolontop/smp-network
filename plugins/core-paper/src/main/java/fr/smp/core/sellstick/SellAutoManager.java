package fr.smp.core.sellstick;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SellAutoManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, double[]> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tasks = new ConcurrentHashMap<>();

    public SellAutoManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled.remove(uuid)) {
            clearPending(uuid);
            return false;
        }
        enabled.add(uuid);
        return true;
    }

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public void remove(Player player) {
        enabled.remove(player.getUniqueId());
        clearPending(player.getUniqueId());
    }

    public void queueActionBar(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        pending.computeIfAbsent(uuid, k -> new double[1])[0] += amount;

        Integer existing = tasks.remove(uuid);
        if (existing != null) plugin.getServer().getScheduler().cancelTask(existing);

        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(uuid);
            double[] arr = pending.remove(uuid);
            if (arr == null) return;
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) return;
            p.sendActionBar(MM.deserialize(
                "<gradient:#a8edea:#fed6e3>SellAuto</gradient> <dark_gray>»</dark_gray> <green>+$" + Msg.money(arr[0]) + "</green>"
            ));
        }, 20L).getTaskId();
        tasks.put(uuid, taskId);
    }

    private void clearPending(UUID uuid) {
        Integer task = tasks.remove(uuid);
        if (task != null) plugin.getServer().getScheduler().cancelTask(task);
        pending.remove(uuid);
    }
}
