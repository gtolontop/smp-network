package fr.smp.ptr.foundation.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.smp.ptr.foundation.PtrServices;
import fr.smp.ptr.foundation.config.PtrConfigService;
import fr.smp.ptr.foundation.platform.RegionLocator;
import fr.smp.ptr.foundation.registry.PtrBlockRegistry;
import fr.smp.ptr.foundation.registry.PtrDamageTypeRegistry;
import fr.smp.ptr.foundation.registry.PtrEnchantRegistry;
import fr.smp.ptr.foundation.registry.PtrItemRegistry;
import fr.smp.ptr.foundation.registry.PtrMobRegistry;
import fr.smp.ptr.foundation.registry.PtrRegistry;
import fr.smp.ptr.foundation.skill.PtrSkillRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Brigadier root {@code /ptrf}.
 *
 * <p>Registered via the Paper Plugin API ({@code LifecycleEvents.COMMANDS}),
 * not the legacy {@code PluginCommand} system. The literal {@code ptr} is
 * deliberately avoided — it's a Velocity-level alias for the server-switch
 * command, registered by core-velocity at {@code SMPCoreVelocity#onEnable}.
 *
 * <p>Sub-commands:
 *
 * <ul>
 *   <li>{@code /ptrf info} — version + service inventory
 *   <li>{@code /ptrf reload} — re-read {@code foundation.yml}
 *   <li>{@code /ptrf registry list &lt;type&gt;} — enumerate one registry
 *   <li>{@code /ptrf registry dump &lt;id&gt;} — describe one entry
 *   <li>{@code /ptrf debug region} — print region-ownership for the sender
 *   <li>{@code /ptrf debug pdc} — list PDC keys on the held item
 * </ul>
 */
public final class PtrFoundationCommand {

    private final PtrServices services;

    public PtrFoundationCommand(@NotNull PtrServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** Build the Brigadier root node. */
    public @NotNull LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ptrf")
                .requires(s -> s.getSender().hasPermission("ptr.cmd.use"))
                .then(Commands.literal("info").executes(this::runInfo))
                .then(
                        Commands.literal("reload")
                                .requires(s -> s.getSender().hasPermission("ptr.cmd.reload"))
                                .executes(this::runReload))
                .then(
                        Commands.literal("registry")
                                .requires(s -> s.getSender().hasPermission("ptr.cmd.registry"))
                                .then(
                                        Commands.literal("list")
                                                .then(
                                                        Commands.argument(
                                                                        "type",
                                                                        StringArgumentType.word())
                                                                .suggests(suggestRegistryTypes())
                                                                .executes(this::runRegistryList)))
                                .then(
                                        Commands.literal("dump")
                                                .then(
                                                        Commands.argument(
                                                                        "id",
                                                                        StringArgumentType.string())
                                                                .executes(this::runRegistryDump))))
                .then(
                        Commands.literal("debug")
                                .requires(s -> s.getSender().hasPermission("ptr.cmd.debug"))
                                .then(Commands.literal("region").executes(this::runDebugRegion))
                                .then(Commands.literal("pdc").executes(this::runDebugPdc)))
                .build();
    }

    private int runInfo(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        Component out =
                Component.text("PtrFoundation", NamedTextColor.GOLD)
                        .append(Component.text(" — services=" + services.size(), NamedTextColor.GRAY))
                        .append(Component.text(" / server=", NamedTextColor.GRAY))
                        .append(Component.text(Bukkit.getName() + " " + Bukkit.getVersion(),
                                NamedTextColor.YELLOW));
        ctx.getSource().getSender().sendMessage(out);
        return Command.SINGLE_SUCCESS;
    }

    private int runReload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        boolean ok = services.get(PtrConfigService.class).reload();
        Component msg =
                ok
                        ? Component.text("PtrFoundation reload OK", NamedTextColor.GREEN)
                        : Component.text(
                                "PtrFoundation reload FAILED — check logs, previous config kept",
                                NamedTextColor.RED);
        ctx.getSource().getSender().sendMessage(msg);
        return Command.SINGLE_SUCCESS;
    }

    private int runRegistryList(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String type = StringArgumentType.getString(ctx, "type");
        PtrRegistry<?> reg = registryByType(type);
        if (reg == null) {
            ctx.getSource()
                    .getSender()
                    .sendMessage(Component.text("Unknown registry: " + type, NamedTextColor.RED));
            return 0;
        }
        ctx.getSource()
                .getSender()
                .sendMessage(
                        Component.text(
                                "Registry " + reg.type() + " size=" + reg.size(),
                                NamedTextColor.GOLD));
        reg.view()
                .forEach(
                        (id, def) ->
                                ctx.getSource()
                                        .getSender()
                                        .sendMessage(
                                                Component.text("  • ", NamedTextColor.DARK_GRAY)
                                                        .append(Component.text(
                                                                id.toString(),
                                                                NamedTextColor.YELLOW))
                                                        .append(Component.text(
                                                                " — ", NamedTextColor.GRAY))
                                                        .append(def.displayName())));
        return Command.SINGLE_SUCCESS;
    }

    private int runRegistryDump(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "id");
        NamespacedKey id = NamespacedKey.fromString(raw);
        if (id == null) {
            ctx.getSource()
                    .getSender()
                    .sendMessage(Component.text("Invalid id: " + raw, NamedTextColor.RED));
            return 0;
        }
        for (Map.Entry<String, PtrRegistry<?>> e : allRegistries().entrySet()) {
            var found = e.getValue().get(id);
            if (found.isPresent()) {
                ctx.getSource()
                        .getSender()
                        .sendMessage(
                                Component.text(
                                                "Found in " + e.getKey() + ": ",
                                                NamedTextColor.GOLD)
                                        .append(found.get().displayName()
                                                .color(NamedTextColor.YELLOW)));
                return Command.SINGLE_SUCCESS;
            }
        }
        ctx.getSource()
                .getSender()
                .sendMessage(
                        Component.text("Not found in any registry: " + id, NamedTextColor.RED));
        return 0;
    }

    private int runDebugRegion(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player p)) {
            ctx.getSource()
                    .getSender()
                    .sendMessage(Component.text("Players only", NamedTextColor.RED));
            return 0;
        }
        boolean owned = RegionLocator.isOwnedByCurrentRegion(p.getLocation());
        ctx.getSource()
                .getSender()
                .sendMessage(
                        Component.text(
                                "Region owned-by-current-thread: " + owned,
                                owned ? NamedTextColor.GREEN : NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }

    private int runDebugPdc(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player p)) {
            ctx.getSource()
                    .getSender()
                    .sendMessage(Component.text("Players only", NamedTextColor.RED));
            return 0;
        }
        var item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            ctx.getSource()
                    .getSender()
                    .sendMessage(
                            Component.text("Hold an item to inspect.", NamedTextColor.YELLOW));
            return 0;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        ctx.getSource()
                .getSender()
                .sendMessage(
                        Component.text(
                                "PDC keys on held item: " + pdc.getKeys(),
                                NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private SuggestionProvider<CommandSourceStack> suggestRegistryTypes() {
        return (ctx, builder) -> {
            for (String type : allRegistries().keySet()) {
                builder.suggest(type);
            }
            return builder.buildFuture();
        };
    }

    private @org.jetbrains.annotations.Nullable PtrRegistry<?> registryByType(String type) {
        return allRegistries().get(type);
    }

    private Map<String, PtrRegistry<?>> allRegistries() {
        Map<String, PtrRegistry<?>> map = new LinkedHashMap<>();
        map.put("block", services.get(PtrBlockRegistry.class));
        map.put("item", services.get(PtrItemRegistry.class));
        map.put("mob", services.get(PtrMobRegistry.class));
        map.put("enchant", services.get(PtrEnchantRegistry.class));
        map.put("damage_type", services.get(PtrDamageTypeRegistry.class));
        map.put("skill", services.get(PtrSkillRegistry.class));
        return map;
    }
}
