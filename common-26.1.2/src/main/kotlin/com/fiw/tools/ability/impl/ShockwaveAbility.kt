package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optParticle
import com.fiw.tools.util.HolderAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

object ShockwaveAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 4.0)
        val damage = ctx.params.optF("damage", 4f)
        val knockback = ctx.params.optD("knockback", 1.2)
        val particle = ctx.params.optParticle("particle", ParticleTypes.CRIT)
        val player = ctx.player
        val world = ctx.world

        val box = AABB.ofSize(player.position(), radius * 2, 2.0, radius * 2)
        val src = world.damageSources().playerAttack(player)
        for (entity in world.getEntitiesOfClass(LivingEntity::class.java, box) { it !== player && it.isAlive }) {
            val dx = entity.x - player.x
            val dz = entity.z - player.z
            val d = Math.sqrt(dx * dx + dz * dz)
            if (d > radius) continue
            entity.hurtServer(world, src, damage)
            val nx = if (d > 0.0001) dx / d else 0.0
            val nz = if (d > 0.0001) dz / d else 0.0
            entity.push(nx * knockback, 0.4, nz * knockback)
            entity.hurtMarked = true
        }

        for (i in 0 until 64) {
            val a = i / 64.0 * Math.PI * 2
            world.sendParticles(particle, player.x + cos(a) * radius, player.y + 0.1, player.z + sin(a) * radius,
                1, 0.0, 0.0, 0.0, 0.0)
        }
        world.playSound(null, player.x, player.y, player.z,
            HolderAccess.value(SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 0.6f, 1.5f)
        return true
    }
}
