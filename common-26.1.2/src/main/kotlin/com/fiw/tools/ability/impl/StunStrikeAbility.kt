package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.phys.Vec3

/**
 * Stun-strike (`stun_strike`) — a true hard crowd-control. On hit the target is locked for
 * `duration` ticks: a per-tick server sweep zeroes its movement (it cannot walk, flee, or be
 * knocked along) while stacked Slowness / Weakness / Mining Fatigue stop it attacking or mining.
 * Best on `on_attack`; pair with a low `chance` and a real cooldown — a full lock is strong.
 *
 * On players this is a hard root and near-total disable (they can still turn the camera, a
 * server-side limit); on mobs it is a complete freeze.
 */
object StunStrikeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val p = ctx.params
        val duration = p.optI("duration", 60).coerceAtLeast(1)
        val slownessAmplifier = p.optI("slownessAmplifier", 250)

        // The stun's per-tick velocity lock would air-freeze an entity even when it shrugs off the CC
        // effects — trivializing bosses like the Ender Dragon. Skip entities immune to the effects.
        val probe = MobEffectInstance(MobEffects.SLOWNESS, duration, slownessAmplifier)
        if (target is EnderDragon || !target.canBeAffected(probe)) return false

        AbilityState.addStun(target.uuid, ctx.world.gameTime + duration, slownessAmplifier)
        // Immediate freeze so the lock is felt on the same tick as the hit.
        target.deltaMovement = Vec3.ZERO
        target.hurtMarked = true
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, duration, slownessAmplifier, false, p.optB("particles", true)))
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, duration, 250, false, false))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, duration, 250, false, false))

        val particle = p.optParticle("particle", ParticleTypes.CRIT)
        ctx.world.sendParticles(particle, target.x, target.y + target.bbHeight, target.z, 16, 0.3, 0.3, 0.3, 0.1)
        return true
    }
}
