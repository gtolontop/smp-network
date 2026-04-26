package fr.smp.anticheat.clients;

import fr.smp.anticheat.AntiCheatPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Heuristique freecam: un client freecam découple la caméra du corps. Côté serveur
 * on observe alors des paquets de rotation-seulement pendant de longues périodes
 * alors que le joueur n'est pas en AFK (pas de cooldown activity + interactions possibles).
 *
 * Cette détection est volontairement conservatrice — le cas légitime (AFK qui bouge
 * la souris) ressemble au cas cheat, donc on exige un seuil élevé ET on ignore les
 * joueurs en créa/spectateur ou qui viennent de bouger.
 */
public final class FreecamDetector implements Listener {

    /** Nombre de ticks consécutifs de rotation-only avant flag. ~8s à 20 tps. */
    private static final int DEFAULT_THRESHOLD_TICKS = 160;

    private final AntiCheatPlugin plugin;
    private final ClientDetectionModule module;
    private final int thresholdTicks;

    public FreecamDetector(AntiCheatPlugin plugin, ClientDetectionModule module, int thresholdTicks) {
        this.plugin = plugin;
        this.module = module;
        this.thresholdTicks = thresholdTicks <= 0 ? DEFAULT_THRESHOLD_TICKS : thresholdTicks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        ClientProfile profile = module.getOrCreate(p);

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        boolean positionChanged = dx != 0.0 || dy != 0.0 || dz != 0.0;
        boolean rotationChanged = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();

        if (positionChanged) {
            profile.resetRotOnlyTicks();
            profile.touchMove();
            return;
        }

        if (rotationChanged) {
            profile.incRotOnlyTicks();
            if (profile.rotOnlyTicks() > thresholdTicks && !p.isSleeping() && !p.isInsideVehicle()) {
                // Le joueur est peut-être juste en train de regarder autour de lui sans bouger —
                // on ne flag que si la fenêtre d'observation est exceptionnellement longue.
                if (profile.rotOnlyTicks() >= thresholdTicks + 1) {
                    // Skip: les joueurs avec un outil de building autorisé (litematica,
                    // schematica) peuvent légitimement utiliser le freecam de ce mod
                    // pour prévisualiser un schematic. Un vrai freecam cheat isolé ne
                    // matchera pas l'allowlist.
                    if (CheatSignatures.hasAllowedChannel(profile.channels())) return;
                    module.raiseFlag(p, profile, CheatSignatures.Severity.GREY,
                            "movement", "rotation-only>" + thresholdTicks + "t");
                }
            }
        }
    }

    public int thresholdTicks() { return thresholdTicks; }
}
