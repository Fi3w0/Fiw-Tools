package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * `while_held` conditional survival passives — kick in only in a pinch. Gate the big ones with a long
 * `cooldownTicks` so they read as "once per emergency" rather than constant.
 */

/** When you drop below a HP fraction: Resistance + Absorption. The passive panic button. */
object LastStandAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val player = ctx.player
        if (player.maxHealth <= 0f) return false
        val threshold = ctx.params.optF("threshold", 0.3f)
        if (player.health / player.maxHealth > threshold) return false
        player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, ctx.params.optI("duration", 100), ctx.params.optI("resistAmplifier", 1)))
        player.addEffect(MobEffectInstance(MobEffects.ABSORPTION, ctx.params.optI("absorptionDuration", 200), 1))
        val world = ctx.world
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.x, player.y + 1.0, player.z, 24, 0.4, 0.6, 0.4, 0.1)
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.1f)
        return true
    }
}

/** Speed + Strength burst the moment you take a hit, decaying after. "Wake up" in fights. */
object AdrenalineAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val combatWindow = ctx.params.optI("combatWindow", 30)
        if (AbilityState.ticksSinceDamage(ctx.player.uuid, ctx.world.gameTime) > combatWindow) return false
        val duration = ctx.params.optI("buffDuration", 40)
        ctx.player.addEffect(MobEffectInstance(MobEffects.SPEED, duration, ctx.params.optI("speedAmplifier", 0)))
        ctx.player.addEffect(MobEffectInstance(MobEffects.STRENGTH, duration, ctx.params.optI("strengthAmplifier", 0)))
        return true
    }
}

/** Maintains a small Absorption shield while held, topped back up on cooldown. */
object ShieldBatteryAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val shield = ctx.params.optF("shield", 4f)
        if (ctx.player.absorptionAmount >= shield) return false
        ctx.player.absorptionAmount = shield
        ctx.world.sendParticles(ParticleTypes.WAX_ON, ctx.player.x, ctx.player.y + 1.0, ctx.player.z, 10, 0.3, 0.4, 0.3, 0.0)
        return true
    }
}
