package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.BountyManager;
import fr.smp.core.managers.TeamManager;
import fr.smp.core.utils.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final SMPCore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .extractUrls()
            .build();

    private static final Map<String, String> EMOJI = Map.ofEntries(
            Map.entry(":heart:", "❤"),
            Map.entry(":star:", "★"),
            Map.entry(":fire:", "🔥"),
            Map.entry(":skull:", "☠"),
            Map.entry(":check:", "✔"),
            Map.entry(":cross:", "✖"),
            Map.entry(":arrow:", "➤"),
            Map.entry(":diamond:", "◆"),
            Map.entry(":sword:", "⚔"),
            Map.entry(":shield:", "🛡"),
            Map.entry(":music:", "♪"),
            Map.entry(":sun:", "☀"),
            Map.entry(":moon:", "☾"),
            Map.entry(":cloud:", "☁"),
            Map.entry(":snow:", "❄"),
            Map.entry(":smile:", "☺"),
            Map.entry(":sad:", "☹"),
            Map.entry(":warn:", "⚠"),
            Map.entry(":crown:", "♛"),
            Map.entry(":coin:", "⛃"),
            Map.entry(":peace:", "☮"),
            Map.entry(":yinyang:", "☯"),
            Map.entry(":shrug:", "¯\\_(ツ)_/¯")
    );

    public ChatListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (plugin.isChatLocked() && !event.getPlayer().hasPermission("smp.admin")
                && !event.getPlayer().hasPermission("smp.chat.bypass-lock")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Msg.err("Le chat est actuellement <red>verrouillé</red>."));
            return;
        }

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        String name = event.getPlayer().getName();
        PlayerData d = plugin.players().get(event.getPlayer());
        String displayName = (d != null && d.nickname() != null && !d.nickname().isEmpty()) ? d.nickname() : name;

        String teamTag = "";
        if (d != null && d.teamId() != null) {
            TeamManager.Team t = plugin.teams().get(d.teamId());
            if (t != null) teamTag = t.color() + "[" + t.tag() + "]<reset> ";
        }

        String rankPrefix = "";
        if (plugin.permissions() != null) {
            rankPrefix = plugin.permissions().prefixOf(event.getPlayer().getUniqueId());
            if (rankPrefix == null) rankPrefix = "";
            if (!rankPrefix.isEmpty() && !rankPrefix.endsWith(" ")) rankPrefix += " ";
        }

        String huntedPrefix = "";
        if (plugin.hunted() != null && plugin.hunted().isHunted(event.getPlayer().getUniqueId())) {
            huntedPrefix = "<dark_red>[<red><bold>CHASSÉ</bold></red>";
            if (plugin.bounties() != null) {
                BountyManager.Bounty b = plugin.bounties().get(event.getPlayer().getUniqueId());
                if (b != null && b.amount() > 0) {
                    huntedPrefix += "<dark_red> • <gold>$" + Msg.money(b.amount()) + "</gold>";
                }
            }
            huntedPrefix += "<dark_red>]</dark_red> ";
        }

        boolean canColor = event.getPlayer().hasPermission("smp.chat.color");
        boolean canFormat = event.getPlayer().hasPermission("smp.chat.format");
        String msgText = applyEmojis(raw);
        Component messageComp = renderMessage(msgText, canColor, canFormat);

        String format = plugin.getConfig().getString("chat.format",
                "%rank%%hunted%%tag%<gray>%player%</gray> <dark_gray>»</dark_gray> ")
                .replace("%rank%", rankPrefix)
                .replace("%hunted%", huntedPrefix)
                .replace("%tag%", teamTag)
                .replace("%player%", displayName)
                .replace("%message%", "");
        boolean isAdmin = event.getPlayer().hasPermission("smp.admin");
        Component base = mm.deserialize(format).append(addMentionHovers(messageComp, isAdmin));
        Component hover = buildHover(name, d);
        Component finalLine = base.hoverEvent(HoverEvent.showText(hover));

        // Play mention sound to pinged players
        playMentionSounds(raw, event.getPlayer());

        // Local render — only local viewers receive via the event renderer.
        event.renderer((source, sourceDisplayName, msg, audience) -> finalLine);

        // Cross-server broadcast: serialize the already-rendered line to MiniMessage
        // and push through the proxy; other backends will deserialize and show.
        String rendered = MiniMessage.miniMessage().serialize(finalLine);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            if (plugin.getMessageChannel() != null) {
                plugin.getMessageChannel().sendChat(name, rendered);
            }
        });

        plugin.logs().log(LogCategory.CHAT, event.getPlayer(), raw);
    }

    private Component renderMessage(String text, boolean colors, boolean format) {
        if (!colors) {
            return Component.text(text).color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
        }
        Component comp = legacyAmp.deserialize(text);
        if (!format) {
            comp = comp.decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.STRIKETHROUGH, false)
                    .decoration(TextDecoration.OBFUSCATED, false)
                    .decoration(TextDecoration.UNDERLINED, false);
        }
        return comp;
    }

    private String applyEmojis(String text) {
        for (Map.Entry<String, String> e : EMOJI.entrySet()) {
            if (text.contains(e.getKey())) text = text.replace(e.getKey(), e.getValue());
        }
        return text;
    }

    private Component addMentionHovers(Component message, boolean isAdmin) {
        if (isAdmin) {
            message = message.replaceText(TextReplacementConfig.builder()
                    .match(Pattern.compile("@@everyone", Pattern.CASE_INSENSITIVE))
                    .replacement(Component.text("@everyone")
                            .color(NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true))
                    .build());
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            String pName = online.getName();
            PlayerData pd = plugin.players().get(online);
            String nick = (pd != null && pd.nickname() != null && !pd.nickname().isEmpty()) ? pd.nickname() : null;
            Component hover = buildHover(pName, pd);
            message = message.replaceText(TextReplacementConfig.builder()
                    .match(Pattern.compile("\\b" + Pattern.quote(pName) + "\\b", Pattern.CASE_INSENSITIVE))
                    .replacement(Component.text(pName)
                            .color(NamedTextColor.YELLOW)
                            .hoverEvent(HoverEvent.showText(hover)))
                    .build());
            if (nick != null && !nick.equalsIgnoreCase(pName)) {
                message = message.replaceText(TextReplacementConfig.builder()
                        .match(Pattern.compile("\\b" + Pattern.quote(nick) + "\\b", Pattern.CASE_INSENSITIVE))
                        .replacement(Component.text(nick)
                                .color(NamedTextColor.YELLOW)
                                .hoverEvent(HoverEvent.showText(hover)))
                        .build());
            }
        }
        return message;
    }

    private Component buildHover(String playerName, PlayerData d) {
        if (d == null) return Msg.mm("<gray>" + playerName + "</gray>");
        String team = "<gray>No team</gray>";
        if (d.teamId() != null) {
            TeamManager.Team t = plugin.teams().get(d.teamId());
            if (t != null) team = t.color() + "[" + t.tag() + "]<reset>";
        }
        String body = "<gradient:#67e8f9:#a78bfa><bold>" + playerName + "</bold></gradient>\n"
                + "<dark_gray>──────────────</dark_gray>\n"
                + "<green>$ Money</green> <white>" + Msg.money(d.money()) + "</white>\n"
                + "<aqua>◆ Saphirs</aqua> <white>" + d.shards() + "</white>\n"
                + "<red>⚔ Kills</red> <white>" + d.kills() + "</white>\n"
                + "<red>☠ Deaths</red> <white>" + d.deaths() + "</white>\n"
                + "<yellow>⏱ Playtime</yellow> <white>" + Msg.duration(d.totalPlaytimeWithSession()) + "</white>\n"
                + "<blue>⚑ Team</blue> " + team;
        return mm.deserialize(body);
    }

    private void playMentionSounds(String text, Player sender) {
        boolean everyonePing = sender.hasPermission("smp.admin")
                && Pattern.compile("@@everyone", Pattern.CASE_INSENSITIVE).matcher(text).find();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(sender.getUniqueId())) continue;
            if (!everyonePing) {
                PlayerData pd = plugin.players().get(online);
                String realName = online.getName();
                String nick = (pd != null && pd.nickname() != null && !pd.nickname().isEmpty()) ? pd.nickname() : null;
                boolean matched = Pattern.compile("\\b" + Pattern.quote(realName) + "\\b", Pattern.CASE_INSENSITIVE)
                        .matcher(text).find();
                if (!matched && nick != null) {
                    matched = Pattern.compile("\\b" + Pattern.quote(nick) + "\\b", Pattern.CASE_INSENSITIVE)
                            .matcher(text).find();
                }
                if (!matched) continue;
            }
            final Player target = online;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isOnline()) {
                    target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f);
                }
            });
        }
    }
}
