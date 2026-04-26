package fr.smp.core.utils;

import fr.smp.core.SMPCore;
import fr.smp.core.commands.TpCommand;
import fr.smp.core.managers.NetworkRoster;
import fr.smp.core.managers.PendingTeleportManager;
import fr.smp.core.managers.TpaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageChannel implements PluginMessageListener {

    public static final String CHANNEL = "smp:core";
    /** Posé par AntiCheat#ClientDetectionModule pour refuser le transfert vers un autre serveur. */
    public static final String AC_BLOCK_TRANSFER_META = "smp_ac_block_transfer";

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public MessageChannel(SMPCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getLogger().info("Plugin messaging channel '" + CHANNEL + "' registered (in+out).");
    }

    // ---- outgoing ----

    private Player carrier() {
        return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
    }

    public void sendTransfer(Player player, String targetServer) {
        // AntiCheat peut refuser le transfert (cheat client détecté en lobby). On lit
        // le metadata posé par ClientDetectionModule plutôt qu'une dépendance directe
        // au plugin AntiCheat, pour éviter un cycle de dépendances au build.
        if (!"lobby".equalsIgnoreCase(targetServer)
                && player.hasMetadata(AC_BLOCK_TRANSFER_META)) {
            String reason = player.getMetadata(AC_BLOCK_TRANSFER_META).isEmpty()
                    ? "client non autorisé"
                    : player.getMetadata(AC_BLOCK_TRANSFER_META).get(0).asString();
            player.sendMessage(mm.deserialize("<red>Accès refusé</red> <gray>— transfert vers <white>"
                    + targetServer + "</white> bloqué par l'AntiCheat.</gray>"));
            player.sendMessage(mm.deserialize("<dark_gray>Raison: <gray>" + reason + "</gray>"));
            plugin.getLogger().warning("Blocked transfer for " + player.getName()
                    + " to " + targetServer + " (reason=" + reason + ")");
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("transfer");
            out.writeUTF(player.getName());
            out.writeUTF(targetServer);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send transfer: " + e.getMessage());
        }
    }

    /** Request Velocity to transfer a player by name (player may be on another backend). */
    public void sendTransferByName(String playerName, String targetServer) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("transfer");
            out.writeUTF(playerName);
            out.writeUTF(targetServer);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send transfer: " + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface PayloadWriter {
        void write(DataOutputStream out) throws IOException;
    }

    /** Send a forward-to-player message routed by Velocity to whichever backend hosts the target. */
    public void sendForward(String targetPlayerName, String innerAction, PayloadWriter payloadWriter) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("forward");
            out.writeUTF(targetPlayerName);
            out.writeUTF(innerAction);
            payloadWriter.write(out);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send forward: " + e.getMessage());
        }
    }

    public void sendTps(double tps1m, int onlinePlayers, int maxPlayers) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("tps");
            out.writeUTF(plugin.getServerType());
            out.writeDouble(tps1m);
            out.writeInt(onlinePlayers);
            out.writeInt(maxPlayers);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send tps: " + e.getMessage());
        }
    }

    /** Fire-and-forget chat broadcast: proxy relays to all other backends. */
    public void sendChat(String playerName, String rendered) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("chat");
            out.writeUTF(plugin.getServerType());
            out.writeUTF(playerName);
            out.writeUTF(rendered);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send chat: " + e.getMessage());
        }
    }

    /** Cross-server /here broadcast: proxy relays to all other backends. */
    public void sendHere(String playerName, String rendered) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("here");
            out.writeUTF(plugin.getServerType());
            out.writeUTF(playerName);
            out.writeUTF(rendered);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send here: " + e.getMessage());
        }
    }

    /** Cross-server /chat lock/unlock: proxy relays to all other backends. */
    public void sendChatLock(boolean locked, String issuer) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("chat-lock");
            out.writeUTF(plugin.getServerType());
            out.writeBoolean(locked);
            out.writeUTF(issuer);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send chat-lock: " + e.getMessage());
        }
    }

    /** Tell the network: permissions changed, please reload from SQLite. */
    public void sendPermReload() {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("permreload");
            out.writeUTF(plugin.getServerType());
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send permreload: " + e.getMessage());
        }
    }

    /** Push this server's roster to the proxy so it can aggregate. */
    public void sendRoster() {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("roster");
            out.writeUTF(plugin.getServerType());
            var online = Bukkit.getOnlinePlayers();
            out.writeInt(online.size());
            for (Player p : online) {
                out.writeUTF(p.getName());
                String prefix = plugin.permissions() != null
                        ? plugin.permissions().prefixOf(p.getUniqueId())
                        : "";
                out.writeUTF(prefix == null ? "" : prefix);
            }
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send roster: " + e.getMessage());
        }
    }

    /** Notify Velocity that a cracked player has successfully authenticated. */
    public void sendAuthNotify(Player player) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("auth-notify");
            out.writeUTF(player.getUniqueId().toString());
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send auth-notify: " + e.getMessage());
        }
    }

    public void sendModKick(String playerName, String reason) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("mod-kick");
            out.writeUTF(playerName);
            out.writeUTF(reason);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send mod-kick: " + e.getMessage());
        }
    }

    public void sendModBanKick(String playerName, String reason, String durationStr) {
        Player carrier = carrier();
        if (carrier == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("mod-ban-kick");
            out.writeUTF(playerName);
            out.writeUTF(reason != null ? reason : "");
            out.writeUTF(durationStr);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send mod-ban-kick: " + e.getMessage());
        }
    }

    public void sendModMuteNotify(String playerName, String reason, String durationStr) {
        sendForward(playerName, "mute-notify", out -> {
            out.writeUTF(reason != null ? reason : "");
            out.writeUTF(durationStr);
        });
    }

    public void sendModUnmuteNotify(String playerName) {
        sendForward(playerName, "unmute-notify", out -> {});
    }

    // ---- incoming ----

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String action = in.readUTF();
            switch (action) {
                case "setmaxplayers" -> {
                    int max = in.readInt();
                    Bukkit.getServer().setMaxPlayers(max);
                    plugin.getLogger().info("Max players set to " + max + " via proxy.");
                }
                case "stats" -> {
                    int count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        String server = in.readUTF();
                        int online = in.readInt();
                        int max = in.readInt();
                        if (plugin.serverStats() != null) {
                            plugin.serverStats().put(server, online, max);
                        }
                    }
                }
                case "chat" -> {
                    String sourceServer = in.readUTF();
                    String playerName = in.readUTF();
                    String rendered = in.readUTF();
                    // Always deliver: players on the source server already saw it
                    // via their local AsyncChatEvent, so the proxy should only
                    // forward to other backends. We still guard here in case the
                    // proxy broadcasts back to source.
                    if (sourceServer.equals(plugin.getServerType())) return;
                    Component comp = mm.deserialize(rendered);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(comp);
                        Bukkit.getConsoleSender().sendMessage(comp);
                    });
                }
                case "here" -> {
                    String sourceServer = in.readUTF();
                    String playerName = in.readUTF();
                    String rendered = in.readUTF();
                    if (sourceServer.equals(plugin.getServerType())) return;
                    Component comp = mm.deserialize(rendered);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(comp);
                        Bukkit.getConsoleSender().sendMessage(comp);
                    });
                }
                case "chat-lock" -> {
                    String sourceServer = in.readUTF();
                    boolean locked = in.readBoolean();
                    String issuer = in.readUTF();
                    if (sourceServer.equals(plugin.getServerType())) return;
                    plugin.setChatLocked(locked);
                    String msg = locked
                            ? "<red><bold>[Chat]</bold></red> <gray>Le chat a été <red>verrouillé</red> par <white>" + issuer + "</white>.</gray>"
                            : "<green><bold>[Chat]</bold></green> <gray>Le chat a été <green>déverrouillé</green> par <white>" + issuer + "</white>.</gray>";
                    Component comp = mm.deserialize(msg);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(comp);
                        Bukkit.getConsoleSender().sendMessage(comp);
                    });
                }
                case "permreload" -> {
                    String sourceServer = in.readUTF();
                    if (sourceServer.equals(plugin.getServerType())) return;
                    if (plugin.permissions() != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.permissions().reload());
                    }
                }
                case "roster" -> {
                    int total = in.readInt();
                    List<NetworkRoster.Entry> entries = new ArrayList<>(total);
                    for (int i = 0; i < total; i++) {
                        String name = in.readUTF();
                        String server = in.readUTF();
                        String prefix = in.readUTF();
                        entries.add(new NetworkRoster.Entry(name, server, prefix));
                    }
                    if (plugin.roster() != null) plugin.roster().replace(entries);
                }
                case "forward" -> {
                    String targetName = in.readUTF();
                    String innerAction = in.readUTF();
                    handleForward(targetName, innerAction, in);
                }
                case "auth-validated" -> {
                    String uuidStr = in.readUTF();
                    String playerName = in.readUTF();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            Player p = Bukkit.getPlayer(uuid);
                            if (p == null || !p.isOnline()) return;
                            if (plugin.auth() == null) return;
                            if (plugin.auth().isAuthenticated(p)) return; // already auth
                            plugin.auth().markAuthenticatedFromProxy(p);
                            plugin.getLogger().info("Auto-auth from proxy for " + playerName);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid UUID in auth-validated: " + uuidStr);
                        }
                    });
                }
                default -> {
                    // ignore unknown actions
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read incoming smp:core message: " + e.getMessage());
        }
    }

    private void handleForward(String targetName, String innerAction, DataInputStream in) throws IOException {
        switch (innerAction) {
            case "tpa-request" -> {
                String fromName = in.readUTF();
                UUID fromUuid = UUID.fromString(in.readUTF());
                String fromServer = in.readUTF();
                String type = in.readUTF();
                String world = in.readUTF();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                TpaManager.SenderLoc loc = world.isEmpty()
                        ? TpaManager.SenderLoc.EMPTY
                        : new TpaManager.SenderLoc(world, x, y, z, yaw, pitch);
                TpaManager.Type t = "HERE".equals(type) ? TpaManager.Type.HERE : TpaManager.Type.TO;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) return;
                    plugin.tpa().receiveRemote(fromName, fromUuid, fromServer, targetName, t, loc);
                    String verb = t == TpaManager.Type.HERE ? "veut que tu le rejoignes" : "veut se téléporter chez toi";
                    target.sendMessage(Msg.info("<aqua>" + fromName + "</aqua> " + verb +
                            ". <green>/tpaccept</green> <dark_gray>|</dark_gray> <red>/tpdeny</red>."));
                });
            }
            case "tpa-denied" -> {
                String byName = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayerExact(targetName);
                    if (p != null) p.sendMessage(Msg.err(byName + " a refusé ta demande."));
                });
            }
            case "tpa-cancel" -> {
                String fromName = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.tpa().cancelOutgoingTo(targetName);
                    Player p = Bukkit.getPlayerExact(targetName);
                    if (p != null) p.sendMessage(Msg.err(fromName + " a annulé sa demande."));
                });
            }
            case "msg" -> {
                String fromName = in.readUTF();
                UUID fromUuid = UUID.fromString(in.readUTF());
                String text = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) return;
                    Component incoming = mm.deserialize("<dark_gray>[</dark_gray><aqua>" + fromName +
                            "</aqua> <gray>→ moi</gray><dark_gray>]</dark_gray> <white>" + escape(text) + "</white>");
                    target.sendMessage(incoming);
                    if (plugin.messages() != null) plugin.messages().remember(target.getUniqueId(), fromUuid);
                });
            }
            case "team-invite" -> {
                String fromName = in.readUTF();
                String teamId = in.readUTF();
                String teamTag = in.readUTF();
                String teamColor = in.readUTF();
                String teamName = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) return;
                    if (plugin.teamInvites() != null) {
                        plugin.teamInvites().invite(target.getUniqueId(), teamId);
                    }
                    target.sendMessage(Msg.info("<aqua>" + fromName + "</aqua> t'invite dans <white>" +
                            teamColor + "[" + teamTag + "] " + teamName + "<reset></white>. <green>/team join " + teamTag + "</green>"));
                });
            }
            case "pay-notice" -> {
                String fromName = in.readUTF();
                double amount = in.readDouble();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        target.sendMessage(Msg.info("<green>$" + Msg.money(amount) +
                                "</green> <gray>reçu de</gray> <aqua>" + fromName + "</aqua>"));
                    }
                });
            }
            case "tp-lookup" -> {
                String requesterName = in.readUTF();
                String requesterServer = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) return;
                    Location loc = target.getLocation();
                    String myServer = plugin.getServerType();
                    sendForward(requesterName, "tp-execute", o -> {
                        o.writeUTF(myServer);
                        o.writeUTF(loc.getWorld().getName());
                        o.writeDouble(loc.getX());
                        o.writeDouble(loc.getY());
                        o.writeDouble(loc.getZ());
                        o.writeFloat(loc.getYaw());
                        o.writeFloat(loc.getPitch());
                    });
                });
            }
            case "tp-execute" -> {
                String targetServer = in.readUTF();
                String world = in.readUTF();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                TpCommand.applyTpExecute(plugin, targetName, world, x, y, z, yaw, pitch, targetServer);
            }
            case "tp-dest-lookup" -> {
                // targetName = dest player; we relay a tp-move-here to mover.
                String moverName = in.readUTF();
                String adminName = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player dest = Bukkit.getPlayerExact(targetName);
                    if (dest == null) return;
                    Location loc = dest.getLocation();
                    String myServer = plugin.getServerType();
                    sendForward(moverName, "tp-move-here", o -> {
                        o.writeUTF(myServer);
                        o.writeUTF(loc.getWorld().getName());
                        o.writeDouble(loc.getX());
                        o.writeDouble(loc.getY());
                        o.writeDouble(loc.getZ());
                        o.writeFloat(loc.getYaw());
                        o.writeFloat(loc.getPitch());
                        o.writeUTF(adminName);
                    });
                });
            }
            case "tp-move-here" -> {
                // targetName = mover. We have their UUID locally, so we can write
                // pending-tp and transfer (or teleport in-place if already on destServer).
                String destServer = in.readUTF();
                String world = in.readUTF();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                String adminName = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player mover = Bukkit.getPlayerExact(targetName);
                    if (mover == null) return;
                    if (destServer.equalsIgnoreCase(plugin.getServerType())) {
                        World w = Bukkit.getWorld(world);
                        if (w == null) return;
                        mover.teleportAsync(new Location(w, x, y, z, yaw, pitch));
                        mover.sendMessage(Msg.info("<aqua>Téléporté par <white>" + adminName + "</white>.</aqua>"));
                        return;
                    }
                    plugin.pendingTp().set(mover.getUniqueId(), new PendingTeleportManager.Pending(
                            PendingTeleportManager.Kind.LOC, world, x, y, z, yaw, pitch,
                            System.currentTimeMillis(), destServer));
                    mover.sendMessage(Msg.info("<aqua>Transfert par <white>" + adminName +
                            "</white> vers <white>" + destServer + "</white>...</aqua>"));
                    sendTransfer(mover, destServer);
                });
            }
            case "mute-notify" -> {
                String reason = in.readUTF();
                String duration = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) return;
                    Component notice = mm.deserialize("<red><bold>Mute</bold></red> <gray>" +
                            (!reason.isEmpty() ? reason : "") + "</gray>" +
                            (!duration.isEmpty() ? " <gray>(" + duration + ")</gray>" : ""));
                    target.sendMessage(notice);
                });
            }
            case "unmute-notify" -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        target.sendMessage(mm.deserialize("<green><bold>Unmute</bold></green> <gray>Tu n'es plus muet.</gray>"));
                    }
                });
            }
            default -> { /* unknown inner action */ }
        }
    }

    private static String escape(String s) {
        return s.replace("<", "‹").replace(">", "›");
    }
}
