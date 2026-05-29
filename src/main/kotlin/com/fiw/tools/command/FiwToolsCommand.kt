package com.fiw.tools.command

import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions

object FiwToolsCommand {
    private val ITEM_ID_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        ItemRegistry.ids().forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("fiwtools").requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                .then(Commands.literal("give")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("itemId", StringArgumentType.word())
                            .suggests(ITEM_ID_SUGGESTIONS)
                            .executes { ctx -> give(ctx.source, EntityArgument.getPlayers(ctx, "targets"),
                                StringArgumentType.getString(ctx, "itemId"), 1) }
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                .executes { ctx -> give(ctx.source, EntityArgument.getPlayers(ctx, "targets"),
                                    StringArgumentType.getString(ctx, "itemId"),
                                    IntegerArgumentType.getInteger(ctx, "count")) }))))
                .then(Commands.literal("list").executes { list(it.source) })
                .then(Commands.literal("info")
                    .then(Commands.argument("itemId", StringArgumentType.word())
                        .suggests(ITEM_ID_SUGGESTIONS)
                        .executes { ctx -> info(ctx.source, StringArgumentType.getString(ctx, "itemId")) }))
                .then(Commands.literal("reload").executes { reload(it.source) })
        )
    }

    private fun give(source: CommandSourceStack, targets: Collection<ServerPlayer>, id: String, count: Int): Int {
        val def = ItemRegistry.byId(id) ?: run {
            source.sendFailure(Component.literal("Unknown Fiw Tools item id '$id'"))
            return 0
        }
        var given = 0
        for (player in targets) {
            val stack = ItemBuilder.build(def, count, player.level().registryAccess()) ?: continue
            if (player.inventory.add(stack)) given++
            else player.drop(stack, false, false)
        }
        source.sendSuccess({ Component.literal("Gave ${count}x $id to $given player(s)") }, true)
        return given
    }

    private fun list(source: CommandSourceStack): Int {
        val ids = ItemRegistry.ids().sorted()
        if (ids.isEmpty()) {
            source.sendSuccess({ Component.literal("No items loaded. Drop JSON files into config/fiw_tools/items/") }, false)
        } else {
            source.sendSuccess({ Component.literal("Loaded ${ids.size} item(s): ${ids.joinToString(", ")}") }, false)
        }
        return ids.size
    }

    private fun info(source: CommandSourceStack, id: String): Int {
        val def = ItemRegistry.byId(id) ?: run {
            source.sendFailure(Component.literal("Unknown Fiw Tools item id '$id'"))
            return 0
        }
        source.sendSuccess({ Component.literal("$id → ${def.describeShort()}") }, false)
        return 1
    }

    private fun reload(source: CommandSourceStack): Int {
        val report = ItemRegistry.loadAll()
        source.sendSuccess({ Component.literal("Reloaded: ${report.loaded} loaded, ${report.failed.size} failed") }, true)
        for ((file, err) in report.failed) {
            source.sendFailure(Component.literal("  $file: $err"))
        }
        return report.loaded
    }
}
