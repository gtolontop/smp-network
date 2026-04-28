package fr.smp.core;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public class PluginMessageListener {
    private final SMPCoreVelocity plugin;

    public PluginMessageListener(SMPCoreVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(SMPCoreVelocity.CHANNEL)) return;
        if (!(event.getSource() instanceof ServerConnection connection)) return;

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
            String action = in.readUTF();

            switch (action) {
                case "transfer" -> {
                    String playerName = in.readUTF();
                    String targetServer = in.readUTF();
                    plugin.getServer().getPlayer(playerName).ifPresent(p ->
                        plugin.getServer().getServer(targetServer).ifPresent(s ->
                            p.createConnectionRequest(s).fireAndForget()
                        )
                    );
                }
                case "tps" -> {
                    String serverName = in.readUTF();
                    double tps = in.readDouble();
                    int online = in.readInt();
                    int max = in.readInt();
                    plugin.putStats(serverName, new ServerStats(tps, online, max));
                }
                case "chat" -> {
                    String sourceServer = in.readUTF();
                    String playerName = in.readUTF();
                    String rendered = in.readUTF();
                    byte[] out = encode(w -> {
                        w.writeUTF("chat");
                        w.writeUTF(sourceServer);
                        w.writeUTF(playerName);
                        w.writeUTF(rendered);
                    });
                    relayToOtherBackends(sourceServer, out);
                }
                case "here" -> {
                    String sourceServer = in.readUTF();
                    String playerName = in.readUTF();
                    String rendered = in.readUTF();
                    byte[] out = encode(w -> {
                        w.writeUTF("here");
                        w.writeUTF(sourceServer);
                        w.writeUTF(playerName);
                        w.writeUTF(rendered);
                    });
                    relayToOtherBackends(sourceServer, out);
                }
                case "broadcast" -> {
                    String sourceServer = in.readUTF();
                    String chatRendered = in.readUTF();
                    String titleRendered = in.readUTF();
                    String subtitleRendered = in.readUTF();
                    int fadeIn = in.readInt();
                    int stay = in.readInt();
                    int fadeOut = in.readInt();
                    byte[] out = encode(w -> {
                        w.writeUTF("broadcast");
                        w.writeUTF(sourceServer);
                        w.writeUTF(chatRendered);
                        w.writeUTF(titleRendered);
                        w.writeUTF(subtitleRendered);
                        w.writeInt(fadeIn);
                        w.writeInt(stay);
                        w.writeInt(fadeOut);
                    });
                    relayToOtherBackends(sourceServer, out);
                }
                case "chat-lock" -> {
                    String sourceServer = in.readUTF();
                    boolean locked = in.readBoolean();
                    String issuer = in.readUTF();
                    byte[] out = encode(w -> {
                        w.writeUTF("chat-lock");
                        w.writeUTF(sourceServer);
                        w.writeBoolean(locked);
                        w.writeUTF(issuer);
                    });
                    relayToOtherBackends(sourceServer, out);
                }
                case "permreload" -> {
                    String sourceServer = in.readUTF();
                    byte[] out = encode(w -> {
                        w.writeUTF("permreload");
                        w.writeUTF(sourceServer);
                    });
                    relayToOtherBackends(sourceServer, out);
                }
                case "roster" -> {
                    String sourceServer = in.readUTF();
                    int count = in.readInt();
                    List<RosterEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String name = in.readUTF();
                        String prefix = in.readUTF();
                        entries.add(new RosterEntry(name, sourceServer, prefix));
                    }
                    plugin.putRoster(sourceServer, entries);
                }
                case "auth-notify" -> {
                    String uuidStr = in.readUTF();
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                        plugin.markAuthenticated(uuid);
                        plugin.getLogger().info("Marked {} as authenticated (auth-notify from backend)", uuidStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warn("Invalid UUID in auth-notify: {}", uuidStr);
                    }
                }
                case "mod-kick" -> {
                    String playerName = in.readUTF();
                    String reason = in.readUTF();
                    var mm = plugin.getMiniMessage();
                    plugin.getServer().getPlayer(playerName).ifPresent(p ->
                        p.disconnect(mm.deserialize("<red><bold>Kick</bold></red>\n<gray>" + reason + "</gray>"))
                    );
                }
                case "mod-ban-kick" -> {
                    String playerName = in.readUTF();
                    String reason = in.readUTF();
                    String duration = in.readUTF();
                    var mm = plugin.getMiniMessage();
                    plugin.getServer().getPlayer(playerName).ifPresent(p ->
                        p.disconnect(mm.deserialize("<red><bold>Banni</bold></red>\n<gray>" +
                                (reason.isEmpty() ? "non spécifié" : reason) + "</gray>\n" +
                                ("<permanent>".equals(duration) ? "<dark_red>Permanent</dark_red>"
                                        : "<gray>Durée: </gray><white>" + duration + "</white>")))
                    );
                }
                case "forward" -> {
                    String targetPlayer = in.readUTF();
                    int remaining = in.available();
                    byte[] rest = new byte[remaining];
                    in.readFully(rest);
                    var opt = plugin.getServer().getPlayer(targetPlayer);
                    if (opt.isEmpty()) break;
                    var targetConn = opt.get().getCurrentServer();
                    if (targetConn.isEmpty()) break;
                    RegisteredServer target = targetConn.get().getServer();
                    byte[] out = encode(w -> {
                        w.writeUTF("forward");
                        w.writeUTF(targetPlayer);
                        w.write(rest);
                    });
                    target.sendPluginMessage(SMPCoreVelocity.CHANNEL, out);
                }
                default -> { /* ignore */ }
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error handling plugin message", e);
        }
    }

    private void relayToOtherBackends(String sourceServer, byte[] payload) {
        for (RegisteredServer rs : plugin.getServer().getAllServers()) {
            String name = rs.getServerInfo().getName();
            if (name.equalsIgnoreCase(sourceServer)) continue;
            if (rs.getPlayersConnected().isEmpty()) continue;
            rs.sendPluginMessage(SMPCoreVelocity.CHANNEL, payload);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws java.io.IOException;
    }

    private byte[] encode(Writer w) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        w.write(out);
        return bytes.toByteArray();
    }

    public record RosterEntry(String name, String server, String prefix) {}
}
