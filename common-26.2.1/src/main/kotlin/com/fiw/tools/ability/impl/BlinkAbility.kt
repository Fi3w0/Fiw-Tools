package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optParticle
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

object BlinkAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val maxDistance = ctx.params.optD("distance", 5.0)
        val particle = ctx.params.optParticle("particle", ParticleTypes.PORTAL)
        val player = ctx.player
        val world = ctx.world

        val look = player.lookAngle.normalize()
        var travelled = 0.0
        var safe: Vec3 = player.position()
        while (travelled < maxDistance) {
            travelled += 0.5
            val candidate = player.position().add(look.x * travelled, 0.0, look.z * travelled)
            val pos = BlockPos.containing(candidate)
            val below = pos.below()
            val feet = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty
            val head = world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty
            if (!feet || !head) break
            if (!world.getBlockState(below).getCollisionShape(world, below).isEmpty) {
                safe = Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            }
        }

        emitBurst(world, player.position().add(0.0, 1.0, 0.0), particle)
        player.teleportTo(safe.x, safe.y, safe.z)
        emitBurst(world, safe.add(0.0, 1.0, 0.0), particle)
        world.playSound(null, safe.x, safe.y, safe.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.0f)
        return true
    }

    private fun emitBurst(world: ServerLevel, at: Vec3, particle: ParticleOptions) {
        for (i in 0 until 24) {
            val ox = (Math.random() - 0.5) * 1.0
            val oy = Math.random() * 1.4
            val oz = (Math.random() - 0.5) * 1.0
            world.sendParticles(particle, at.x + ox, at.y + oy, at.z + oz, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}
