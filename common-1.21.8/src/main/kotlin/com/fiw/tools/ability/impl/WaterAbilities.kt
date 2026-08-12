package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import com.fiw.tools.elemental.ElementalStatus
import com.fiw.tools.elemental.ElementalStatusTracker
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

/**
 * Directional water surge: knocks entities in a forward cone away from the player and slows them.
 * Unlike `whirlwind` (360°) this is a targeted blast — strong knockback in one direction.
 */
object TidalSurgeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 7.0)
        val angle = ctx.params.optD("angle", 80.0)
        val knockback = ctx.params.optD("knockback", 1.8)
        val slowDuration = ctx.params.optI("slowDuration", 60)
        val slowAmplifier = ctx.params.optI("slowAmplifier", 1)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val player = ctx.player
        val world = ctx.world

        val look = player.lookAngle.let { Vec3(it.x, 0.0, it.z).normalize() }
        val cosHalfAngle = Math.cos(Math.toRadians(angle / 2.0))

        var hit = false
        for (e in collectTargets(world, player.position(), radius, player, scope)) {
            val toEntity = Vec3(e.x - player.x, 0.0, e.z - player.z)
            if (toEntity.length() < 0.05) continue
            if (look.dot(toEntity.normalize()) < cosHalfAngle) continue
            val dir = toEntity.normalize()
            e.push(dir.x * knockback, 0.4, dir.z * knockback)
            e.hurtMarked = true
            e.addEffect(MobEffectInstance(MobEffects.SLOWNESS, slowDuration, slowAmplifier))
            ElementalStatusTracker.apply(e.uuid, ElementalStatus.SOAKED, 120)
            world.sendParticles(ParticleTypes.SPLASH, e.x, e.y + 0.5, e.z, 12, 0.3, 0.3, 0.3, 0.1)
            hit = true
        }
        if (hit) {
            world.playSound(null, player.x, player.y, player.z,
                SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0f, 0.7f)
        }
        return hit
    }
}
