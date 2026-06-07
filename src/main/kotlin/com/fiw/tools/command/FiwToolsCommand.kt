package com.fiw.tools.command

import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.curse.CurseJsonWriter
import com.fiw.tools.imbue.ImbueMods
import com.fiw.tools.sync.ItemSyncHandler
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.item.component.CustomData

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
                .then(Commands.literal("curse")
                    .then(Commands.literal("add")
                        .then(Commands.argument("itemId", StringArgumentType.word())
                            .suggests(ITEM_ID_SUGGESTIONS)
                            .executes { ctx -> curseSet(ctx.source, StringArgumentType.getString(ctx, "itemId"), true) }))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("itemId", StringArgumentType.word())
                            .suggests(ITEM_ID_SUGGESTIONS)
                            .executes { ctx -> curseSet(ctx.source, StringArgumentType.getString(ctx, "itemId"), false) }))
                    .then(Commands.literal("list")
                        .then(Commands.argument("itemId", StringArgumentType.word())
                            .suggests(ITEM_ID_SUGGESTIONS)
                            .executes { ctx -> curseList(ctx.source, StringArgumentType.getString(ctx, "itemId")) }))
                    .then(Commands.literal("whitelist")
                        .then(Commands.literal("add")
                            .then(Commands.argument("itemId", StringArgumentType.word())
                                .suggests(ITEM_ID_SUGGESTIONS)
                                .then(Commands.argument("player", EntityArgument.player())
                                    .executes { ctx -> curseWhitelistAdd(ctx.source,
                                        StringArgumentType.getString(ctx, "itemId"),
                                        EntityArgument.getPlayer(ctx, "player")) })))
                        .then(Commands.literal("remove")
                            .then(Commands.argument("itemId", StringArgumentType.word())
                                .suggests(ITEM_ID_SUGGESTIONS)
                                .then(Commands.argument("query", StringArgumentType.word())
                                    .executes { ctx -> curseWhitelistRemove(ctx.source,
                                        StringArgumentType.getString(ctx, "itemId"),
                                        StringArgumentType.getString(ctx, "query")) })))))
                .then(Commands.literal("uncurse_held").executes { uncurseHeld(it.source) })
                .then(Commands.literal("imbue")
                    .then(Commands.literal("best")
                        .then(Commands.argument("catalystId", StringArgumentType.word())
                            .suggests(ITEM_ID_SUGGESTIONS)
                            .executes { ctx -> imbueRun(ctx.source, StringArgumentType.getString(ctx, "catalystId"), bestOnly = true) }))
                    .then(Commands.literal("roll")
                        .then(Commands.argument("catalystId", StringArgumentType.word())
                            .suggests(ITEM_ID_SUGGESTIONS)
                            .executes { ctx -> imbueRun(ctx.source, StringArgumentType.getString(ctx, "catalystId"), bestOnly = false) }))
                    .then(Commands.literal("reset").executes { imbueReset(it.source) })
                    .then(Commands.literal("clear").executes { imbueClear(it.source) })
                    .then(Commands.literal("log").executes { imbueLog(it.source) }))
        )
    }

    /**
     * Apply a catalyst's imbuement to the held off-hand stack. `bestOnly` runs the highest-weight
     * outcome (admin tooling) rather than rolling. Bypasses the catalyst's charge tracking entirely
     * since the catalyst stack itself isn't being used.
     */
    private fun imbueRun(source: CommandSourceStack, catalystId: String, bestOnly: Boolean): Int {
        val player = source.player ?: run {
            source.sendFailure(Component.literal("Run this as a player.")); return 0
        }
        val def = ItemRegistry.byId(catalystId) ?: run {
            source.sendFailure(Component.literal("Unknown catalyst '$catalystId'")); return 0
        }
        val imbueAbility = def.abilities.firstOrNull { it.type.equals("imbue", ignoreCase = true) } ?: run {
            source.sendFailure(Component.literal("Item '$catalystId' has no 'imbue' ability.")); return 0
        }
        val target = player.offhandItem
        if (target.isEmpty) { source.sendFailure(Component.literal("Hold the target in your off hand.")); return 0 }

        val outcomes = imbueAbility.params.getAsJsonArray("outcomes")
        if (outcomes == null || outcomes.isEmpty()) {
            source.sendFailure(Component.literal("Catalyst has no outcomes.")); return 0
        }
        val outcome = if (bestOnly) {
            outcomes.maxByOrNull {
                (it.asJsonObject.get("weight")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 1)
            }?.asJsonObject
        } else {
            // Reuse the same weighted roll as the live ability via a single right-click would.
            val rngOn = imbueAbility.params.get("rng")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            pickOutcomeAdmin(outcomes, rngOn)
        } ?: run { source.sendFailure(Component.literal("No outcome picked.")); return 0 }

        val mods = outcome.getAsJsonObject("mods") ?: com.google.gson.JsonObject()
        val attrPrefix = "stack/${ImbueMods.readCount(target)}"
        ImbueMods.applyMods(target, mods, player.level().registryAccess(), attrPrefix)
        ImbueMods.appendLog(target, mods)
        val name = outcome.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "(unnamed)"
        source.sendSuccess({ Component.literal("Imbued held off-hand with '$name'.") }, true)
        return 1
    }

    private fun pickOutcomeAdmin(outcomes: com.google.gson.JsonArray, rng: Boolean): com.google.gson.JsonObject? {
        if (!rng) return outcomes.firstOrNull()?.asJsonObject
        val weights = outcomes.map { (it.asJsonObject.get("weight")?.takeIf { e -> !e.isJsonNull }?.asInt ?: 1) }
        val total = weights.sum()
        if (total <= 0) return outcomes.firstOrNull()?.asJsonObject
        var roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(total)
        for ((i, w) in weights.withIndex()) {
            if (roll < w) return outcomes[i].asJsonObject
            roll -= w
        }
        return outcomes.last().asJsonObject
    }

    private fun imbueReset(source: CommandSourceStack): Int {
        val player = source.player ?: run { source.sendFailure(Component.literal("Run this as a player.")); return 0 }
        val stack = player.mainHandItem
        if (stack.isEmpty) { source.sendFailure(Component.literal("Hold a target item in main hand.")); return 0 }
        ImbueMods.resetCount(stack)
        source.sendSuccess({ Component.literal("Imbue count reset on held item.") }, true)
        return 1
    }

    private fun imbueClear(source: CommandSourceStack): Int {
        val player = source.player ?: run { source.sendFailure(Component.literal("Run this as a player.")); return 0 }
        val stack = player.mainHandItem
        if (stack.isEmpty) { source.sendFailure(Component.literal("Hold a target item in main hand.")); return 0 }
        val id = FiwItems.fiwId(stack) ?: run {
            source.sendFailure(Component.literal("Not a Fiw Tools item; nothing to rebuild against."))
            return 0
        }
        val def = ItemRegistry.byId(id) ?: run {
            source.sendFailure(Component.literal("Definition for '$id' is no longer loaded.")); return 0
        }
        val rebuilt = ItemBuilder.build(def, stack.count, player.level().registryAccess()) ?: run {
            source.sendFailure(Component.literal("Failed to rebuild '$id'.")); return 0
        }
        player.inventory.setItem(player.inventory.selectedSlot, rebuilt)
        source.sendSuccess({ Component.literal("Cleared all imbuements and rebuilt held item from config.") }, true)
        return 1
    }

    private fun imbueLog(source: CommandSourceStack): Int {
        val player = source.player ?: run { source.sendFailure(Component.literal("Run this as a player.")); return 0 }
        val stack = player.mainHandItem
        val log = ImbueMods.readLog(stack)
        val count = ImbueMods.readCount(stack)
        if (log.isEmpty) {
            source.sendSuccess({ Component.literal("No imbuements on held item.") }, false)
        } else {
            source.sendSuccess({ Component.literal("Imbue count: $count, entries: ${log.size()}") }, false)
            log.forEachIndexed { i, entry ->
                source.sendSuccess({ Component.literal("  [$i] $entry") }, false)
            }
        }
        return 1
    }

    private fun curseSet(source: CommandSourceStack, id: String, cursed: Boolean): Int {
        val path = CurseJsonWriter.findFile(id) ?: run {
            source.sendFailure(Component.literal("No JSON file found for item '$id'"))
            return 0
        }
        runCatching { CurseJsonWriter.setCursed(path, cursed) }.onFailure {
            source.sendFailure(Component.literal("Failed to edit $id: ${it.message}")); return 0
        }
        ItemRegistry.loadAll()
        ItemSyncHandler.syncAll(source.server)
        source.sendSuccess({ Component.literal(if (cursed) "Cursed '$id'." else "Lifted curse on '$id'.") }, true)
        return 1
    }

    private fun curseList(source: CommandSourceStack, id: String): Int {
        val def = ItemRegistry.byId(id) ?: run {
            source.sendFailure(Component.literal("Unknown Fiw Tools item id '$id'")); return 0
        }
        val state = if (def.cursed) "CURSED" else "not cursed"
        val wl = if (def.curseWhitelist.isEmpty()) "(empty)" else def.curseWhitelist.joinToString(", ")
        source.sendSuccess({ Component.literal("$id → $state, whitelist: $wl") }, false)
        return 1
    }

    private fun curseWhitelistAdd(source: CommandSourceStack, id: String, player: ServerPlayer): Int {
        val path = CurseJsonWriter.findFile(id) ?: run {
            source.sendFailure(Component.literal("No JSON file found for item '$id'")); return 0
        }
        val entry = "${player.gameProfile.name}|${player.uuid}"
        runCatching { CurseJsonWriter.addToWhitelist(path, entry) }.onFailure {
            source.sendFailure(Component.literal("Failed to edit $id: ${it.message}")); return 0
        }
        ItemRegistry.loadAll()
        source.sendSuccess({ Component.literal("Added ${player.gameProfile.name} to '$id' whitelist.") }, true)
        return 1
    }

    private fun curseWhitelistRemove(source: CommandSourceStack, id: String, query: String): Int {
        val path = CurseJsonWriter.findFile(id) ?: run {
            source.sendFailure(Component.literal("No JSON file found for item '$id'")); return 0
        }
        val removed = runCatching { CurseJsonWriter.removeFromWhitelist(path, query) }.getOrElse {
            source.sendFailure(Component.literal("Failed to edit $id: ${it.message}")); return 0
        }
        ItemRegistry.loadAll()
        if (removed) source.sendSuccess({ Component.literal("Removed '$query' from '$id' whitelist.") }, true)
        else source.sendFailure(Component.literal("'$query' was not in '$id' whitelist."))
        return if (removed) 1 else 0
    }

    private fun uncurseHeld(source: CommandSourceStack): Int {
        val player = source.player ?: run {
            source.sendFailure(Component.literal("Run this as a player.")); return 0
        }
        val stack = player.mainHandItem
        if (!FiwItems.isFiwItem(stack)) {
            source.sendFailure(Component.literal("Hold a Fiw Tools item in your main hand.")); return 0
        }
        val data = stack.get(DataComponents.CUSTOM_DATA)
        if (data == null) { source.sendFailure(Component.literal("Item has no custom data.")); return 0 }
        val tag = data.copyTag()
        if (!tag.getBoolean(ItemBuilder.CURSED_KEY).orElse(false)) {
            source.sendFailure(Component.literal("That item isn't cursed.")); return 0
        }
        tag.putBoolean(ItemBuilder.UNCURSED_KEY, true)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        source.sendSuccess({ Component.literal("Stack uncursed.") }, true)
        return 1
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
        ItemSyncHandler.syncAll(source.server)
        source.sendSuccess({ Component.literal("Reloaded: ${report.loaded} loaded, ${report.failed.size} failed") }, true)
        for ((file, err) in report.failed) {
            source.sendFailure(Component.literal("  $file: $err"))
        }
        return report.loaded
    }
}
