package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

object FrostNovaAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val damage = ctx.params.optF("damage", 2f)
        val slowDur = ctx.params.optI("slowDuration", 40)
        val slowAmp = ctx.params.optI("slowAmplifier", 1)
        val particle = ctx.params.optParticle("particle", ParticleTypes.SNOWFLAKE)
        val player = ctx.player
        val world = ctx.world

        val box = AABB.ofSize(player.position(), radius * 2, 2.0, radius * 2)
        val src = world.damageSources().magic()
        for (entity in world.getEntitiesOfClass(LivingEntity::class.java, box) { it !== player && it.isAlive }) {
            if (entity.position().distanceTo(player.position()) > radius) continue
            entity.hurtServer(world, src, damage)
            entity.addEffect(MobEffectInstance(MobEffects.SLOWNESS, slowDur, slowAmp))
        }

        for (ring in 0..2) {
            val r = radius * (0.4 + ring * 0.3)
            for (i in 0 until 48) {
                val a = i / 48.0 * Math.PI * 2
                world.sendParticles(particle,
                    player.x + cos(a) * r, player.y + 0.2, player.z + sin(a) * r,
                    1, 0.0, 0.05, 0.0, 0.0)
            }
        }
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.6f)
        return true
    }
}
