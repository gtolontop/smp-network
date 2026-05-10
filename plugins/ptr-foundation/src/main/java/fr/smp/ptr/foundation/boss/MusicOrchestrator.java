package fr.smp.ptr.foundation.boss;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Plays / stops / crossfades boss music tracks per player.
 *
 * <p>Tracks are addressed by their {@code jukebox_song} {@link NamespacedKey}.
 * Music is played as a {@link Sound} on the {@code MUSIC} category so the
 * client respects the player's Music slider.
 *
 * <p>This is the foundation hook; concrete music files ship in the resource
 * pack (out of scope for the foundation).
 */
public final class MusicOrchestrator {

    /** Track currently playing per player. */
    private final ConcurrentMap<UUID, NamespacedKey> playing = new ConcurrentHashMap<>();

    /** Start track {@code song} for {@code player}, stopping anything else first. */
    public void play(@NotNull Player player, @NotNull NamespacedKey song) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(song, "song");
        stop(player);
        Sound sound =
                Sound.sound(
                        song.toString().contains(":")
                                ? net.kyori.adventure.key.Key.key(
                                        song.getNamespace(), song.getKey())
                                : net.kyori.adventure.key.Key.key("minecraft", song.getKey()),
                        Sound.Source.MUSIC,
                        1.0f,
                        1.0f);
        player.playSound(sound);
        playing.put(player.getUniqueId(), song);
    }

    /** Stop whatever is playing for {@code player}, if anything. */
    public void stop(@NotNull Player player) {
        Objects.requireNonNull(player, "player");
        NamespacedKey previous = playing.remove(player.getUniqueId());
        if (previous == null) {
            return;
        }
        player.stopSound(
                SoundStop.named(
                        net.kyori.adventure.key.Key.key(previous.getNamespace(), previous.getKey())));
    }

    /** Stop {@code song} for every online player. */
    public void stopAll(@NotNull NamespacedKey song) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (song.equals(playing.get(player.getUniqueId()))) {
                stop(player);
            }
        }
    }

    /** Currently-playing track for {@code player}, or {@code null}. */
    public @org.jetbrains.annotations.Nullable NamespacedKey currentSong(@NotNull Player player) {
        return playing.get(player.getUniqueId());
    }
}
