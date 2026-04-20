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
                            System.currentTimeMillis()));
                    mover.sendMessage(Msg.info("<aqua>Transfert par <white>" + adminName +
                            "</white> vers <white>" + destServer + "</white>...</aqua>"));
                    sendTransfer(mover, destServer);
                });
            }
            default -> { /* unknown inner action */ }
        }
    }

    private static String escape(String s) {
        return s.replace("<", "‹").replace(">", "›");
    }
}
