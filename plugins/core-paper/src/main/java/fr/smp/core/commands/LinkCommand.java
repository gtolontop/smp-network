package fr.smp.core.commands;

import com.google.gson.JsonObject;
import fr.smp.core.SMPCore;
import fr.smp.core.discord.BridgeClient;
import fr.smp.core.discord.DiscordBridge;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * /link &lt;code&gt; — receives a link code created by the Discord /link command
 * and sends it to the bot over the bridge. The bot responds with a link_result
 * packet which completes the CompletableFuture below.
 */
public class LinkCommand implements CommandExecutor {

    private static final long TIMEOUT_SEC = 10;
    private static final Map<UUID, CompletableFuture<LinkResult>> pending = new ConcurrentHashMap<>();

    private final SMPCore plugin;

    public LinkCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    public record LinkResult(boolean ok, String discordTag, String error) {}

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.err("Commande joueur uniquement."));
            return true;
        }

        if (args.length < 1) {
            p.sendMessage(Msg.err("Utilisation : /link <code>"));
            p.sendMessage(Msg.info("Obtiens ton code avec /discord link sur le serveur Discord."));
            return true;
        }

        String code = args[0].toUpperCase().trim();
        if (code.length() != 6) {
            p.sendMessage(Msg.err("Code invalide. Utilise les 6 caractères fournis par /discord link."));
            return true;
        }

        UUID uuid = p.getUniqueId();

        // Cancel any pending attempt for this player.
        pending.remove(uuid);

        DiscordBridge bridge = plugin.discordBridge();
        BridgeClient client = bridge != null ? bridge.getClient() : null;
        if (client == null || !client.isConnected()) {
            p.sendMessage(Msg.err("Le bridge Discord est hors-ligne. Réessaie dans quelques secondes."));
            return true;
        }

        p.sendMessage(Msg.info("Vérification du code…"));

        CompletableFuture<LinkResult> future = new CompletableFuture<>();
        pending.put(uuid, future);

        JsonObject pkt = new JsonObject();
        pkt.addProperty("kind", "link_attempt");
        pkt.addProperty("code", code);
        pkt.addProperty("uuid", uuid.toString());
        pkt.addProperty("name", p.getName());
        client.send(pkt);

        future.completeOnTimeout(
                new LinkResult(false, "", "Temps écoulé — réessaie /link " + code),
                TIMEOUT_SEC, TimeUnit.SECONDS);

        future.whenComplete((result, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(uuid);
            if (!p.isOnline()) return;
            LinkResult r = result != null ? result
                    : new LinkResult(false, "", "Erreur interne lors du link.");
            if (r.ok()) {
                p.sendMessage(Msg.ok("Compte lié à Discord : <aqua>" + r.discordTag() + "</aqua>"));
            } else {
                p.sendMessage(Msg.err(r.error() != null && !r.error().isBlank()
                        ? r.error() : "Code invalide ou expiré."));
            }
        }));

        return true;
    }

    /** Called by RpcHandler when a link_result packet arrives. */
    public static void resolveLinkResult(UUID uuid, boolean ok, String discordTag, String error) {
        CompletableFuture<LinkResult> f = pending.remove(uuid);
        if (f != null) {
            f.complete(new LinkResult(ok, discordTag, error != null ? error : ""));
        }
    }
}
