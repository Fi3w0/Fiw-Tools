package com.fiw.tools.ability

import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

/**
 * Boss-slayer bonus: `bonusVsBosses` / `bonusVsMinions` multiply damage a player deals to bosses
 * and their minions. An ability's own params value wins while that ability runs; otherwise the
 * item that dealt the hit (main hand, or the bow that fired the arrow) decides. Applied from a
 * LivingEntity damage mixin, so it covers melee, projectiles and ability damage on every loader.
 */
object BossSlayer {
    /** Set while an ability executes, so its own bonus applies to the damage it deals. */
    @JvmStatic var currentAbility: ItemDefinition.AbilityDef? = null

    @JvmStatic
    fun scale(victim: LivingEntity, source: DamageSource, amount: Float): Float {
        val attacker = source.entity as? ServerPlayer ?: return amount
        if (attacker === victim || amount <= 0f) return amount
        val key = when {
            BossTargets.isBoss(victim) -> "bonusVsBosses"
            BossTargets.isMinion(victim) -> "bonusVsMinions"
            else -> return amount
        }
        currentAbility?.params?.get(key)?.takeIf { it.isJsonPrimitive }?.let { return amount * it.asFloat.coerceAtLeast(0f) }
        val weapon = AbilityDispatcher.killingWeapon(attacker, source)
        val def = FiwItems.fiwId(weapon)?.let { ItemRegistry.byId(it) } ?: return amount
        val mult = if (key == "bonusVsBosses") def.bonusVsBosses else def.bonusVsMinions
        return amount * mult
    }

    /** Runs [block] with [ab] as the ability whose bonus applies to any damage dealt meanwhile. */
    inline fun <T> during(ab: ItemDefinition.AbilityDef, block: () -> T): T {
        val previous = currentAbility
        currentAbility = ab
        try {
            return block()
        } finally {
            currentAbility = previous
        }
    }
}
