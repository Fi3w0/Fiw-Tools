package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Sacrifice a fraction of your own HP to deal amplified damage on hit. The lower your HP the
 * less you can sacrifice — playing aggressively carries diminishing returns as you get wounded.
 * The player can never be killed by this self-damage (floor: 1 HP).
 */
object BloodPactAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val hpCostFrac = ctx.params.optF("hpCost", 0.25f).coerceIn(0f, 0.9f)
        val multiplier = ctx.params.optF("multiplier", 3f)
        val player = ctx.player
        val world = ctx.world
        val currentHp = player.health
        val sacrifice = (currentHp * hpCostFrac).coerceAtMost(currentHp - 1f)
        if (sacrifice < 0.5f) return false
        player.hurtServer(world, world.damageSources().magic(), sacrifice)
        val bonus = sacrifice * multiplier
        target.hurtServer(world, world.damageSources().playerAttack(player), bonus)
        world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.x, player.y + 1.0, player.z, 6, 0.3, 0.4, 0.3, 0.0)
        world.sendParticles(ParticleTypes.SOUL, target.x, target.y + 1.0, target.z, 12, 0.3, 0.4, 0.3, 0.02)
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5f, 0.8f)
        return true
    }
}

/**
 * Reactive passive: when the holder takes damage, the attacker is immediately infected with
 * a bleed DoT. The counter-aggression mechanic — punishes anyone who hits you.
 */
object HemorrhageAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val attacker = ctx.target ?: return false
        val dps = ctx.params.optF("dps", 1f)
        val pulses = ctx.params.optI("pulses", 4)
        val interval = ctx.params.optI("intervalTicks", 20)
        AbilityState.addBleed(attacker.uuid, dps, pulses, interval)
        ctx.world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, attacker.x, attacker.y + 1.0, attacker.z, 5, 0.2, 0.3, 0.2, 0.0)
        return true
    }
}

/**
 * Lifesteal that scales with how wounded you are — the lower your HP, the more HP is drained
 * per hit. A comeback mechanic: desperate fighters recover faster.
 */
object SanguineStrikeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        ctx.target ?: return false
        val baseHeal = ctx.params.optF("baseHeal", 0.5f)
        val bonusHeal = ctx.params.optF("bonusHeal", 3f)
        val player = ctx.player
        if (player.health >= player.maxHealth) return false
        val missingFrac = if (player.maxHealth > 0f) 1f - (player.health / player.maxHealth) else 0f
        val heal = baseHeal + bonusHeal * missingFrac
        player.heal(heal)
        ctx.world.sendParticles(ParticleTypes.HEART, player.x, player.y + 1.0, player.z, 3, 0.3, 0.4, 0.3, 0.0)
        ctx.world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, ctx.target!!.x, ctx.target!!.y + 1.0, ctx.target!!.z, 4, 0.2, 0.3, 0.2, 0.0)
        return true
    }
}
