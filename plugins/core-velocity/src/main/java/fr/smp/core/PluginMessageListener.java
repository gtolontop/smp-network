package fr.smp.core;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class PluginMessageListener {
    private final SMPCoreVelocity plugin;

    public PluginMessageListener(SMPCoreVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(SMPCoreVelocity.CHANNEL)) return;

        // Only handle messages from backend servers
        if (!(event.getSource() instanceof ServerConnection connection)) return;

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
            String action = in.readUTF();

            if ("transfer".equals(action)) {
                String playerName = in.readUTF();
                String targetServer = in.readUTF();

                plugin.getServer().getPlayer(playerName).ifPresent(player -> {
                    plugin.getServer().getServer(targetServer).ifPresent(server -> {
                        player.createConnectionRequest(server).fireAndForget();
                    });
                });
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error handling plugin message", e);
        }
    }
}
