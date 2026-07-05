package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import com.fiw.tools.ability.scope
import com.fiw.tools.util.TextParser
import com.google.gson.JsonObject
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sustained energy beam — the Fiw Bosses beam attack in item form. A windup gathers spiraling
 * energy at the caster's eyes, then a solid particle beam fires from them along the crosshair
 * for `duration` ticks, damaging everything caught in its cylinder every tick. By default the
 * beam follows the caster's aim (a sweepable laser); `followLook: false` locks the direction
 * when the windup ends, like the boss version. Stops at the first wall unless `throughBlocks`.
 *
 * Every number, sound and particle is a param; defaults mirror the boss attack.
 */
object BeamAbility : Ability {
    private class Beam(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val windup: Int,
        val duration: Int,
        val damage: Float,
        val width: Double,
        val range: Double,
        val affects: AffectScope,
        val pierce: Double,
        val followLook: Boolean,
        val throughBlocks: Boolean,
        val coreParticle: ParticleOptions,
        val coreDensity: Double,
        val outerParticle: ParticleOptions,
        val outerDensity: Double,
        val chargeParticle: ParticleOptions,
        val chargeCoreParticle: ParticleOptions,
        val fireSound: SoundEvent?,
        val fireVolume: Float,
        val firePitch: Float,
        val humSound: SoundEvent?,
        val humVolume: Float,
        val humPitch: Float,
        val humInterval: Int,
        val endSound: SoundEvent?,
        val endVolume: Float,
        val endPitch: Float,
        var lockedDir: Vec3? = null,
        var tick: Int = 0
    )

    private val active: MutableList<Beam> = ArrayList()

