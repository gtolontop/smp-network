package fr.smp.core;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class ServerCommand implements SimpleCommand {
    private final SMPCoreVelocity plugin;
    private final String targetServer;

    public ServerCommand(SMPCoreVelocity plugin, String targetServer) {
        this.plugin = plugin;
        this.targetServer = targetServer;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Players only."));
            return;
        }

        var mm = plugin.getMiniMessage();
        var serverOpt = plugin.getServer().getServer(targetServer);

        if (serverOpt.isEmpty()) {
            player.sendMessage(mm.deserialize("<red>Serveur introuvable.</red>"));
            return;
        }

        var server = serverOpt.get();

        // Check if already on that server
        if (player.getCurrentServer().isPresent() &&
            player.getCurrentServer().get().getServerInfo().getName().equals(targetServer)) {
            player.sendMessage(mm.deserialize("<yellow>Tu es déjà sur ce serveur !</yellow>"));
            return;
        }

        player.sendMessage(mm.deserialize("<gray>Connexion à <white>" + targetServer + "</white>...</gray>"));
        player.createConnectionRequest(server).fireAndForget();
    }
}
