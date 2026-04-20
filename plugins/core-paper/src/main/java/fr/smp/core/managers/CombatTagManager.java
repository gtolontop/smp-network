package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SMPCore plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();

    public CombatTagManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public long durationMs() {
        return plugin.getConfig().getLong("combat.duration-seconds", 15) * 1000L;
    }

    public boolean isTagged(Player p) {
        Long until = taggedUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long remainingSec(Player p) {
        Long until = taggedUntil.get(p.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public void start() {
        // Actionbar countdown displayed just above the hotbar every 5 ticks while tagged.
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, Long> e : taggedUntil.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null) continue;
                    long left = Math.max(0, (e.getValue() - now + 999) / 1000);
                    if (left <= 0) continue;
                    Component bar = MM.deserialize(
                            "<red><bold>⚔ COMBAT</bold></red> <dark_gray>|</dark_gray> " +
                            "<yellow>" + left + "s</yellow>");
                    p.sendActionBar(bar);
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    public void tag(Player p, Player attacker) {
        boolean wasTagged = isTagged(p);
        taggedUntil.put(p.getUniqueId(), System.currentTimeMillis() + durationMs());
        if (attacker != null) lastAttacker.put(p.getUniqueId(), attacker.getUniqueId());
        if (!wasTagged) {
            p.sendMessage(Msg.info("<red>⚔ Combat engagé</red> <gray>— pas de téléport pendant " +
                    (durationMs() / 1000) + "s.</gray>"));
            plugin.logs().log(LogCategory.COMBAT, p, "combat_tag_begin attacker=" +
                    (attacker != null ? attacker.getName() : "?"));
        }
    }

    public void untag(Player p) {
        taggedUntil.remove(p.getUniqueId());
        lastAttacker.remove(p.getUniqueId());
    }

    public UUID lastAttacker(UUID uuid) {
        return lastAttacker.get(uuid);
    }

    public Map<UUID, Long> snapshot() {
        return new HashMap<>(taggedUntil);
    }
}
