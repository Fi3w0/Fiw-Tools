package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optParticle
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object ProjectileBurstAbility : Ability {
    private data class InFlight(
        val world: ServerLevel,
        val owner: ServerPlayer,
        var pos: Vec3,
        val dir: Vec3,
        val speed: Double,
        val range: Double,
        val damage: Float,
        val aoeRadius: Double,
        val particle: ParticleOptions,
        var travelled: Double = 0.0
    )

    private val projectiles: MutableList<InFlight> = ArrayList()

    fun init() {
        ServerTickEvents.END_SERVER_TICK.register { _ -> tickAll() }
    }

    /** Drop all in-flight projectiles (e.g. on server stop) so none tick into a freshly loaded world. */
    fun clear() {
        synchronized(projectiles) { projectiles.clear() }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val range = ctx.params.optD("range", 16.0)
        val speed = ctx.params.optD("speed", 1.4)
        val damage = ctx.params.optF("damage", 5f)
        val aoe = ctx.params.optD("aoeRadius", 1.0)
        val particle = ctx.params.optParticle("particle", ParticleTypes.FLAME)

        val origin = ctx.player.position().add(0.0, 1.4, 0.0)
        val dir = ctx.player.lookAngle.normalize()

        synchronized(projectiles) {
            projectiles.add(InFlight(ctx.world, ctx.player, origin, dir, speed, range, damage, aoe, particle))
        }
        return true
    }

    private fun tickAll() {
        synchronized(projectiles) {
            val it = projectiles.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.pos = p.pos.add(p.dir.scale(p.speed))
                p.travelled += p.speed
                p.world.sendParticles(p.particle, p.pos.x, p.pos.y, p.pos.z, 4, 0.05, 0.05, 0.05, 0.0)

                val box = AABB.ofSize(p.pos, p.aoeRadius * 2, p.aoeRadius * 2, p.aoeRadius * 2)
                val hit = p.world.getEntitiesOfClass(LivingEntity::class.java, box) { e -> e !== p.owner && e.isAlive }
                if (hit.isNotEmpty()) {
                    val src = p.world.damageSources().playerAttack(p.owner)
                    for (e in hit) e.hurtServer(p.world, src, p.damage)
                    it.remove()
                    continue
                }
                if (p.travelled >= p.range) it.remove()
            }
        }
    }
}
