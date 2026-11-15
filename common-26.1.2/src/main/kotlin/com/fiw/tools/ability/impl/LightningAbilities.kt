package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import com.fiw.tools.ability.scope
import com.fiw.tools.util.HolderAccess
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** Spawns one lightning bolt at [pos] and deals [damage] to everything within [aoeRadius]. */
private fun strikeBolt(
    world: ServerLevel, owner: ServerPlayer, pos: Vec3, cosmetic: Boolean,
    damage: Float, aoeRadius: Double, affects: AffectScope, particle: ParticleOptions
) {
    val bolt = EntityType.LIGHTNING_BOLT.create(world, EntitySpawnReason.TRIGGERED)
    if (bolt != null) {
        bolt.setPos(pos.x, pos.y, pos.z)
        bolt.setVisualOnly(cosmetic)  // cosmetic = no fire / mob conversion; damage is dealt below
        world.addFreshEntity(bolt)
    }
    world.sendParticles(particle, pos.x, pos.y + 0.5, pos.z, 16, 0.4, 1.0, 0.4, 0.02)
    if (damage <= 0f || aoeRadius <= 0.0) return
    val src = world.damageSources().indirectMagic(owner, owner)
    for (victim in collectTargets(world, pos.add(0.0, 1.0, 0.0), aoeRadius, owner, affects)) {
        victim.hurtServer(world, src, damage)
    }
}

/** Snaps [pos] down onto the nearest solid surface within a few blocks, so bolts land on the ground. */
private fun groundAt(world: ServerLevel, owner: ServerPlayer, pos: Vec3): Vec3 {
    val from = pos.add(0.0, 4.0, 0.0)
    val to = pos.subtract(0.0, 8.0, 0.0)
    val hit = world.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner))
    return hit.location
}

private fun lightningSound(id: String): SoundEvent? {
    if (id.isEmpty()) return null
    val sid = Identifier.tryParse(id) ?: return null
    val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return null
    return HolderAccess.value(holder)
}

private fun JsonObject?.optSl(key: String, default: String): String =
    this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default

/**
 * Lightning rain (`lightning_rain`) — mark a circle at the crosshair (up to `targetRange`), telegraph
 * it for `delay` ticks, then rain bolts down inside it on a fully custom schedule. Give a `groups`
 * array of `{count, delayTicks}` to say exactly how the bolts arrive (e.g. one, then one 10t later,
 * then three together); omit it for `count` bolts spread evenly over `waves`, `waveInterval` apart.
 * Each bolt is cosmetic by default (no fire / mob conversion) and damages everything within
 * `aoeRadius` of where it lands.
 */
object LightningRainAbility : Ability {
    private class Rain(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val center: Vec3,
        val radius: Double,
        val delay: Int,
        /** Sorted (tickOffset, boltCount) entries; tick is measured from cast. */
        val schedule: List<Pair<Int, Int>>,
        val damage: Float,
        val aoeRadius: Double,
        val cosmetic: Boolean,
        val affects: AffectScope,
        val markerParticle: ParticleOptions,
        val boltParticle: ParticleOptions,
        val strikeSound: SoundEvent?,
        val strikeVolume: Float,
        val strikePitch: Float,
        var tick: Int = 0,
        var fired: Int = 0
    )

    private val rains: MutableList<Rain> = ArrayList()

    fun tick() {
        synchronized(rains) {
            val it = rains.iterator()
            while (it.hasNext()) { if (!step(it.next())) it.remove() }
        }
    }

