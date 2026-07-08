package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optParticle
import com.fiw.tools.ability.scope
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.acos

/**
 * Homing bolt (`homing_bolt`) — a particle projectile that curves toward the nearest enemy as it
 * flies. Each tick it re-aims at the closest valid target within `trackRange`, turning at most
 * `turnRate` degrees so it arcs rather than snapping. On contact (or reaching `range`) it deals
 * `damage` in a small `aoeRadius`. Cosmetic trail only — nothing to pick up.
 */
object HomingBoltAbility : Ability {
    private class Bolt(
        val world: ServerLevel,
        val owner: ServerPlayer,
        var pos: Vec3,
        var dir: Vec3,
        val speed: Double,
        val range: Double,
        val damage: Float,
        val aoeRadius: Double,
        val trackRange: Double,
        val turnRadians: Double,
        val affects: AffectScope,
        val particle: ParticleOptions,
        var travelled: Double = 0.0
    )

    private val bolts: MutableList<Bolt> = ArrayList()

    fun tick() {
        synchronized(bolts) {
            val it = bolts.iterator()
            while (it.hasNext()) { if (!step(it.next())) it.remove() }
        }
    }

    fun clear() { synchronized(bolts) { bolts.clear() } }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        synchronized(bolts) {
            bolts.add(Bolt(
                world = ctx.world, owner = ctx.player,
                pos = ctx.player.eyePosition,
                dir = ctx.player.lookAngle.normalize(),
                speed = p.optD("speed", 0.8).coerceIn(0.1, 4.0),
                range = p.optD("range", 24.0).coerceIn(2.0, 96.0),
                damage = p.optF("damage", 6f),
                aoeRadius = p.optD("aoeRadius", 1.0).coerceIn(0.2, 8.0),
                trackRange = p.optD("trackRange", 12.0).coerceIn(1.0, 48.0),
                turnRadians = Math.toRadians(p.optD("turnRate", 12.0).coerceIn(0.0, 90.0)),
                affects = p.scope("affects", AffectScope.ALL),
                particle = p.optParticle("particle", ParticleTypes.SOUL_FIRE_FLAME)
            ))
        }
        return true
    }

    private fun step(b: Bolt): Boolean {
        if (b.owner.isRemoved || b.owner.level() !== b.world) return false

        steerTowardNearest(b)
        b.pos = b.pos.add(b.dir.scale(b.speed))
        b.travelled += b.speed
        b.world.sendParticles(b.particle, b.pos.x, b.pos.y, b.pos.z, 3, 0.03, 0.03, 0.03, 0.0)

        val box = AABB.ofSize(b.pos, b.aoeRadius * 2, b.aoeRadius * 2, b.aoeRadius * 2)
        val hit = b.world.getEntitiesOfClass(LivingEntity::class.java, box) { e ->
            e !== b.owner && e.isAlive && b.affects.matches(b.owner, e)
        }
        if (hit.isNotEmpty()) {
            val src = b.world.damageSources().indirectMagic(b.owner, b.owner)
            for (e in hit) e.hurt(src, b.damage)
            b.world.sendParticles(ParticleTypes.CRIT, b.pos.x, b.pos.y, b.pos.z, 10, 0.2, 0.2, 0.2, 0.1)
            return false
        }
        return b.travelled < b.range
    }

    /** Rotate the bolt's heading toward the closest valid target within range, capped at turnRadians. */
    private fun steerTowardNearest(b: Bolt) {
        val box = AABB.ofSize(b.pos, b.trackRange * 2, b.trackRange * 2, b.trackRange * 2)
        var best: LivingEntity? = null
        var bestDist = Double.MAX_VALUE
        for (e in b.world.getEntitiesOfClass(LivingEntity::class.java, box) { it !== b.owner && it.isAlive && b.affects.matches(b.owner, it) }) {
            val d = e.position().distanceToSqr(b.pos)
            if (d < bestDist) { bestDist = d; best = e }
        }
        val target = best ?: return
        val desired = Vec3(target.x, target.y + target.bbHeight / 2.0, target.z).subtract(b.pos)
        if (desired.length() < 1.0e-4) return
        val want = desired.normalize()
        val dot = b.dir.dot(want).coerceIn(-1.0, 1.0)
        val angle = acos(dot)
        b.dir = if (angle <= b.turnRadians || angle < 1.0e-4) want
        else {
            val t = b.turnRadians / angle
            b.dir.scale(1.0 - t).add(want.scale(t)).normalize()
        }
    }
}
