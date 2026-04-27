package fr.smp.core.managers;

import fr.smp.core.SMPCore;
import fr.smp.core.data.PlayerData;
import fr.smp.core.data.PlayerDataManager;
import fr.smp.core.logging.LogCategory;

import java.util.UUID;

public class EconomyManager {

    private final SMPCore plugin;
    private final PlayerDataManager players;

    public EconomyManager(SMPCore plugin, PlayerDataManager players) {
        this.plugin = plugin;
        this.players = players;
    }

    private PlayerData fetch(UUID uuid) {
        PlayerData online = players.get(uuid);
        if (online != null) return online;
        return players.loadOffline(uuid);
    }

    private void persist(PlayerData d) {
        if (d != null) players.save(d);
    }

    public double balance(UUID uuid) {
        PlayerData d = fetch(uuid);
        return d != null ? d.money() : 0;
    }

    public boolean has(UUID uuid, double amount) {
        return balance(uuid) >= amount;
    }

    public boolean withdraw(UUID uuid, double amount, String reason) {
        PlayerData d = fetch(uuid);
        if (d == null || d.money() < amount) return false;
        d.addMoney(-amount);
        persist(d);
        plugin.logs().log(LogCategory.ECONOMY, "withdraw " + d.name() + " -" + amount + " (" + reason + ")");
        return true;
    }

    public void deposit(UUID uuid, double amount, String reason) {
        PlayerData d = fetch(uuid);
        if (d == null) return;
        d.addMoney(amount);
        persist(d);
        plugin.logs().log(LogCategory.ECONOMY, "deposit " + d.name() + " +" + amount + " (" + reason + ")");
    }

    public boolean transfer(UUID from, UUID to, double amount) {
        if (!has(from, amount)) return false;
        withdraw(from, amount, "transfer");
        deposit(to, amount, "transfer");
        return true;
    }

    public void setBalance(UUID uuid, double amount) {
        PlayerData d = fetch(uuid);
        if (d == null) return;
        d.setMoney(amount);
        persist(d);
    }

}
