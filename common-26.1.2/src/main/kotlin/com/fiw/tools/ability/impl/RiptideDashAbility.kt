package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optParticle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

object RiptideDashAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val distance = ctx.params.optD("distance", 5.0)
        val vertical = ctx.params.optD("vertical", 0.4)
        val particle = ctx.params.optParticle("particle", ParticleTypes.CLOUD)
        val player = ctx.player

        val look: Vec3 = player.lookAngle
        val push = Vec3(look.x * distance * 0.2, vertical, look.z * distance * 0.2)
        player.push(push.x, push.y, push.z)
        player.hurtMarked = true
        player.fallDistance = 0.0

        val world = ctx.world
        val origin = player.position().add(0.0, 1.0, 0.0)
        for (i in 0 until 24) {
            val t = i / 24.0
            val px = origin.x - look.x * t * 1.2
            val py = origin.y - look.y * t * 1.2
            val pz = origin.z - look.z * t * 1.2
            world.sendParticles(particle, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0)
        }
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.6f, 1.4f)
        return true
    }
}
