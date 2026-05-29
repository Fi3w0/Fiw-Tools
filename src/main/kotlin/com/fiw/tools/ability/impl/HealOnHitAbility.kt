package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optParticle
import net.minecraft.core.particles.ParticleTypes

object HealOnHitAbility : Ability {
    override fun execute(ctx: AbilityContext) {
        val amount = ctx.params.optF("amount", 1.5f)
        val particle = ctx.params.optParticle("particle", ParticleTypes.HEART)
        val player = ctx.player
        if (player.health < player.maxHealth) {
            player.heal(amount)
        }
        val world = ctx.world
        for (i in 0 until 6) {
            val ox = (Math.random() - 0.5) * 0.8
            val oy = Math.random() * 1.6
            val oz = (Math.random() - 0.5) * 0.8
            world.sendParticles(particle, player.x + ox, player.y + oy, player.z + oz, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}
