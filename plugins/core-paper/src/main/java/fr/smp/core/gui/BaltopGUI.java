package fr.smp.core.gui;

import fr.smp.core.SMPCore;
import fr.smp.core.managers.LeaderboardManager;
import org.bukkit.entity.Player;

public class BaltopGUI {

    private final SMPCore plugin;

    public BaltopGUI(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        new LeaderboardGUI(plugin).open(viewer, LeaderboardManager.Category.MONEY, LeaderboardManager.Scope.SOLO, 0);
    }
}
