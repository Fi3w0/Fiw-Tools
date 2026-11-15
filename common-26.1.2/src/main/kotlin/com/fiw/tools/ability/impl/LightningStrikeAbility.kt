package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optParticle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType

object LightningStrikeAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val damage = ctx.params.optF("damage", 3f)
        val cosmetic = ctx.params.optB("cosmetic", true)
        val particle = ctx.params.optParticle("particle", ParticleTypes.ELECTRIC_SPARK)
        val world = ctx.world

        val bolt = EntityType.LIGHTNING_BOLT.create(world, EntitySpawnReason.TRIGGERED) ?: return false
        bolt.setPos(target.x, target.y, target.z)
        if (cosmetic) bolt.setVisualOnly(true)
        world.addFreshEntity(bolt)

        val src = world.damageSources().playerAttack(ctx.player)
        target.hurtServer(world, src, damage)

        for (i in 0 until 24) {
            val ox = (Math.random() - 0.5) * 1.6
            val oy = Math.random() * 2.0
            val oz = (Math.random() - 0.5) * 1.6
            world.sendParticles(particle, target.x + ox, target.y + oy, target.z + oz, 1, 0.0, 0.0, 0.0, 0.0)
        }
        return true
    }
}
