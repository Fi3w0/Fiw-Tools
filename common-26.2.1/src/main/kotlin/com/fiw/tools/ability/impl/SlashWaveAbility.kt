package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.ParticleSpec
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import com.fiw.tools.util.HolderAccess
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flying crescent slash — a ranged cut launched from the weapon. A curved blade of particles
 * (purple by default, matching the arc-slash family) travels along the crosshair, cutting every
 * entity it passes through once. `waves` > 1 fires a fan spread across `spreadAngle` degrees;
 * `roll` tilts each crescent (0 flat, 90 vertical). Piercing by default; stops at walls.
 */
object SlashWaveAbility : Ability {
    private data class Burst(val particle: ParticleOptions, val amount: Int, val hSpread: Double, val vSpread: Double, val speed: Double)

    private class Wave(
        val world: ServerLevel,
        val owner: ServerPlayer,
        var pos: Vec3,
        val dir: Vec3,
        /** Crescent lateral axis (right/up rotated by roll around dir). */
        val axis: Vec3,
        val speed: Double,
        val range: Double,
        val width: Double,
        val arc: Double,
        val points: Int,
        val damage: Float,
        val knockback: Double,
        val hitRadius: Double,
        val pierce: Double,
        val piercing: Boolean,
        val throughBlocks: Boolean,
        val affects: AffectScope,
        val particles: List<Burst>,
        val hitSound: SoundEvent?,
        val hitVolume: Float,
        val hitPitch: Float,
        var travelled: Double = 0.0,
        val alreadyHit: MutableSet<UUID> = HashSet()
    )

    private val waves: MutableList<Wave> = ArrayList()