    fun clear() { synchronized(rains) { rains.clear() } }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val range = p.optD("targetRange", 40.0).coerceIn(4.0, 128.0)
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.normalize().scale(range))
        val center = ctx.targetPos
            ?: world.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).location

        val delay = p.optI("delay", 10).coerceIn(0, 200)
        val castSound = lightningSound(p.optSl("castSound", "minecraft:entity.lightning_bolt.thunder"))
        if (castSound != null) {
            world.playSound(null, player.x, player.y, player.z, castSound, SoundSource.PLAYERS,
                p.optF("castSoundVolume", 0.6f), p.optF("castSoundPitch", 1.2f))
        }

        synchronized(rains) {
            rains.add(Rain(
                world = world, owner = player, center = center,
                radius = p.optD("radius", 5.0).coerceIn(0.5, 32.0),
                delay = delay,
                schedule = buildSchedule(p, delay),
                damage = p.optF("damage", 5f),
                aoeRadius = p.optD("aoeRadius", 2.5).coerceIn(0.0, 16.0),
                cosmetic = p.optB("cosmetic", true),
                affects = p.scope("affects", AffectScope.ALL),
                markerParticle = p.optParticle("markerParticle", ParticleTypes.ELECTRIC_SPARK),
                boltParticle = p.optParticle("boltParticle", ParticleTypes.ELECTRIC_SPARK),
                strikeSound = lightningSound(p.optSl("strikeSound", "minecraft:entity.lightning_bolt.impact")),
                strikeVolume = p.optF("strikeSoundVolume", 0.7f),
                strikePitch = p.optF("strikeSoundPitch", 1.0f)
            ))
        }
        return true
    }

    /** `groups: [{count, delayTicks}]` → absolute (tick, count) offsets; else evenly spread bolts. */
    private fun buildSchedule(p: JsonObject, delay: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        val groups = p.getAsJsonArray("groups")
        if (groups != null && groups.size() > 0) {
            var t = delay
            for (g in groups) {
                val o = g.asJsonObject
                t += o.optI("delayTicks", 0).coerceAtLeast(0)
                out.add(t to o.optI("count", 1).coerceAtLeast(1))
            }
            return out
        }
        val count = p.optI("count", 12).coerceIn(1, 400)
        val waves = p.optI("waves", count).coerceIn(1, count)
        val interval = p.optI("waveInterval", 8).coerceAtLeast(1)
        val perWave = (count + waves - 1) / waves
        var remaining = count
        var t = delay
        for (w in 0 until waves) {
            if (remaining <= 0) break
            val n = minOf(perWave, remaining)
            out.add(t to n)
            remaining -= n
            t += interval
        }
        return out
    }

    private fun step(r: Rain): Boolean {
        if (r.owner.isRemoved || r.owner.level() !== r.world) return false
        r.tick++

        if (r.tick <= r.delay) {
            val shrink = 1.0 + 0.6 * (1.0 - r.tick.toDouble() / r.delay.coerceAtLeast(1))
            val points = 16
            for (i in 0 until points) {
                val a = Math.toRadians(360.0 / points * i + r.tick * 9.0)
                r.world.sendParticles(r.markerParticle,
                    r.center.x + cos(a) * r.radius * shrink, r.center.y + 0.2, r.center.z + sin(a) * r.radius * shrink,
                    1, 0.0, 0.05, 0.0, 0.0)
            }
            return true
        }

        // Fire every schedule entry whose tick has arrived (entries are in ascending tick order).
        while (r.fired < r.schedule.size && r.schedule[r.fired].first <= r.tick) {
            val (_, bolts) = r.schedule[r.fired]
            for (i in 0 until bolts) {
                val a = Math.random() * Math.PI * 2
                val d = Math.sqrt(Math.random()) * r.radius
                val spot = groundAt(r.world, r.owner, Vec3(r.center.x + cos(a) * d, r.center.y, r.center.z + sin(a) * d))
                strikeBolt(r.world, r.owner, spot, r.cosmetic, r.damage, r.aoeRadius, r.affects, r.boltParticle)
            }
            if (r.strikeSound != null) {
                r.world.playSound(null, r.center.x, r.center.y, r.center.z, r.strikeSound, SoundSource.PLAYERS,
                    r.strikeVolume, r.strikePitch + (Math.random().toFloat() - 0.5f) * 0.2f)
            }
            r.fired++
        }
        return r.fired < r.schedule.size
    }
}

/**
 * Lightning ray (`lightning_ray`) — a straight line of bolts that march forward from the caster's
 * look direction, one every `boltInterval` ticks, up to `range` blocks out, each landing on the
 * ground and damaging everything within `aoeRadius`. Cosmetic by default. Great for a boss's
 * "run or get fried" telegraphed lane.
 */
object LightningRayAbility : Ability {
    private class Ray(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val origin: Vec3,
        val dir: Vec3,
        val step: Double,
        val bolts: Int,
        val boltInterval: Int,
        val damage: Float,
        val aoeRadius: Double,
        val cosmetic: Boolean,
        val affects: AffectScope,
        val boltParticle: ParticleOptions,
        val strikeSound: SoundEvent?,
        val strikeVolume: Float,
        val strikePitch: Float,
        var tick: Int = 0,
        var index: Int = 0
    )

    private val rays: MutableList<Ray> = ArrayList()

    fun tick() {
        synchronized(rays) {
            val it = rays.iterator()
            while (it.hasNext()) { if (!step(it.next())) it.remove() }
        }
    }

    fun clear() { synchronized(rays) { rays.clear() } }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val look = player.lookAngle
        val flat = Vec3(look.x, 0.0, look.z)
        if (flat.length() < 0.01) return false
        val dir = flat.normalize()

        val range = p.optD("range", 16.0).coerceIn(2.0, 96.0)
        val step = p.optD("stepDistance", 2.0).coerceIn(0.5, 8.0)
        synchronized(rays) {
            rays.add(Ray(
                world = world, owner = player,
                origin = player.position(), dir = dir, step = step,
                bolts = (range / step).toInt().coerceAtLeast(1),
                boltInterval = p.optI("boltInterval", 4).coerceAtLeast(1),
                damage = p.optF("damage", 5f),
                aoeRadius = p.optD("aoeRadius", 2.0).coerceIn(0.0, 16.0),
                cosmetic = p.optB("cosmetic", true),
                affects = p.scope("affects", AffectScope.ALL),
                boltParticle = p.optParticle("boltParticle", ParticleTypes.ELECTRIC_SPARK),
                strikeSound = lightningSound(p.optSl("strikeSound", "minecraft:entity.lightning_bolt.impact")),
                strikeVolume = p.optF("strikeSoundVolume", 0.7f),
                strikePitch = p.optF("strikeSoundPitch", 1.0f)
            ))
        }
        return true
    }

    private fun step(r: Ray): Boolean {
        if (r.owner.isRemoved || r.owner.level() !== r.world) return false
        // The first bolt lands on cast; each following one after boltInterval ticks.
        if (r.tick % r.boltInterval == 0) {
            val out = r.step * (r.index + 1)
            val spot = groundAt(r.world, r.owner,
                r.origin.add(r.dir.x * out, 0.0, r.dir.z * out))
            strikeBolt(r.world, r.owner, spot, r.cosmetic, r.damage, r.aoeRadius, r.affects, r.boltParticle)
            if (r.strikeSound != null) {
                r.world.playSound(null, spot.x, spot.y, spot.z, r.strikeSound, SoundSource.PLAYERS,
                    r.strikeVolume, r.strikePitch)
            }
            r.index++
        }
        r.tick++
        return r.index < r.bolts
    }
}
