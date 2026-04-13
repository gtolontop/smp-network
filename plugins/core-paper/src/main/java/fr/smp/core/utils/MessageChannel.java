package fr.smp.core.utils;

import fr.smp.core.SMPCore;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Sends plugin messages on the "smp:core" channel to the Velocity proxy.
 *
 * <p>Message format for a transfer request:
 * <pre>
 *   writeUTF("transfer")
 *   writeUTF(playerName)
 *   writeUTF(targetServer)
 * </pre>
 *
 * The Velocity-side plugin must register an incoming handler on the same
 * channel and call ServerTransfer / ConnectionRequest accordingly.
 */
public class MessageChannel {

    public static final String CHANNEL = "smp:core";

    private final SMPCore plugin;

    public MessageChannel(SMPCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getLogger().info("Plugin messaging channel '" + CHANNEL + "' registered.");
    }

    /**
     * Requests the proxy to transfer {@code player} to {@code targetServer}.
     *
     * <p>The message is delivered via the player's connection, so the player
     * must be online at the moment this is called.
     */
    public void sendTransfer(Player player, String targetServer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);

            out.writeUTF("transfer");
            out.writeUTF(player.getName());
            out.writeUTF(targetServer);

            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            plugin.getLogger().info("Transfer request sent: " + player.getName() + " -> " + targetServer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send transfer request for "
                    + player.getName() + ": " + e.getMessage());
        }
    }
}
