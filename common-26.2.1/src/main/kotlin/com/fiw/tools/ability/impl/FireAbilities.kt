package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Dashes forward in the look direction and ignites every entity along the path.
 * Leaves fire particles; anything the caster passes through gets set ablaze.
 */
object FlameDashAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val maxDistance = ctx.params.optD("distance", 7.0)
        val igniteSeconds = ctx.params.optI("igniteSeconds", 4)
        val player = ctx.player
        val world = ctx.world
        val look = player.lookAngle.normalize()

        val ignited = mutableSetOf<LivingEntity>()
        var travelled = 0.5
        var safe = player.position()

        while (travelled <= maxDistance) {
            val pt = player.position().add(look.x * travelled, 0.0, look.z * travelled)
            val pos = BlockPos.containing(pt)
            val feet = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty
            val head = world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty
            if (!feet || !head) break
            if (!world.getBlockState(pos.below()).getCollisionShape(world, pos.below()).isEmpty) {
                safe = Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            }
            val box = AABB.ofSize(pt.add(0.0, 1.0, 0.0), 1.8, 2.2, 1.8)
            for (e in world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && it !== player }) {
                ignited.add(e)
            }
            world.sendParticles(ParticleTypes.FLAME, pt.x, pt.y + 0.8, pt.z, 3, 0.15, 0.2, 0.15, 0.02)
            travelled += 0.5
        }

        player.teleportTo(safe.x, safe.y, safe.z)
        for (e in ignited) {
            e.igniteForSeconds(igniteSeconds.toFloat())
            world.sendParticles(ParticleTypes.FLAME, e.x, e.y + 1.0, e.z, 10, 0.3, 0.4, 0.3, 0.05)
        }
        world.playSound(null, safe.x, safe.y, safe.z,
            SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 1.0f)
        return true
    }
}

/**
 * Casts a fireball at the targeted ground position (raycast up to `range` blocks).
 * On impact: AoE fire damage + ignite in `radius`, with a dramatic explosion-style burst.
 */
object MeteorStrikeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val range = ctx.params.optD("range", 24.0)
        val radius = ctx.params.optD("radius", 4.0)
        val damage = ctx.params.optF("damage", 8f)
        val igniteSeconds = ctx.params.optI("igniteSeconds", 5)
        val scope = ctx.params.scope("affects", AffectScope.ALL)
        val player = ctx.player
        val world = ctx.world

        val eye = player.position().add(0.0, player.eyeHeight.toDouble(), 0.0)
        val look = player.lookAngle.normalize()
        var targetPos = eye.add(look.scale(range))
        var t = 1.0
        while (t < range) {
            val pt = eye.add(look.scale(t))
            val pos = BlockPos.containing(pt)
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty) {
                targetPos = pt
                break
            }
            t += 0.5
        }

        val src = world.damageSources().magic()
        for (e in collectTargets(world, targetPos, radius, player, scope)) {
            e.hurtServer(world, src, damage)
            e.igniteForSeconds(igniteSeconds.toFloat())
        }

        for (i in 0 until 5) {
            world.sendParticles(ParticleTypes.FLAME,
                targetPos.x + (Math.random() - 0.5) * radius,
                targetPos.y + Math.random() * 2,
                targetPos.z + (Math.random() - 0.5) * radius,
                8, 0.3, 0.4, 0.3, 0.08)
        }
        world.sendParticles(ParticleTypes.EXPLOSION, targetPos.x, targetPos.y + 0.5, targetPos.z, 1, 0.0, 0.0, 0.0, 0.0)
        world.playSound(null, targetPos.x, targetPos.y, targetPos.z,
            SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.5f)
        world.playSound(null, targetPos.x, targetPos.y, targetPos.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.3f)
        return true
    }
}
