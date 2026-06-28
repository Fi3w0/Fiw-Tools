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
 * Active abilities that apply debuffs or damage-over-time to targets.
 */

/** Sets the target on fire for a configurable number of seconds. */
object IgniteAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val seconds = ctx.params.optI("seconds", 5)
        target.setSecondsOnFire(seconds)
        val world = ctx.world
        world.sendParticles(ParticleTypes.FLAME, target.x, target.y + 1.0, target.z, 16, 0.3, 0.5, 0.3, 0.05)
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.8f, 1.0f)
        return true
    }
}

/** Applies Wither to the target — a magic DoT that can kill, bypassing armor. */
object WitherTouchAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val duration = ctx.params.optI("duration", 60)
        val amplifier = ctx.params.optI("amplifier", 0)
        target.addEffect(MobEffectInstance(MobEffects.WITHER, duration, amplifier))
        val world = ctx.world
        world.sendParticles(ParticleTypes.SCULK_SOUL, target.x, target.y + 1.2, target.z, 10, 0.3, 0.4, 0.3, 0.02)
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, 1.6f)
        return true
    }
}

/**
 * Schedules a ticking magic DoT on the target. Unlike Poison, this can kill and bypasses armor.
 * Pulses applied every [intervalTicks] ticks for [pulses] total hits.
 */
object BleedAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val dps = ctx.params.optF("dps", 1.0f)
        val pulses = ctx.params.optI("pulses", 5)
        val interval = ctx.params.optI("intervalTicks", 20)
        AbilityState.addBleed(target.uuid, dps, pulses, interval)
        val world = ctx.world
        world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.x, target.y + 1.0, target.z, 6, 0.2, 0.3, 0.2, 0.0)
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 0.7f)
        return true
    }
}
