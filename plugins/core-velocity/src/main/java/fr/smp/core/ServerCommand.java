package fr.smp.core;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class ServerCommand implements SimpleCommand {
    private final SMPCoreVelocity plugin;
    private final String targetServer;
    private final String displayName;

    public ServerCommand(SMPCoreVelocity plugin, String targetServer) {
        this.plugin = plugin;
        this.targetServer = targetServer;
        this.displayName = switch (targetServer.toLowerCase()) {
            case "lobby" -> "Lobby";
            case "survival" -> "Survival";
            case "ptr" -> "PTR";
            default -> Character.toUpperCase(targetServer.charAt(0)) + targetServer.substring(1);
        };
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

        if (player.getCurrentServer().isPresent() &&
            player.getCurrentServer().get().getServerInfo().getName().equals(targetServer)) {
            player.sendMessage(mm.deserialize("<yellow>Tu es déjà sur <white>" + displayName + "</white> !</yellow>"));
            return;
        }

        player.sendMessage(mm.deserialize("<gray>Connexion à <white>" + displayName + "</white>...</gray>"));

        server.ping().whenComplete((ping, ex) -> {
            if (ex != null || ping == null) {
                if (player.isActive()) {
                    player.sendMessage(mm.deserialize(
                        "<red>✗</red> <gray><white>" + displayName + "</white> est actuellement <red>hors ligne</red>. Réessaie plus tard.</gray>"
                    ));
                }
                return;
            }
            player.createConnectionRequest(server).connect().thenAccept(result -> {
                if (!player.isActive()) return;
                if (result.getStatus() == ConnectionRequestBuilder.Status.SUCCESS) return;
                String reason = switch (result.getStatus()) {
                    case ALREADY_CONNECTED -> "<yellow>Tu es déjà connecté à <white>" + displayName + "</white>.</yellow>";
                    case CONNECTION_IN_PROGRESS -> "<yellow>Une connexion est déjà en cours...</yellow>";
                    case SERVER_DISCONNECTED -> "<red>✗</red> <gray><white>" + displayName + "</white> a refusé la connexion.</gray>";
                    default -> "<red>✗</red> <gray>Impossible de rejoindre <white>" + displayName + "</white>.</gray>";
                };
                player.sendMessage(mm.deserialize(reason));
            });
        });
    }
}
