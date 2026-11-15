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

/**
 * Abilities that interact with the elemental status system (FROZEN, SOAKED, SHOCKED).
 *
 * Setup abilities apply a status:
 *   freeze → FROZEN, soak → SOAKED, shock → SHOCKED + instant damage
 *
 * Interaction abilities consume a status for a payoff:
 *   thaw_burst → consumes FROZEN, AoE fire damage burst
 *   storm_chain → consumes SOAKED, chains lightning to nearby targets
 */

/** Apply FROZEN to the attack target. Chains with thaw_burst for a setup+payoff combo. */
object FreezeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val duration = ctx.params.optI("duration", 120)
        ElementalStatusTracker.apply(target.uuid, ElementalStatus.FROZEN, duration)
        ctx.world.sendParticles(ParticleTypes.SNOWFLAKE, target.x, target.y + 1.0, target.z, 15, 0.4, 0.5, 0.4, 0.05)
        ctx.world.playSound(null, target.x, target.y, target.z,
            SoundEvents.POWDER_SNOW_FALL, SoundSource.PLAYERS, 0.8f, 0.8f)
        return true
    }
}

/** Drench the target in water (SOAKED). Amplified by storm_chain; naturally dampens fire damage interaction. */
object SoakAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val duration = ctx.params.optI("duration", 100)
        ElementalStatusTracker.apply(target.uuid, ElementalStatus.SOAKED, duration)
        // Extinguish any fire — water puts it out
        target.clearFire()
        ctx.world.sendParticles(ParticleTypes.SPLASH, target.x, target.y + 1.0, target.z, 20, 0.4, 0.4, 0.4, 0.1)
        ctx.world.playSound(null, target.x, target.y, target.z,
            SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 1.1f)
        return true
    }
}

/** Electrify the target (SHOCKED): periodic magic damage + spark particles until duration expires. */
object ShockAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val duration = ctx.params.optI("duration", 100)
        val damage = ctx.params.optF("damage", 2f)
        ElementalStatusTracker.apply(target.uuid, ElementalStatus.SHOCKED, duration)
        // Instant hit on application
        target.hurtServer(ctx.world, ctx.world.damageSources().magic(), damage)
        ctx.world.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 1.0, target.z, 16, 0.3, 0.5, 0.3, 0.08)
        ctx.world.playSound(null, target.x, target.y, target.z,
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4f, 1.8f)
        return true
    }
}

/**
 * Consumes FROZEN on the target for an AoE fire damage burst around it.
 * Ice + Fire interaction: the freeze shatters into a scalding explosion.
 * Returns false (no cooldown) if the target isn't frozen.
 */
object ThawBurstAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        if (!ElementalStatusTracker.has(target.uuid, ElementalStatus.FROZEN)) return false
        ElementalStatusTracker.clear(target.uuid, ElementalStatus.FROZEN)

        val radius = ctx.params.optD("radius", 4.0)
        val damage = ctx.params.optF("damage", 6f)
        val igniteSeconds = ctx.params.optI("igniteSeconds", 3)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val world = ctx.world

        val src = world.damageSources().magic()
        for (e in collectTargets(world, target.position(), radius, ctx.player, scope)) {
            e.hurtServer(world, src, damage)
            e.igniteForSeconds(igniteSeconds.toFloat())
        }
        world.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.5, target.z, 30, 1.0, 0.8, 1.0, 0.1)
        world.sendParticles(ParticleTypes.SNOWFLAKE, target.x, target.y + 1.0, target.z, 20, 1.0, 0.8, 1.0, 0.05)
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f)
        return true
    }
}

/**
 * Consumes SOAKED on the target and chains lightning damage to nearby entities within `chainRadius`.
 * Water + Lightning: the soaked target acts as a conductor.
 * Returns false (no cooldown) if the target isn't soaked.
 */
object StormChainAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        if (!ElementalStatusTracker.has(target.uuid, ElementalStatus.SOAKED)) return false
        ElementalStatusTracker.clear(target.uuid, ElementalStatus.SOAKED)

        val chainRadius = ctx.params.optD("chainRadius", 5.0)
        val damage = ctx.params.optF("damage", 5f)
        val chainDamage = ctx.params.optF("chainDamage", 3f)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val world = ctx.world

        // Hit the primary target
        target.hurtServer(world, world.damageSources().magic(), damage)
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 1.0, target.z, 20, 0.3, 0.5, 0.3, 0.1)

        // Chain to nearby targets
        for (e in collectTargets(world, target.position(), chainRadius, ctx.player, scope)) {
            if (e === target) continue
            e.hurtServer(world, world.damageSources().magic(), chainDamage)
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y + 1.0, e.z, 10, 0.2, 0.4, 0.2, 0.06)
        }
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.5f)
        return true
    }
}
