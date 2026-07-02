package com.fiw.tools.ability

import com.google.gson.JsonObject
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.AABB

/**
 * Optional gates any `while_held` passive can declare in its params, ANDed together. A missing key
 * never gates. Lets the same passive be "only when low", "only in combat", "only underwater", etc.
 */
object PassiveConditions {
    fun met(player: ServerPlayer, world: ServerLevel, params: JsonObject): Boolean {
        val maxHp = player.maxHealth
        val frac = if (maxHp > 0f) player.health / maxHp else 0f

        params.optFOrNull("whenBelowHealth")?.let { if (frac > it) return false }
        params.optFOrNull("whenAboveHealth")?.let { if (frac < it) return false }
        params.optBOrNull("whenUnderwater")?.let { if (it && !player.isUnderWater) return false }
        params.optDOrNull("whenEnemyWithin")?.let { r -> if (!enemyWithin(player, world, r)) return false }
        params.optIOrNull("whenInCombat")?.let { t ->
            if (AbilityState.ticksSinceDamage(player.uuid, world.gameTime) > t) return false
        }
        params.optIOrNull("whenOutOfCombat")?.let { t ->
            if (AbilityState.ticksSinceDamage(player.uuid, world.gameTime) <= t) return false
        }
        return true
    }

    private fun enemyWithin(player: ServerPlayer, world: ServerLevel, radius: Double): Boolean {
        val box = AABB.ofSize(player.position(), radius * 2, radius * 2, radius * 2)
        return world.getEntitiesOfClass(LivingEntity::class.java, box) {
            it.isAlive && it is Enemy && it.position().distanceTo(player.position()) <= radius
        }.isNotEmpty()
    }
}
