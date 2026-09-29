package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optIOrNull
import com.fiw.tools.api.FiwBossesBridge
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.entity.Entity

/**
 * Boss phase (`boss_phase`) — admin tool for Fiw Bosses: moves a boss to another phase without
 * touching its HP, e.g. to ease a fight players are stuck on or push a stalled one forward.
 * Targets the hit/looked-at boss when the trigger has one, else the nearest boss in `range`.
 * Needs Fiw Bosses installed; otherwise it does nothing.
 *
 * | Param | Default | Meaning |
 * |-------|---------|---------|
 * | `action` | `next` | `next`, `prev`, or `set` (with `phase`). |
 * | `phase` | — | Target phase for `set`, 1-based. |
 * | `range` | 32 | Search radius when the trigger has no target. |
 * | `requireOp` | true | Only operators (permission level 2+) can use it. Keep this on. |
 */
object BossPhaseAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        if (!FiwBossesBridge.isPresent) return tell(player, "§cFiw Bosses is not installed.")
        if (p.optB("requireOp", true) && !isOperator(player)) return tell(player, "§cOnly operators can shift boss phases.")

        val boss = ctx.target?.takeIf { FiwBossesBridge.isBoss(it) } ?: nearestBoss(ctx, p.optD("range", 32.0))
            ?: return tell(player, "§7No boss in range.")

        val current = FiwBossesBridge.getPhase(boss)
        val wanted = when (p.optS("action", "next").lowercase()) {
            "next" -> current + 1
            "prev", "previous" -> current - 1
            "set" -> p.optIOrNull("phase") ?: return tell(player, "§c'set' needs a 'phase' param.")
            else -> return tell(player, "§cUnknown action; use next, prev or set.")
        }
        val count = FiwBossesBridge.getPhaseCount(boss)
        when {
            count <= 1 -> return tell(player, "§7This boss has only one phase.")
            wanted < 1 -> return tell(player, "§7Already at the first phase.")
            wanted > count -> return tell(player, "§7Already at the last phase ($current/$count).")
            wanted == current -> return tell(player, "§7Boss is already in phase $wanted.")
        }
        if (!FiwBossesBridge.setPhase(boss, wanted)) {
            return tell(player, "§7The fight hasn't started yet (or the boss is dying).")
        }
        player.sendSystemMessage(Component.literal(
            "§6Boss phase §f$current §6→ §f$wanted§7/$count"))
        return true
    }

    private fun nearestBoss(ctx: AbilityContext, range: Double): Entity? =
        ctx.world.getEntities(ctx.player, ctx.player.boundingBox.inflate(range)) { FiwBossesBridge.isBoss(it) }
            .minByOrNull { it.distanceToSqr(ctx.player) }

    private fun isOperator(player: ServerPlayer): Boolean =
        player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)

    // Last refusal-message tick per player. A failed use arms no cooldown and one right-click
    // can fire several times, so refusals are throttled to one per second.
    private val lastRefusal = HashMap<java.util.UUID, Long>()

    /** Feedback that the ability did nothing; returns false so no cooldown is armed. */
    private fun tell(player: ServerPlayer, msg: String): Boolean {
        val now = player.level().gameTime
        val last = lastRefusal[player.uuid]
        if (last == null || now - last >= 20 || now < last) {
            lastRefusal[player.uuid] = now
            player.sendSystemMessage(Component.literal(msg))
        }
        return false
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
