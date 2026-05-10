package fr.smp.ptr.foundation.skill.mechanic;

import fr.smp.ptr.foundation.platform.SchedulerService;
import fr.smp.ptr.foundation.skill.PtrSkillContext;
import fr.smp.ptr.foundation.skill.PtrSkillMechanic;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code message{m="..."}} — send {@code message} to every player target.
 * The chat audience defaults to the entity targets; if none are players,
 * the mechanic is a no-op (matches MM behaviour).
 */
public final class MessageMechanic implements PtrSkillMechanic {

    private final Component message;

    public MessageMechanic(@NotNull Component message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    @Override
    public void execute(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull PtrSkillContext ctx) {
        for (Entity e : ctx.entityTargets()) {
            if (e instanceof Player p && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    @Override
    public @NotNull String label() {
        return "message";
    }
}
