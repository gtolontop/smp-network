package fr.smp.core.listeners;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.logging.LogCategory;
import fr.smp.core.managers.BountyManager;
import fr.smp.core.utils.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DeathListener implements Listener {

    private final SMPCore plugin;

    public DeathListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        PlayerData vd = plugin.players().get(victim);
        if (vd != null) vd.incrementDeaths();
        plugin.logs().log(LogCategory.DEATH, victim, "death cause=" +
                (victim.getLastDamageCause() != null ? victim.getLastDamageCause().getCause() : "?"));

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            PlayerData kd = plugin.players().get(killer);
            if (kd != null) {
                kd.incrementKills();
                bumpDailyKills(kd);
            }
            plugin.logs().log(LogCategory.COMBAT, killer, "kill " + victim.getName());
            payoutBounty(victim, killer);
            if (plugin.hunted() != null) plugin.hunted().onKill(killer, kd);
        }
        if (plugin.hunted() != null) plugin.hunted().onHuntedDeath(victim);
        if (plugin.combat() != null) plugin.combat().untag(victim);
    }

    private void bumpDailyKills(PlayerData d) {
        String today = LocalDate.now().toString();
        if (!today.equals(d.dailyKillsDate())) {
            d.setDailyKills(0);
            d.setDailyKillsDate(today);
        }
        d.incrementDailyKills();
    }

    private void payoutBounty(Player victim, Player killer) {
        if (plugin.bounties() == null) return;
        BountyManager.Bounty b = plugin.bounties().get(victim.getUniqueId());
        if (b == null || b.amount() <= 0) return;
        BountyManager.Contribution top = plugin.bounties().biggestContributor(victim.getUniqueId());
        plugin.bounties().remove(victim.getUniqueId());
        plugin.economy().deposit(killer.getUniqueId(), b.amount(),
                "bounty kill " + victim.getName());
        killer.sendMessage(Msg.ok("<green>Tu as collecté la prime de <gold>$" + Msg.money(b.amount()) +
                "</gold> sur <white>" + victim.getName() + "</white> !</green>"));
        Bukkit.broadcast(Msg.mm("<dark_red>\u2620 <red><white>" + killer.getName() +
                "</white> a collecté la prime de <gold>$" + Msg.money(b.amount()) +
                "</gold> sur <white>" + victim.getName() + "</white> !</red>"));
        plugin.logs().log(LogCategory.ECONOMY,
                "bounty.payout target=" + victim.getName() + " killer=" + killer.getName() + " amount=" + b.amount());

        if (top != null) {
            awardTrophyHead(victim, killer, top, b.amount());
        }
    }

    private void awardTrophyHead(Player victim, Player killer, BountyManager.Contribution top, double totalAmount) {
        ItemStack head = buildTrophyHead(victim, top, totalAmount);
        Player winner = Bukkit.getPlayer(top.issuer());
        if (winner != null && winner.isOnline()) {
            giveOrDrop(winner, head);
            winner.sendMessage(Msg.ok("<gold>La tête de <white>" + victim.getName() +
                    "</white> est à toi — tu avais posé la plus grosse prime (<yellow>$" +
                    Msg.money(top.amount()) + "</yellow>).</gold>"));
        } else {
            giveOrDrop(killer, head);
            killer.sendMessage(Msg.ok("<gold>Le plus gros contributeur (<white>" + top.issuerName() +
                    "</white>) n'est pas connecté.</gold> <gray>La tête trophée t'est confiée, remets-la en mains propres.</gray>"));
        }
        plugin.logs().log(LogCategory.ECONOMY,
                "bounty.trophy target=" + victim.getName() + " winner=" + top.issuerName() +
                        " contrib=" + top.amount());
    }

    private ItemStack buildTrophyHead(Player victim, BountyManager.Contribution top, double totalAmount) {
        MiniMessage mm = MiniMessage.miniMessage();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            sm.setOwningPlayer(victim);
        }
        meta.displayName(mm.deserialize("<!italic><gradient:#ffd700:#f85032><bold>Trophée: " +
                victim.getName() + "</bold></gradient>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<!italic> "));
        lore.add(mm.deserialize("<!italic><gray>Prime collectée: <gold>$" + Msg.money(totalAmount) + "</gold></gray>"));
        lore.add(mm.deserialize("<!italic><gray>Commanditaire principal: <aqua>" + top.issuerName() + "</aqua></gray>"));
        lore.add(mm.deserialize("<!italic><gray>Mise: <gold>$" + Msg.money(top.amount()) + "</gold></gray>"));
        lore.add(mm.deserialize("<!italic> "));
        lore.add(mm.deserialize("<!italic><dark_gray>Souvenir de la chasse.</dark_gray>"));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private void giveOrDrop(Player p, ItemStack item) {
        var leftover = p.getInventory().addItem(item);
        for (ItemStack remaining : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), remaining);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) return;

        Location fallback = plugin.spawns().spawn();
        if (fallback != null) event.setRespawnLocation(fallback);

        Player p = event.getPlayer();

        // Pas de lit / pas d'ancre : quand le joueur reviendra en survie,
        // on veut qu'il soit RTP (pas au spawn 0/100/0). Le JoinListener
        // passe par la branche RTP uniquement si survivalJoined == false.
        PlayerData data = plugin.players().get(p);
        if (data != null) data.setSurvivalJoined(false);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.getMessageChannel() != null) {
                plugin.getMessageChannel().sendTransfer(p, "lobby");
            }
        }, 5L);
    }
}
