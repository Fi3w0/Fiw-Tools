package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import net.minecraft.server.permissions.PermissionSet
import org.slf4j.LoggerFactory

/**
 * `run_command` — executes one or more server commands when triggered, opening the door to any
 * ability the command system can express. Runs at full permission with all output suppressed:
 * nothing in chat, nothing in the console, no admin broadcast.
 *
 * Params:
 * - `command`: a single command string, or `commands`: an array of them (leading `/` optional)
 * - placeholders: `{player}` name, `{uuid}`, `{x}` `{y}` `{z}` block position, `{target}` the
 *   targeted entity's name (empty when there is none)
 *
 * Pair with `on_right_click` / `on_shift_right_click` for two different command sets on one item.
 */
object RunCommandAbility : Ability {
    private val logger = LoggerFactory.getLogger("fiw-tools/run_command")

    override fun execute(ctx: AbilityContext): Boolean {
        val commands = buildList {
            ctx.params.get("command")?.takeIf { it.isJsonPrimitive }?.asString?.let { add(it) }
            ctx.params.getAsJsonArray("commands")?.forEach { el ->
                if (el.isJsonPrimitive) add(el.asString)
            }
        }
        if (commands.isEmpty()) return false

        val player = ctx.player
        val pos = player.blockPosition()
        val source = player.createCommandSourceStack()
            .withPermission(PermissionSet.ALL_PERMISSIONS)
            .withSuppressedOutput()
        val dispatcher = ctx.world.server.commands

        for (raw in commands) {
            val cmd = raw
                .removePrefix("/")
                .replace("{player}", player.gameProfile.name)
                .replace("{uuid}", player.uuid.toString())
                .replace("{x}", pos.x.toString())
                .replace("{y}", pos.y.toString())
                .replace("{z}", pos.z.toString())
                .replace("{target}", ctx.target?.name?.string ?: "")
            try {
                dispatcher.performPrefixedCommand(source, cmd)
            } catch (e: Exception) {
                logger.warn("run_command failed for '${player.gameProfile.name}': '$cmd' — ${e.message}")
            }
        }
        return true
    }
}
