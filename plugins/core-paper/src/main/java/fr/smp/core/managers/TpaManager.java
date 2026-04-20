package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.utils.Msg;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpaManager {

    public enum Type { TO, HERE }

    public record SenderLoc(String world, double x, double y, double z, float yaw, float pitch) {
        public static SenderLoc EMPTY = new SenderLoc("", 0, 0, 0, 0f, 0f);
        public boolean isEmpty() { return world == null || world.isEmpty(); }
    }

    public record Request(UUID from, String fromName, String fromServer, UUID to, Type type,
                          SenderLoc senderLoc, long expiresAt) {}

    private final SMPCore plugin;
    /** key = target name (lowercase), value = incoming request.
     *  Keyed by name because cross-server requests arrive without a known Bukkit Player object. */
    private final Map<String, Request> pending = new HashMap<>();

    public TpaManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public long ttlMs() {
        return plugin.getConfig().getLong("tpa.ttl-seconds", 60) * 1000L;
    }

    public boolean send(Player from, Player to, Type type) {
        if (from.equals(to)) return false;
        SenderLoc loc = type == Type.HERE
                ? new SenderLoc(from.getWorld().getName(),
                        from.getLocation().getX(), from.getLocation().getY(), from.getLocation().getZ(),
                        from.getLocation().getYaw(), from.getLocation().getPitch())
                : SenderLoc.EMPTY;
        pending.put(to.getName().toLowerCase(), new Request(
                from.getUniqueId(), from.getName(), plugin.getServerType(),
                to.getUniqueId(), type, loc, System.currentTimeMillis() + ttlMs()));
        plugin.logs().log(LogCategory.TPA, from, "sent " + type + " -> " + to.getName());
        return true;
    }

    /** Store an incoming cross-server request (target UUID unknown on this server until accept). */
    public void receiveRemote(String fromName, UUID fromUuid, String fromServer,
                              String toName, Type type, SenderLoc loc) {
        pending.put(toName.toLowerCase(), new Request(
                fromUuid, fromName, fromServer, null, type, loc,
                System.currentTimeMillis() + ttlMs()));
    }

    public Request pendingFor(Player target) {
        Request r = pending.get(target.getName().toLowerCase());
        if (r == null) return null;
        if (r.expiresAt() < System.currentTimeMillis()) {
            pending.remove(target.getName().toLowerCase());
            return null;
        }
        return r;
    }

    public Request consume(Player target) {
        Request r = pendingFor(target);
        if (r != null) pending.remove(target.getName().toLowerCase());
        return r;
    }

    public void cancelOutgoing(UUID from) {
        pending.values().removeIf(r -> r.from().equals(from));
    }

    public void cancelOutgoingTo(String targetName) {
        pending.remove(targetName.toLowerCase());
    }
}