    private val FLASH_WHITE: ColorParticleOption = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0f, 1.0f, 1.0f)

    fun tick() {
        synchronized(active) {
            val it = active.iterator()
            while (it.hasNext()) {
                if (!step(it.next())) it.remove()
            }
        }
    }

    /** Drop all in-flight beams (server stop) so none tick into a freshly loaded world. */
    fun clear() {
        synchronized(active) { active.clear() }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val startMessage = p.optS("startMessage", "")
        if (startMessage.isNotEmpty()) {
            val component = TextParser.parse(startMessage)
            val radius = p.optD("startMessageRadius", 48.0)
            for (other in world.players()) {
                if (other.position().distanceTo(player.position()) <= radius) other.sendSystemMessage(component)
            }
        }
        val chargeSound = soundById(p.optS("chargeSound", "minecraft:entity.elder_guardian.curse"))
        if (chargeSound != null) {
            world.playSound(null, player.x, player.y, player.z, chargeSound, SoundSource.PLAYERS,
                p.optF("chargeSoundVolume", 1.5f), p.optF("chargeSoundPitch", 1.0f))
        }

        val beam = Beam(
            world = world, owner = player,
            windup = p.optI("windup", 20).coerceIn(0, 200),
            duration = p.optI("duration", 50).coerceIn(1, 1200),
            damage = p.optF("damage", 3f),
            width = p.optD("width", 0.9),
            range = p.optD("range", 35.0).coerceIn(2.0, 128.0),
            affects = p.scope("affects", AffectScope.ALL),
            pierce = pierceFraction(p, "ignoreArmor", 1.0),
            followLook = p.optB("followLook", true),
            throughBlocks = p.optB("throughBlocks", false),
            coreParticle = p.optParticle("coreParticle", ParticleTypes.END_ROD),
            coreDensity = p.optD("coreDensity", 10.0).coerceIn(1.0, 20.0),
            outerParticle = p.optParticle("outerParticle", ParticleTypes.ELECTRIC_SPARK),
            outerDensity = p.optD("outerDensity", 5.0).coerceIn(0.0, 20.0),
            chargeParticle = p.optParticle("chargeParticle", ParticleTypes.ELECTRIC_SPARK),
            chargeCoreParticle = p.optParticle("chargeCoreParticle", ParticleTypes.END_ROD),
            fireSound = soundById(p.optS("fireSound", "minecraft:entity.guardian.attack")),
            fireVolume = p.optF("fireSoundVolume", 1.8f),
            firePitch = p.optF("fireSoundPitch", 0.6f),
            humSound = soundById(p.optS("humSound", "minecraft:entity.guardian.attack")),
            humVolume = p.optF("humSoundVolume", 0.6f),
            humPitch = p.optF("humSoundPitch", 1.3f),
            humInterval = p.optI("humInterval", 8).coerceAtLeast(1),
            endSound = soundById(p.optS("endSound", "minecraft:block.glass.break")),
            endVolume = p.optF("endSoundVolume", 1.0f),
            endPitch = p.optF("endSoundPitch", 0.8f)
        )
        synchronized(active) { active.add(beam) }
        return true
    }

    /** Advances [b] one tick; false removes it. */
    private fun step(b: Beam): Boolean {
        val owner = b.owner
        if (owner.isRemoved || !owner.isAlive || owner.level() !== b.world) return false
        b.tick++

        val origin = owner.eyePosition.subtract(0.0, 0.2, 0.0)
        val world = b.world

        // ── Windup: energy spirals inward at the eyes ─────────────────────────
        if (b.tick <= b.windup) {
            val progress = b.tick.toDouble() / b.windup
            val gatherRadius = 2.5 * (1.0 - progress)
            for (i in 0 until 6) {
                val angle = Math.toRadians(60.0 * i + b.tick * 15.0)
                world.sendParticles(b.chargeParticle,
                    origin.x + cos(angle) * gatherRadius,
                    origin.y + sin(angle) * gatherRadius * 0.4,
                    origin.z + sin(angle) * gatherRadius,
                    1, 0.0, 0.0, 0.0, 0.0)
            }
            world.sendParticles(b.chargeCoreParticle, origin.x, origin.y, origin.z,
                2 + (progress * 4).toInt(), 0.08, 0.08, 0.08, 0.0)

            if (b.tick == b.windup) {
                if (!b.followLook) b.lockedDir = owner.lookAngle.normalize()
                if (b.fireSound != null) {
                    world.playSound(null, owner.x, owner.y, owner.z, b.fireSound, SoundSource.PLAYERS,
                        b.fireVolume, b.firePitch)
                }
            }
            return true
        }

        // ── Firing ────────────────────────────────────────────────────────────
        if (b.tick == 1 && b.windup == 0 && b.fireSound != null) {
            world.playSound(null, owner.x, owner.y, owner.z, b.fireSound, SoundSource.PLAYERS,
                b.fireVolume, b.firePitch)
        }
        val dir = when {
            b.followLook -> owner.lookAngle.normalize()
            else -> b.lockedDir ?: owner.lookAngle.normalize().also { b.lockedDir = it }
        }
        var end = origin.add(dir.scale(b.range))
        if (!b.throughBlocks) {
            end = world.clip(ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).location
        }
        val len = origin.distanceTo(end)

        // Outer soft glow (sparse, slight spread) and inner bright core (dense, none).
        val outerSteps = ceil(len * b.outerDensity).toInt().coerceIn(1, 256)
        for (s in 0..outerSteps) {
            val t = s.toDouble() / outerSteps
            world.sendParticles(b.outerParticle,
                origin.x + dir.x * len * t, origin.y + dir.y * len * t, origin.z + dir.z * len * t,
                2, 0.1, 0.1, 0.1, 0.0)
        }
        val coreSteps = ceil(len * b.coreDensity).toInt().coerceIn(1, 512)
        for (s in 0..coreSteps) {
            val t = s.toDouble() / coreSteps
            world.sendParticles(b.coreParticle,
                origin.x + dir.x * len * t, origin.y + dir.y * len * t, origin.z + dir.z * len * t,
                1, 0.0, 0.0, 0.0, 0.0)
        }

        // Muzzle flash + impact burst.
        world.sendParticles(FLASH_WHITE, origin.x, origin.y, origin.z, 1, 0.0, 0.0, 0.0, 0.0)
        world.sendParticles(b.outerParticle, end.x, end.y, end.z, 6, 0.25, 0.25, 0.25, 0.1)
        world.sendParticles(b.coreParticle, end.x, end.y, end.z, 2, 0.15, 0.15, 0.15, 0.05)

        // Damage everything caught inside the beam cylinder.
        val broad = AABB(
            minOf(origin.x, end.x) - b.width, minOf(origin.y, end.y) - 1.5, minOf(origin.z, end.z) - b.width,
            maxOf(origin.x, end.x) + b.width, maxOf(origin.y, end.y) + 1.5, maxOf(origin.z, end.z) + b.width
        )
        val victims = world.getEntitiesOfClass(LivingEntity::class.java, broad) {
            it !== owner && it.isAlive && b.affects.matches(owner, it) &&
                isOnBeam(Vec3(it.x, it.y + it.bbHeight / 2.0, it.z), origin, dir, len, b.width)
        }
        for (victim in victims) {
            dealDamage(world, owner, victim, b.damage, b.pierce)
            world.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                victim.x, victim.y + 1.0, victim.z, 4, 0.2, 0.2, 0.2, 0.05)
        }

        if (b.humSound != null && b.tick % b.humInterval == 0) {
            world.playSound(null, owner.x, owner.y, owner.z, b.humSound, SoundSource.PLAYERS, b.humVolume, b.humPitch)
        }

        // ── Shutdown burst ────────────────────────────────────────────────────
        if (b.tick >= b.windup + b.duration) {
            world.sendParticles(b.outerParticle, origin.x, origin.y, origin.z, 20, 0.4, 0.4, 0.4, 0.15)
            if (b.endSound != null) {
                world.playSound(null, owner.x, owner.y, owner.z, b.endSound, SoundSource.PLAYERS, b.endVolume, b.endPitch)
            }
            return false
        }
        return true
    }

    /** Is [point] within [width] of the beam segment [origin]→[origin]+[dir]*[len]? */
    private fun isOnBeam(point: Vec3, origin: Vec3, dir: Vec3, len: Double, width: Double): Boolean {
        val toPoint = point.subtract(origin)
        val proj = toPoint.dot(dir)
        if (proj < 0 || proj > len) return false
        return origin.add(dir.scale(proj)).distanceToSqr(point) <= width * width
    }

    /** Deals [damage], routing [pierce] (0..1) of it through an armor-bypassing magic source. */
    private fun dealDamage(world: ServerLevel, owner: ServerPlayer, target: LivingEntity, damage: Float, pierce: Double) {
        if (damage <= 0f) return
        val normalSrc = world.damageSources().playerAttack(owner)
        val magicSrc = world.damageSources().indirectMagic(owner, owner)
        when {
            pierce >= 1.0 -> target.hurt(magicSrc, damage)
            pierce <= 0.0 -> target.hurt(normalSrc, damage)
            else -> {
                val pierced = (damage * pierce).toFloat()
                target.hurt(normalSrc, damage - pierced)
                // Second source lands inside the invulnerability window; reopen it.
                target.invulnerableTime = 0
                target.hurt(magicSrc, pierced)
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

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = ResourceLocation.tryParse(id) ?: return null
        return BuiltInRegistries.SOUND_EVENT.get(sid)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
