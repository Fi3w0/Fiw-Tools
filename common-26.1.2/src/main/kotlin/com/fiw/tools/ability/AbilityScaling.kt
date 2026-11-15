package com.fiw.tools.ability

import com.fiw.tools.charge.ChargeHandler
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.imbue.ImbueMods
import com.fiw.tools.streak.KillstreakHandler
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Resolves an ability's [ItemDefinition.AbilityDef.scaling] into concrete params at fire time. Each
 * entry adds `perPoint * stat` (optionally clamped by `cap`) to the base numeric value already in
 * `params[param]`, so an ability's numbers grow with killstreak tier, imbue count, remaining
 * charges, or the holder's max health. Returns the ability's own params untouched when it declares
 * no scaling — the common case, allocation-free.
 */
object AbilityScaling {
    fun resolve(player: ServerPlayer, stack: ItemStack, def: ItemDefinition, ab: ItemDefinition.AbilityDef): JsonObject {
        if (ab.scaling.isEmpty()) return ab.params
        val out = ab.params.deepCopy()
        for (s in ab.scaling) {
            var bonus = s.perPoint * statValue(player, stack, def, s.stat)
            s.cap?.let { bonus = bonus.coerceAtMost(it) }
            val base = out.get(s.param)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: 0.0
            out.addProperty(s.param, base + bonus)
        }
        return out
    }

    private fun statValue(player: ServerPlayer, stack: ItemStack, def: ItemDefinition, stat: String): Double = when (stat) {
        "streak_tier" -> KillstreakHandler.tier(stack, def).toDouble()
        "imbue_count" -> ImbueMods.readCount(stack).toDouble()
        "charges" -> ChargeHandler.charges(stack).toDouble()
        "max_health" -> player.maxHealth.toDouble()
        else -> 0.0
    }
}
