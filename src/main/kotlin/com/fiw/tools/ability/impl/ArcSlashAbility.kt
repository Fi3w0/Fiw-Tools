package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated arc slash — 5-layer particle sweep ported from Fiw Bosses' ArcSlashGoal,
 * scaled down for player use. Locks origin/forward at fire time so a player turning
 * mid-swing doesn't curve the arc. Each arc point is a one-shot hit (already-hit set
 * prevents double damage).
 *
 * Params (player defaults):
 *   range/radius (3.5)   reach in blocks
 *   arc          (140)   total sweep angle in degrees
 *   damage       (5.0)   per-victim damage
 *   knockback    (0.6)
 *   duration     (5)     ticks for the full sweep
 *   points       (24)    arc resolution
 *   yOffset      (1.1)
 *   height       (0.8)   vertical bulge at midpoint
 *   roll         (0.0)   plane tilt in degrees
 *   hitRadius    (1.0)
 */
object ArcSlashAbility : Ability {
    private data class InFlight(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val origin: Vec3,
        val forward: Vec3,
        val right: Vec3,
        val arc: Double,
        val radius: Double,
        val damage: Float,
        val knockback: Double,
        val duration: Int,
        val points: Int,
        val yOffset: Double,
        val height: Double,
        val rollRad: Double,
        val hitRadius: Double,
        var tick: Int = 0,
        val alreadyHit: MutableSet<UUID> = HashSet()
    )

    private val active: MutableList<InFlight> = ArrayList()

    private val FLASH_WHITE: ColorParticleOption = ColorParticleOption.create(ParticleTypes.FLASH, 1.0f, 1.0f, 1.0f)

    fun init() {
        ServerTickEvents.END_SERVER_TICK.register { _ -> tickAll() }
    }

    /** Drop all in-flight slashes (e.g. on server stop) so none tick into a freshly loaded world. */
    fun clear() {
        synchronized(active) { active.clear() }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("range", ctx.params.optD("radius", 3.5))
        val arcDeg = ctx.params.optD("arc", 140.0)
        val damage = ctx.params.optF("damage", 5f)
        val knockback = ctx.params.optD("knockback", 0.6)
        val duration = ctx.params.optI("duration", 5).coerceIn(1, 30)
        val points = ctx.params.optI("points", 24).coerceIn(6, 64)
        val yOffset = ctx.params.optD("yOffset", 1.1)
        val height = ctx.params.optD("height", 0.8)
        val rollDeg = ctx.params.optD("roll", 0.0)
        val hitRadius = ctx.params.optD("hitRadius", 1.0)

        val player = ctx.player
        val world = ctx.world
        val origin: Vec3 = player.position()
        val rawFwd = player.lookAngle
        val forward = Vec3(rawFwd.x, 0.0, rawFwd.z).normalize()
        val right = Vec3(-forward.z, 0.0, forward.x)

        world.playSound(null, origin.x, origin.y, origin.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 0.55f)

        synchronized(active) {
            active.add(InFlight(world, player, origin, forward, right,
                arcDeg, radius, damage, knockback, duration, points, yOffset, height,
                Math.toRadians(rollDeg), hitRadius))
        }
        return true
    }

    private fun tickAll() {
        synchronized(active) {
            val it = active.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.tick++
                stepSlash(s)
                if (s.tick >= s.duration) it.remove()
            }
        }
    }

    private fun stepSlash(s: InFlight) {
        val world = s.world

        if (s.tick == 1) {
            world.playSound(null, s.origin.x, s.origin.y, s.origin.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, 1.2f)
            world.playSound(null, s.origin.x, s.origin.y, s.origin.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 0.95f)
        }

        val prevT = (s.tick - 1).toDouble() / s.duration
        val currT = s.tick.toDouble() / s.duration
        val iStart = (prevT * s.points).toInt()
        val iEnd = minOf(s.points, (currT * s.points).toInt() + 1)

        for (pi in iStart..iEnd) {
            val t = pi.toDouble() / s.points
            val thetaDeg = -s.arc / 2.0 + t * s.arc
            val pos = arcPoint(s, thetaDeg, t)

            world.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0)
            world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 4, 0.07, 0.07, 0.07, 0.20)
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 2, 0.07, 0.07, 0.07, 0.14)
            if (pi % 2 == 0) {
                world.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.004)
            }
            if (pi == s.points / 2) {
                world.sendParticles(FLASH_WHITE, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0)
            }

            val box = AABB(
                pos.x - s.hitRadius, pos.y - s.hitRadius - 0.5, pos.z - s.hitRadius,
                pos.x + s.hitRadius, pos.y + s.hitRadius + 0.5, pos.z + s.hitRadius
            )
            val victims = world.getEntitiesOfClass(LivingEntity::class.java, box) {
                it !== s.owner && it.isAlive && !s.alreadyHit.contains(it.uuid)
            }
            for (victim in victims) {
                val src = world.damageSources().playerAttack(s.owner)
                victim.hurtServer(world, src, s.damage)
                s.alreadyHit.add(victim.uuid)

                val knock = victim.position().subtract(s.owner.position()).normalize()
                victim.push(knock.x * s.knockback, 0.4, knock.z * s.knockback)
                victim.hurtMarked = true

                val cx = victim.x; val cy = victim.y + victim.bbHeight / 2.0; val cz = victim.z
                world.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 16, 0.45, 0.45, 0.45, 0.28)
                world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, cx, cy, cz, 8, 0.3, 0.3, 0.3, 0.12)
                world.sendParticles(ParticleTypes.SWEEP_ATTACK, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0)
                world.playSound(null, victim.x, victim.y, victim.z,
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.85f)
            }
        }

        if (s.tick == s.duration) {
            val end = arcPoint(s, s.arc / 2.0, 1.0)
            world.sendParticles(FLASH_WHITE, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0)
            world.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 8, 0.3, 0.3, 0.3, 0.3)
            world.playSound(null, s.origin.x, s.origin.y, s.origin.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.7f)
        }
    }

    private fun arcPoint(s: InFlight, thetaDeg: Double, t: Double): Vec3 {
        val theta = Math.toRadians(thetaDeg)
        val hx = s.origin.x + s.radius * (cos(theta) * s.forward.x + sin(theta) * s.right.x)
        val hz = s.origin.z + s.radius * (cos(theta) * s.forward.z + sin(theta) * s.right.z)
        val vertArc = sin(Math.PI * t)
        val lateralFrac = t * 2.0 - 1.0
        val rollOffset = lateralFrac * sin(s.rollRad) * s.height
        val hy = s.origin.y + s.yOffset + s.height * vertArc + rollOffset
        return Vec3(hx, hy, hz)
    }
}
