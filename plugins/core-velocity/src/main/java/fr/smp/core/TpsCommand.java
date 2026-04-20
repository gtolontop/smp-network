package fr.smp.core;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public class TpsCommand implements SimpleCommand {

    private final SMPCoreVelocity plugin;

    public TpsCommand(SMPCoreVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var mm = plugin.getMiniMessage();
        var source = invocation.source();

        source.sendMessage(mm.deserialize("<gray>━━━━━━ <white>TPS réseau</white> ━━━━━━</gray>"));

        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            String name = server.getServerInfo().getName();
            ServerStats stats = plugin.getStats(name);

            if (stats == null || !stats.isFresh()) {
                int connected = server.getPlayersConnected().size();
                source.sendMessage(mm.deserialize(
                    "<gray>•</gray> <white>" + name + "</white> <dark_gray>—</dark_gray> <red>pas de données</red> <gray>(" + connected + " joueurs)</gray>"
                ));
                continue;
            }

            String tpsColor;
            if (stats.tps >= 19.5) tpsColor = "green";
            else if (stats.tps >= 17.0) tpsColor = "yellow";
            else tpsColor = "red";

            String tpsStr = String.format("%.2f", Math.min(20.0, stats.tps));
            source.sendMessage(mm.deserialize(
                "<gray>•</gray> <white>" + name + "</white> " +
                "<dark_gray>—</dark_gray> <" + tpsColor + ">" + tpsStr + "</" + tpsColor + ">" +
                " <gray>(" + stats.online + "/" + stats.max + " joueurs)</gray>"
            ));
        }
    }
}
