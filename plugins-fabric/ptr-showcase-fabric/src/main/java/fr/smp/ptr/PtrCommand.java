package fr.smp.ptr;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class PtrCommand {

    public static void bootstrap() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, env) -> register(dispatcher, registryAccess));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("ptr")
                // PTR is a test realm — open to all by design.
                .then(Commands.literal("list")
                        .executes(PtrCommand::listAll))
                .then(Commands.literal("give")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(itemSuggestions())
                                .executes(c -> giveItem(c.getSource(), StringArgumentType.getString(c, "item"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> giveItem(c.getSource(),
                                                StringArgumentType.getString(c, "item"),
                                                IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("block")
                        .then(Commands.argument("block", StringArgumentType.word())
                                .suggests(blockSuggestions())
                                .executes(c -> giveBlock(c.getSource(), StringArgumentType.getString(c, "block"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> giveBlock(c.getSource(),
                                                StringArgumentType.getString(c, "block"),
                                                IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("info")
                        .executes(PtrCommand::info)));
    }

    private static int listAll(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("§6§lPTR Showcase content").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("§e" + PtrBlocks.count() + " blocks, " + PtrItems.count() + " items registered."), false);
        src.sendSuccess(() -> Component.literal("§7Use §f/ptr give <name> §7or §f/ptr block <name>"), false);
        return 1;
    }

    private static int giveItem(CommandSourceStack src, String name, int count) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Players only."));
            return 0;
        }
        Identifier id = PtrShowcase.id(name);
        Item item = BuiltInRegistries.ITEM.get(id).map(h -> h.value()).orElse(null);
        if (item == null) {
            src.sendFailure(Component.literal("Unknown PTR item: " + name));
            return 0;
        }
        ItemStack stack = new ItemStack(item, count);
        player.getInventory().add(stack);
        src.sendSuccess(() -> Component.literal("§aGave §f" + count + "× §6ptr:" + name + "§a to " + player.getName().getString()), true);
        return 1;
    }

    private static int giveBlock(CommandSourceStack src, String name, int count) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Players only."));
            return 0;
        }
        Identifier id = PtrShowcase.id(name);
        Block block = BuiltInRegistries.BLOCK.get(id).map(h -> h.value()).orElse(null);
        if (block == null) {
            src.sendFailure(Component.literal("Unknown PTR block: " + name));
            return 0;
        }
        Item asItem = block.asItem();
        if (asItem == null || asItem == net.minecraft.world.item.Items.AIR) {
            src.sendFailure(Component.literal("Block has no item form: " + name));
            return 0;
        }
        ItemStack stack = new ItemStack(asItem, count);
        player.getInventory().add(stack);
        src.sendSuccess(() -> Component.literal("§aGave §f" + count + "× §6ptr:" + name + " §7(block)§a to " + player.getName().getString()), true);
        return 1;
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("§6§l═══ PTR Showcase ═══").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("§eFabric + Polymer overhaul §7(MC 26.1.2)"), false);
        src.sendSuccess(() -> Component.literal("§7Real registry ids server-side, vanilla-disguised over the wire."), false);
        src.sendSuccess(() -> Component.literal("§e" + PtrBlocks.count() + "§7 blocks, §e" + PtrItems.count() + "§7 items"), false);
        src.sendSuccess(() -> Component.literal("§7Datapack: damage_types, enchantments, jukebox_songs, instruments, paintings, banner_patterns, trim_patterns, biomes"), false);
        return 1;
    }

    private static SuggestionProvider<CommandSourceStack> itemSuggestions() {
        return (ctx, builder) -> {
            String[] names = {
                    "foreuse", "tronconneuse", "grappin", "scanner", "voidstone",
                    "boussole_boss", "marteau_build", "totem_alarme",
                    "miner_hat", "pillager_crown", "void_circlet", "archmage_hood"
            };
            for (String n : names) {
                if (n.startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(n);
                }
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> blockSuggestions() {
        return (ctx, builder) -> {
            String[] names = {
                    "runesteel", "plasma_block", "darkstone", "glow_moss", "blue_grass",
                    "forge_runique", "station_recharge", "console_marche_noir", "pylone_telegraph", "tableau_events"
            };
            for (String n : names) {
                if (n.startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(n);
                }
            }
            return builder.buildFuture();
        };
    }

    private PtrCommand() {}
}
