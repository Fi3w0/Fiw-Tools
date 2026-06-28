package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

/**
 * `while_held` reactive self-defense passives — the "annoying" ones. They act on a `cooldownTicks`
 * cadence and default to `affects: all` (anything but you), so they ward off players and mobs alike.
 * Restrict with the `affects` param.
 */

/** Periodically zaps + lightly damages anything close. */
object StaticFieldAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 3.0)
        val damage = ctx.params.optF("damage", 1f)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val world = ctx.world
        val src = world.damageSources().magic()
        var hit = false
        for (e in collectTargets(world, ctx.player.position(), radius, ctx.player, scope)) {
            e.hurtServer(world, src, damage)
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y + 1.0, e.z, 6, 0.2, 0.3, 0.2, 0.05)
            hit = true
        }
        if (hit) world.playSound(null, ctx.player.x, ctx.player.y, ctx.player.z,
            SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.4f, 1.8f)
        return hit
    }
}

/** Shoves away anything that gets close — no damage, just very annoying. */
object RepulseWardAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 3.0)
        val strength = ctx.params.optD("knockback", 0.8)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val player = ctx.player
        var pushed = false
        for (e in collectTargets(ctx.world, player.position(), radius, player, scope)) {
            val delta = Vec3(e.x - player.x, 0.0, e.z - player.z)
            if (delta.length() < 0.05) continue
            val dir = delta.normalize()
            e.push(dir.x * strength, 0.3, dir.z * strength)
            e.hurtMarked = true
            pushed = true
        }
        if (pushed) ctx.world.playSound(null, player.x, player.y, player.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 0.8f)
        return pushed
    }
}

/** Periodically applies Slowness to nearby enemies. */
object ChillAuraAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val duration = ctx.params.optI("duration", 40)
        val amplifier = ctx.params.optI("amplifier", 0)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        var hit = false
        for (e in collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)) {
            e.addEffect(MobEffectInstance(MobEffects.SLOWNESS, duration, amplifier))
            hit = true
        }
        return hit
    }
}

/** When something's close, blinds and disorients it. The most obnoxious — use a long cooldown. */
object BlindingFlashAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val duration = ctx.params.optI("duration", 40)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val targets = collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)
        if (targets.isEmpty()) return false
        for (e in targets) {
            e.addEffect(MobEffectInstance(MobEffects.BLINDNESS, duration, 0))
            e.addEffect(MobEffectInstance(MobEffects.NAUSEA, duration, 0))
        }
        ctx.world.sendParticles(ParticleTypes.SMOKE, ctx.player.x, ctx.player.y + 1.0, ctx.player.z, 20, 0.6, 0.5, 0.6, 0.02)
        return true
    }
}

/** Pulses Poison + Hunger onto adjacent enemies. */
object SporeCloudAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 3.0)
        val duration = ctx.params.optI("duration", 60)
        val amplifier = ctx.params.optI("amplifier", 0)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        var hit = false
        for (e in collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)) {
            e.addEffect(MobEffectInstance(MobEffects.POISON, duration, amplifier))
            e.addEffect(MobEffectInstance(MobEffects.HUNGER, duration, amplifier))
            ctx.world.sendParticles(ParticleTypes.SCULK_SOUL, e.x, e.y + 1.0, e.z, 4, 0.2, 0.3, 0.2, 0.01)
            hit = true
        }
        return hit
    }
}

/** Retaliation: while you're recently hit, lashes back at anything close. */
object ThornPulseAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val combatWindow = ctx.params.optI("combatWindow", 40)
        if (AbilityState.ticksSinceDamage(ctx.player.uuid, ctx.world.gameTime) > combatWindow) return false
        val radius = ctx.params.optD("radius", 3.0)
        val damage = ctx.params.optF("damage", 2f)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val world = ctx.world
        val src = world.damageSources().magic()
        var hit = false
        for (e in collectTargets(world, ctx.player.position(), radius, ctx.player, scope)) {
            e.hurtServer(world, src, damage)
            world.sendParticles(ParticleTypes.CRIT, e.x, e.y + 1.0, e.z, 6, 0.2, 0.3, 0.2, 0.1)
            hit = true
        }
        return hit
    }
}

/** Glows nearby enemies so they can't sneak up. Info only, no damage. */
object CowardMarkAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 8.0)
        val duration = ctx.params.optI("duration", 120)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        var hit = false
        for (e in collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)) {
            e.addEffect(MobEffectInstance(MobEffects.GLOWING, duration, 0))
            hit = true
        }
        return hit
    }
}

/** Auto-pesters the nearest threat with particle stings and a tiny shove. */
object HornetSwarmAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 5.0)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val target = collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)
            .minByOrNull { it.position().distanceTo(ctx.player.position()) } ?: return false
        val player = ctx.player
        val delta = Vec3(target.x - player.x, 0.0, target.z - player.z)
        if (delta.length() > 0.05) {
            val dir = delta.normalize()
            target.push(dir.x * 0.2, 0.1, dir.z * 0.2)
            target.hurtMarked = true
        }
        ctx.world.sendParticles(ParticleTypes.CRIT, target.x, target.y + 1.0, target.z, 8, 0.3, 0.4, 0.3, 0.1)
        return true
    }
}

/** Nags nearby enemies with Weakness + Mining Fatigue. */
object CursePulseAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val duration = ctx.params.optI("duration", 60)
        val amplifier = ctx.params.optI("amplifier", 0)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        var hit = false
        for (e in collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)) {
            e.addEffect(MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier))
            e.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, duration, amplifier))
            hit = true
        }
        return hit
    }
}

/** Pulses Wither to nearby enemies — magic DoT that can kill. */
object DecayAuraAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val duration = ctx.params.optI("duration", 60)
        val amplifier = ctx.params.optI("amplifier", 0)
        val scope = ctx.params.scope("affects", AffectScope.HOSTILES)
        var hit = false
        for (e in collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)) {
            e.addEffect(MobEffectInstance(MobEffects.WITHER, duration, amplifier))
            ctx.world.sendParticles(ParticleTypes.SCULK_SOUL, e.x, e.y + 1.0, e.z, 4, 0.2, 0.3, 0.2, 0.01)
            hit = true
        }
        return hit
    }
}

/** Sets nearby enemies on fire — damage scales with fire resistance and armor as normal. */
object EmberAuraAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val seconds = ctx.params.optI("seconds", 3)
        val scope = ctx.params.scope("affects", AffectScope.HOSTILES)
        var hit = false
        for (e in collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, scope)) {
            e.setSecondsOnFire(seconds)
            ctx.world.sendParticles(ParticleTypes.FLAME, e.x, e.y + 1.0, e.z, 6, 0.2, 0.4, 0.2, 0.04)
            hit = true
        }
        if (hit) ctx.world.playSound(null, ctx.player.x, ctx.player.y, ctx.player.z,
            SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.3f, 1.2f)
        return hit
    }
}
