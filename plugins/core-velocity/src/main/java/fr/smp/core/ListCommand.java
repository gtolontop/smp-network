package fr.smp.core;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import java.util.stream.Collectors;

public class ListCommand implements SimpleCommand {
    private final SMPCoreVelocity plugin;

    public ListCommand(SMPCoreVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var mm = plugin.getMiniMessage();
        var source = invocation.source();

        source.sendMessage(mm.deserialize("<gray>━━━━━━━━━━ <white>Joueurs en ligne</white> ━━━━━━━━━━</gray>"));

        int total = 0;
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            var players = server.getPlayersConnected();
            total += players.size();
            String names = players.stream()
                .map(Player::getUsername)
                .collect(Collectors.joining("<gray>, </gray><white>"));

            String serverName = server.getServerInfo().getName();
            source.sendMessage(mm.deserialize(
                "<green>" + serverName + "</green> <gray>(" + players.size() + ")</gray>" +
                (players.isEmpty() ? "" : " <white>" + names + "</white>")
            ));
        }

        source.sendMessage(mm.deserialize("<gray>Total: <white>" + total + "</white> joueur(s)</gray>"));
    }
}
