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
 *   targeted entity's name, `{target_uuid}` its UUID — usable as a command selector so a command can
 *   affect the entity you hit (both empty when there is no target)
 *
 * Pair with `on_right_click` / `on_shift_right_click` for two different command sets on one item.
 */
object RunCommandAbility : Ability {
    private val logger = LoggerFactory.getLogger("fiw-tools/run_command")

    /**
     * Placeholder values are substituted into a command run at [PermissionSet.ALL_PERMISSIONS], so a
     * value that reaches a selector position must never carry selector or command-break syntax.
     * `{target}` in particular comes from an entity's display name — trivially attacker-controlled via
     * a name tag (`@a`, `@e[...]`, quotes, spaces). We strip every placeholder down to the characters
     * legal in a real player/entity name plus the `-` used by UUIDs and negative coordinates; anything
     * that could break out of a token (`@`, whitespace, quotes, brackets, ...) is removed.
     */
    private val UNSAFE = Regex("[^A-Za-z0-9_-]")
    private fun sanitize(raw: String): String = UNSAFE.replace(raw, "")

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
                .replace("{player}", sanitize(player.gameProfile.name))
                .replace("{uuid}", sanitize(player.uuid.toString()))
                .replace("{x}", sanitize(pos.x.toString()))
                .replace("{y}", sanitize(pos.y.toString()))
                .replace("{z}", sanitize(pos.z.toString()))
                // {target_uuid} addresses the actual targeted entity (a UUID is a valid entity
                // selector), so commands can affect a mob you hit — {target} is only its display name.
                .replace("{target_uuid}", sanitize(ctx.target?.uuid?.toString() ?: ""))
                .replace("{target}", sanitize(ctx.target?.name?.string ?: ""))
            try {
                dispatcher.performPrefixedCommand(source, cmd)
            } catch (e: Exception) {
                logger.warn("run_command failed for '${player.gameProfile.name}': '$cmd' — ${e.message}")
            }
        }
        return true
    }
}
