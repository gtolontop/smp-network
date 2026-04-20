package fr.smp.core;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;

public class ServersCommand implements SimpleCommand {

    private final SMPCoreVelocity plugin;

    public ServersCommand(SMPCoreVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var mm = plugin.getMiniMessage();
        var source = invocation.source();

        source.sendMessage(mm.deserialize("<gray>━━━━━━ <white>État du réseau</white> ━━━━━━</gray>"));

        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            String name = server.getServerInfo().getName();
            server.ping().whenComplete((ping, err) -> {
                if (err != null || ping == null) {
                    source.sendMessage(mm.deserialize(
                        "<gray>•</gray> <white>" + name + "</white> <dark_gray>—</dark_gray> <red>hors ligne</red>"
                    ));
                    return;
                }
                int online = ping.getPlayers().map(ServerPing.Players::getOnline).orElse(server.getPlayersConnected().size());
                int max = ping.getPlayers().map(ServerPing.Players::getMax).orElse(0);
                source.sendMessage(mm.deserialize(
                    "<gray>•</gray> <white>" + name + "</white> <dark_gray>—</dark_gray> <green>en ligne</green> <gray>(" + online + "/" + max + ")</gray>"
                ));
            });
        }
    }
}
