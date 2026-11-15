package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import com.fiw.tools.elemental.ElementalStatus
import com.fiw.tools.elemental.ElementalStatusTracker
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/** Single-target hit: bonus magic damage + heavy Slowness to shatter momentum. */
object IceLanceAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val damage = ctx.params.optF("damage", 4f)
        val slowDuration = ctx.params.optI("slowDuration", 80)
        val slowAmplifier = ctx.params.optI("slowAmplifier", 3)
        val world = ctx.world
        target.hurtServer(world, world.damageSources().magic(), damage)
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, slowDuration, slowAmplifier))
        target.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, slowDuration, 0))
        ElementalStatusTracker.apply(target.uuid, ElementalStatus.FROZEN, slowDuration)
        world.sendParticles(ParticleTypes.SNOWFLAKE, target.x, target.y + 1.0, target.z, 20, 0.4, 0.5, 0.4, 0.05)
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 1.4f)
        return true
    }
}

/** AoE frost storm: Slowness + Blindness + light damage around the player. */
object BlizzardAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 6.0)
        val damage = ctx.params.optF("damage", 2f)
        val slowDuration = ctx.params.optI("slowDuration", 60)
        val slowAmplifier = ctx.params.optI("slowAmplifier", 1)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val world = ctx.world
        val targets = collectTargets(world, ctx.player.position(), radius, ctx.player, scope)
        if (targets.isEmpty()) return false
        val src = world.damageSources().magic()
        val frozenDuration = ctx.params.optI("frozenDuration", 80)
        for (e in targets) {
            e.hurtServer(world, src, damage)
            e.addEffect(MobEffectInstance(MobEffects.SLOWNESS, slowDuration, slowAmplifier))
            e.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 30, 0))
            ElementalStatusTracker.apply(e.uuid, ElementalStatus.FROZEN, frozenDuration)
            world.sendParticles(ParticleTypes.SNOWFLAKE, e.x, e.y + 1.0, e.z, 8, 0.3, 0.4, 0.3, 0.05)
        }
        world.sendParticles(ParticleTypes.SNOWFLAKE, ctx.player.x, ctx.player.y + 1.0, ctx.player.z, 40, 1.5, 1.0, 1.5, 0.1)
        world.playSound(null, ctx.player.x, ctx.player.y, ctx.player.z,
            SoundEvents.POWDER_SNOW_FALL, SoundSource.PLAYERS, 1.0f, 0.6f)
        return true
    }
}

/**
 * Passive survival: while below a health threshold the player trades mobility for armor — gains
 * Resistance but also gains Slowness (can't sprint through while invulnerable).
 */
object GlacialShellAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val threshold = ctx.params.optF("threshold", 0.4f)
        val player = ctx.player
        if (player.maxHealth <= 0f || player.health / player.maxHealth > threshold) return false
        val duration = ctx.params.optI("buffDuration", 40)
        val resistAmplifier = ctx.params.optI("resistAmplifier", 0)
        player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, duration, resistAmplifier))
        player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, duration, 1))
        player.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, duration, 0))
        return true
    }
}
