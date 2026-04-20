package fr.smp.core;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public class MaxPlayersCommand implements SimpleCommand {

    private final SMPCoreVelocity plugin;

    public MaxPlayersCommand(SMPCoreVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var mm = plugin.getMiniMessage();
        var source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 2) {
            source.sendMessage(mm.deserialize("<yellow>Usage : /nmaxplayers <serveur> <n></yellow>"));
            return;
        }

        String serverName = args[0];
        int max;
        try {
            max = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            source.sendMessage(mm.deserialize("<red>Nombre invalide.</red>"));
            return;
        }
        if (max < 1 || max > 1000) {
            source.sendMessage(mm.deserialize("<red>Valeur hors bornes (1-1000).</red>"));
            return;
        }

        var target = plugin.getServer().getServer(serverName);
        if (target.isEmpty()) {
            source.sendMessage(mm.deserialize("<red>Serveur introuvable.</red>"));
            return;
        }
        RegisteredServer server = target.get();

        Player carrier = server.getPlayersConnected().stream().findFirst().orElse(null);
        if (carrier == null) {
            source.sendMessage(mm.deserialize("<red>Aucun joueur sur ce serveur pour porter le message. Réessaie quand au moins 1 joueur y est.</red>"));
            return;
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("setmaxplayers");
            out.writeInt(max);

            ServerConnection connection = carrier.getCurrentServer().orElse(null);
            if (connection == null) {
                source.sendMessage(mm.deserialize("<red>Échec : pas de connexion active.</red>"));
                return;
            }
            connection.sendPluginMessage(SMPCoreVelocity.CHANNEL, bytes.toByteArray());

            source.sendMessage(mm.deserialize(
                "<green>Max joueurs de <white>" + serverName + "</white> défini à <white>" + max + "</white>.</green>"
            ));
        } catch (Exception e) {
            source.sendMessage(mm.deserialize("<red>Erreur : " + e.getMessage() + "</red>"));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("smp.admin");
    }
}
