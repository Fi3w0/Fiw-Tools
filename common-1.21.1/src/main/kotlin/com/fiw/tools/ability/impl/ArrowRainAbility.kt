package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optParticle
import com.google.gson.JsonObject
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Arrow rain — mark a zone at the crosshair (up to `targetRange`, or the wall you're looking
 * at), telegraph it with a shrinking particle ring for `delay` ticks, then real arrows pour
 * from the sky over it in `waves`. Arrows carry the caster's kill credit, can't be picked up
 * (no free ammo), and hit whatever they hit — friend or foe, like any arrow.
 */
object ArrowRainAbility : Ability {
    private class Rain(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val center: Vec3,
        val radius: Double,
        val delay: Int,
        val waves: Int,
        val waveInterval: Int,
        val perWave: Int,
        val height: Double,
        val speed: Double,
        val baseDamage: Double,
        val flaming: Boolean,
        val critical: Boolean,
        val markerParticle: ParticleOptions,
        val rainSound: SoundEvent?,
        val rainVolume: Float,
        val rainPitch: Float,
        var tick: Int = 0,
        var wavesFired: Int = 0
    )

    private val rains: MutableList<Rain> = ArrayList()

    fun tick() {
        synchronized(rains) {
            val it = rains.iterator()
            while (it.hasNext()) {
                if (!step(it.next())) it.remove()
            }
        }
    }

    /** Drop all pending rains (server stop) so none tick into a freshly loaded world. */
    fun clear() {
        synchronized(rains) { rains.clear() }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        // Zone lands where the crosshair points — a wall, the ground, or max range in the air.
        val range = p.optD("targetRange", 40.0).coerceIn(4.0, 128.0)
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.normalize().scale(range))
        val center = ctx.targetPos
            ?: world.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).location

        val castSound = soundById(p.optS("castSound", "minecraft:entity.arrow.shoot"))
        if (castSound != null) {
            world.playSound(null, player.x, player.y, player.z, castSound, SoundSource.PLAYERS,
                p.optF("castSoundVolume", 1.0f), p.optF("castSoundPitch", 0.8f))
        }

        val count = p.optI("count", 20).coerceIn(1, 200)
        val waves = p.optI("waves", 4).coerceIn(1, 20)
        synchronized(rains) {
            rains.add(Rain(
                world = world, owner = player, center = center,
                radius = p.optD("radius", 4.0).coerceIn(0.5, 24.0),
                delay = p.optI("delay", 10).coerceIn(0, 200),
                waves = waves,
                waveInterval = p.optI("waveInterval", 3).coerceAtLeast(1),
                perWave = (count + waves - 1) / waves,
                height = p.optD("height", 12.0).coerceIn(3.0, 48.0),
                speed = p.optD("arrowSpeed", 1.5).coerceIn(0.4, 4.0),
                baseDamage = p.optD("damage", 4.0),
                flaming = p.optB("flaming", false),
                critical = p.optB("critical", false),
                markerParticle = p.optParticle("markerParticle", ParticleTypes.CLOUD),
                rainSound = soundById(p.optS("rainSound", "minecraft:entity.arrow.shoot")),
                rainVolume = p.optF("rainSoundVolume", 0.8f),
                rainPitch = p.optF("rainSoundPitch", 1.1f)
            ))
        }
        return true
    }

    /** Advances [r] one tick; false removes it. */
    private fun step(r: Rain): Boolean {
        if (r.owner.isRemoved || r.owner.level() !== r.world) return false
        r.tick++

        // ── Telegraph: ring shrinking onto the zone ───────────────────────────
        if (r.tick <= r.delay) {
            val shrink = 1.0 + 0.6 * (1.0 - r.tick.toDouble() / r.delay)
            val points = 16
            for (i in 0 until points) {
                val a = Math.toRadians(360.0 / points * i + r.tick * 9.0)
                r.world.sendParticles(r.markerParticle,
                    r.center.x + cos(a) * r.radius * shrink,
                    r.center.y + 0.2,
                    r.center.z + sin(a) * r.radius * shrink,
                    1, 0.0, 0.05, 0.0, 0.0)
            }
            return true
        }

        // ── Waves of real arrows from above ───────────────────────────────────
        if ((r.tick - r.delay - 1) % r.waveInterval == 0 && r.wavesFired < r.waves) {
            r.wavesFired++
            for (i in 0 until r.perWave) {
                val a = Math.random() * Math.PI * 2
                val d = Math.sqrt(Math.random()) * r.radius
                val x = r.center.x + cos(a) * d
                val z = r.center.z + sin(a) * d
                val arrow = EntityType.ARROW.create(r.world) ?: continue
                arrow.setOwner(r.owner)
                arrow.setPos(x, r.center.y + r.height + Math.random() * 2.0, z)
                arrow.setDeltaMovement(
                    (Math.random() - 0.5) * 0.15,
                    -r.speed * (0.85 + Math.random() * 0.3),
                    (Math.random() - 0.5) * 0.15
                )
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED
                arrow.setBaseDamage(r.baseDamage)
                if (r.critical) arrow.setCritArrow(true)
                if (r.flaming) arrow.remainingFireTicks = 100
                r.world.addFreshEntity(arrow)
            }
            if (r.rainSound != null) {
                r.world.playSound(null, r.center.x, r.center.y + r.height, r.center.z,
                    r.rainSound, SoundSource.PLAYERS, r.rainVolume,
                    r.rainPitch + (Math.random().toFloat() - 0.5f) * 0.2f)
            }
        }
        return r.wavesFired < r.waves
    }

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = ResourceLocation.tryParse(id) ?: return null
        return BuiltInRegistries.SOUND_EVENT.get(sid)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