    private val DEFAULT_PARTICLES: List<Burst> = listOf(
        Burst(ParticleTypes.WITCH, 2, 0.05, 0.05, 0.0),
        Burst(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f), 1, 0.02, 0.02, 0.01),
        Burst(ParticleTypes.ELECTRIC_SPARK, 1, 0.05, 0.05, 0.0)
    )

    fun tick() {
        synchronized(waves) {
            val it = waves.iterator()
            while (it.hasNext()) {
                if (!step(it.next())) it.remove()
            }
        }
    }

    /** Drop all in-flight waves (server stop) so none tick into a freshly loaded world. */
    fun clear() {
        synchronized(waves) { waves.clear() }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val count = p.optI("waves", 1).coerceIn(1, 9)
        val spread = p.optD("spreadAngle", 15.0)
        val rollRad = Math.toRadians(p.optD("roll", 0.0))
        val speed = p.optD("speed", 1.2).coerceIn(0.2, 4.0)
        val range = p.optD("range", 24.0).coerceIn(2.0, 96.0)
        val width = p.optD("width", 2.5)
        val arc = p.optD("arc", 150.0)
        val points = p.optI("points", 16).coerceIn(4, 48)
        val damage = p.optF("damage", 6f)
        val knockback = p.optD("knockback", 0.4)
        val hitRadius = p.optD("hitRadius", 1.2)
        val pierce = pierceFraction(p, "ignoreArmor", 0.0)
        val piercing = p.optB("piercing", true)
        val throughBlocks = p.optB("throughBlocks", false)
        val affects = p.scope("affects", AffectScope.ALL)
        val particles = parseBursts(p, "waveParticles", DEFAULT_PARTICLES)
        val hitSound = soundById(p.optS("hitSound", "minecraft:entity.player.attack.crit"))

        val baseDir = player.lookAngle.normalize()
        val origin = player.eyePosition.subtract(0.0, 0.3, 0.0)

        val launchSound = soundById(p.optS("launchSound", "minecraft:entity.player.attack.sweep"))
        if (launchSound != null) {
            world.playSound(null, player.x, player.y, player.z, launchSound, SoundSource.PLAYERS,
                p.optF("launchSoundVolume", 1.0f), p.optF("launchSoundPitch", 0.7f))
        }

        synchronized(waves) {
            for (i in 0 until count) {
                // Fan the extra waves horizontally around the center one.
                val yawOffset = if (count == 1) 0.0 else (i - (count - 1) / 2.0) * Math.toRadians(spread)
                val dir = rotateYaw(baseDir, yawOffset)
                val axis = crescentAxis(dir, rollRad)
                waves.add(Wave(world, player, origin, dir, axis, speed, range, width, arc, points,
                    damage, knockback, hitRadius, pierce, piercing, throughBlocks, affects, particles,
                    hitSound, p.optF("hitSoundVolume", 1.0f), p.optF("hitSoundPitch", 0.9f)))
            }
        }
        return true
    }

    /** Advances [w] one tick in sub-steps; false removes it. */
    private fun step(w: Wave): Boolean {
        if (w.owner.isRemoved || w.owner.level() !== w.world) return false
        var remaining = w.speed
        while (remaining > 0) {
            val stepLen = minOf(0.6, remaining)
            remaining -= stepLen
            val next = w.pos.add(w.dir.scale(stepLen))
            if (!w.throughBlocks) {
                val hit = w.world.clip(ClipContext(w.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w.owner))
                if (hit.location.distanceToSqr(w.pos) < next.distanceToSqr(w.pos) - 1.0e-4) {
                    drawCrescent(w, hit.location)
                    return false
                }
            }
            w.pos = next
            w.travelled += stepLen
            drawCrescent(w, w.pos)
            if (!hitEntities(w)) return false
            if (w.travelled >= w.range) return false
        }
        return true
    }

    private fun drawCrescent(w: Wave, center: Vec3) {
        for (pi in 0..w.points) {
            val theta = Math.toRadians(-w.arc / 2.0 + (pi.toDouble() / w.points) * w.arc)
            val pos = crescentPoint(w, center, theta)
            for (b in w.particles) {
                w.world.sendParticles(b.particle, pos.x, pos.y, pos.z, b.amount, b.hSpread, b.vSpread, b.hSpread, b.speed)
            }
        }
    }

    /** True to keep flying; false when a non-piercing wave connected. */
    private fun hitEntities(w: Wave): Boolean {
        val reach = w.width + w.hitRadius
        val box = AABB.ofSize(w.pos, reach * 2, reach * 2, reach * 2)
        val victims = w.world.getEntitiesOfClass(LivingEntity::class.java, box) { e ->
            e !== w.owner && e.isAlive && !w.alreadyHit.contains(e.uuid) &&
                w.affects.matches(w.owner, e) && touchesCrescent(w, e)
        }
        for (victim in victims) {
            dealDamage(w.world, w.owner, victim, w.damage, w.pierce)
            w.alreadyHit.add(victim.uuid)
            if (w.knockback > 0.0) {
                victim.push(w.dir.x * w.knockback, 0.25, w.dir.z * w.knockback)
                victim.hurtMarked = true
            }
            w.world.sendParticles(ParticleTypes.CRIT, victim.x, victim.y + victim.bbHeight / 2.0, victim.z,
                10, 0.3, 0.3, 0.3, 0.2)
            if (w.hitSound != null) {
                w.world.playSound(null, victim.x, victim.y, victim.z, w.hitSound, SoundSource.PLAYERS,
                    w.hitVolume, w.hitPitch)
            }
        }
        return w.piercing || victims.isEmpty()
    }

    private fun touchesCrescent(w: Wave, e: LivingEntity): Boolean {
        val center = Vec3(e.x, e.y + e.bbHeight / 2.0, e.z)
        for (pi in 0..w.points) {
            val theta = Math.toRadians(-w.arc / 2.0 + (pi.toDouble() / w.points) * w.arc)
            if (crescentPoint(w, w.pos, theta).distanceToSqr(center) <= w.hitRadius * w.hitRadius) return true
        }
        return false
    }

    /** Point on the crescent: spans the lateral axis, edges trailing back along the flight path. */
    private fun crescentPoint(w: Wave, center: Vec3, theta: Double): Vec3 {
        val lateral = w.width * sin(theta)
        val trail = w.width * (cos(theta) - 1.0) * 0.4
        return Vec3(
            center.x + w.axis.x * lateral + w.dir.x * trail,
            center.y + w.axis.y * lateral + w.dir.y * trail,
            center.z + w.axis.z * lateral + w.dir.z * trail
        )
    }

    /** Lateral crescent axis: horizontal right vector rotated `roll` around the flight direction. */
    private fun crescentAxis(dir: Vec3, rollRad: Double): Vec3 {
        val worldUp = if (abs(dir.y) > 0.98) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val right = dir.cross(worldUp).normalize()
        val up = right.cross(dir).normalize()
        return right.scale(cos(rollRad)).add(up.scale(sin(rollRad))).normalize()
    }

    private fun rotateYaw(v: Vec3, angle: Double): Vec3 {
        val c = cos(angle)
        val s = sin(angle)
        return Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c).normalize()
    }

    /** Deals [damage], routing [pierce] (0..1) of it through an armor-bypassing magic source. */
    private fun dealDamage(world: ServerLevel, owner: ServerPlayer, target: LivingEntity, damage: Float, pierce: Double) {
        if (damage <= 0f) return
        val normalSrc = world.damageSources().playerAttack(owner)
        val magicSrc = world.damageSources().indirectMagic(owner, owner)
        when {
            pierce >= 1.0 -> target.hurtServer(world, magicSrc, damage)
            pierce <= 0.0 -> target.hurtServer(world, normalSrc, damage)
            else -> {
                val pierced = (damage * pierce).toFloat()
                target.hurtServer(world, normalSrc, damage - pierced)
                // Second source lands inside the invulnerability window; reopen it.
                target.invulnerableTime = 0
                target.hurtServer(world, magicSrc, pierced)
            }
        }
    }

    /** Bool-or-number param: true = 1.0, false = 0.0, 0..1 = fraction, >1 = percent. */
    private fun pierceFraction(p: JsonObject?, key: String, default: Double): Double {
        val prim = p?.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return default
        return when {
            prim.isBoolean -> if (prim.asBoolean) 1.0 else 0.0
            prim.isNumber -> prim.asDouble.let { if (it > 1.0) it / 100.0 else it }.coerceIn(0.0, 1.0)
            else -> default
        }
    }

    /** `[{particle, amount, hSpread, vSpread, speed}, ...]` or bare particle-id strings. */
    private fun parseBursts(p: JsonObject, key: String, default: List<Burst>): List<Burst> {
        val arr = p.getAsJsonArray(key) ?: return default
        val out = ArrayList<Burst>()
        for (el in arr) {
            if (el.isJsonPrimitive) {
                out.add(Burst(ParticleSpec.parse(el.asString), 1, 0.05, 0.05, 0.0))
            } else if (el.isJsonObject) {
                val o = el.asJsonObject
                out.add(Burst(
                    ParticleSpec.parse(o.get("particle")?.takeIf { it.isJsonPrimitive }?.asString),
                    o.optI("amount", 1), o.optD("hSpread", 0.05), o.optD("vSpread", 0.05), o.optD("speed", 0.0)
                ))
            }
        }
        return if (out.isEmpty()) default else out
    }

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = Identifier.tryParse(id) ?: return null
        val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return null
        return HolderAccess.value(holder)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
