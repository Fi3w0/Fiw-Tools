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
import com.fiw.tools.util.HolderAccess
import com.google.gson.JsonObject
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

/**
 * Sonic boom — a warden-style concussive shot. A short ribcage-rattling charge at the caster's
 * chest, then one instant shockwave ray along the crosshair that rips **through walls** by
 * default, hits everything in its path with armor-ignoring damage and hurls victims away.
 * One shot per cast — the anti-turtle answer to shields and cover.
 */
object SonicBoomAbility : Ability {
    private class Charge(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val windup: Int,
        val damage: Float,
        val width: Double,
        val range: Double,
        val knockback: Double,
        val pierce: Double,
        val throughBlocks: Boolean,
        val affects: AffectScope,
        val boomParticle: ParticleOptions,
        val particleSpacing: Double,
        val chargeParticle: ParticleOptions,
        val fireSound: SoundEvent?,
        val fireVolume: Float,
        val firePitch: Float,
        var tick: Int = 0
    )

    private val charges: MutableList<Charge> = ArrayList()

    fun tick() {
        synchronized(charges) {
            val it = charges.iterator()
            while (it.hasNext()) {
                if (!step(it.next())) it.remove()
            }
        }
    }

    /** Drop all pending booms (server stop) so none tick into a freshly loaded world. */
    fun clear() {
        synchronized(charges) { charges.clear() }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val chargeSound = soundById(p.optS("chargeSound", "minecraft:entity.warden.sonic_charge"))
        if (chargeSound != null) {
            world.playSound(null, player.x, player.y, player.z, chargeSound, SoundSource.PLAYERS,
                p.optF("chargeSoundVolume", 2.0f), p.optF("chargeSoundPitch", 1.0f))
        }

        synchronized(charges) {
            charges.add(Charge(
                world = world, owner = player,
                windup = p.optI("windup", 10).coerceIn(0, 100),
                damage = p.optF("damage", 12f),
                width = p.optD("width", 1.2),
                range = p.optD("range", 24.0).coerceIn(2.0, 96.0),
                knockback = p.optD("knockback", 1.5),
                pierce = pierceFraction(p, "ignoreArmor", 1.0),
                throughBlocks = p.optB("throughBlocks", true),
                affects = p.scope("affects", AffectScope.ALL),
                boomParticle = p.optParticle("boomParticle", ParticleTypes.SONIC_BOOM),
                particleSpacing = p.optD("particleSpacing", 1.0).coerceIn(0.25, 4.0),
                chargeParticle = p.optParticle("chargeParticle", ParticleTypes.SCULK_CHARGE_POP),
                fireSound = soundById(p.optS("fireSound", "minecraft:entity.warden.sonic_boom")),
                fireVolume = p.optF("fireSoundVolume", 2.0f),
                firePitch = p.optF("fireSoundPitch", 1.0f)
            ))
        }
        return true
    }

    /** Advances [c] one tick; false removes it. */
    private fun step(c: Charge): Boolean {
        val owner = c.owner
        if (owner.isRemoved || !owner.isAlive || owner.level() !== c.world) return false
        c.tick++

        val origin = owner.eyePosition.subtract(0.0, 0.3, 0.0)

        // ── Charge: sculk pops crawling up the caster ─────────────────────────
        if (c.tick < c.windup) {
            val progress = c.tick.toDouble() / c.windup
            c.world.sendParticles(c.chargeParticle,
                owner.x, owner.y + 0.5 + progress * 1.2, owner.z,
                3, 0.3, 0.2, 0.3, 0.02)
            return true
        }

        // ── Fire: one instant ray ─────────────────────────────────────────────
        val dir = owner.lookAngle.normalize()
        var end = origin.add(dir.scale(c.range))
        if (!c.throughBlocks) {
            end = c.world.clip(ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).location
        }
        val len = origin.distanceTo(end)

        if (c.fireSound != null) {
            c.world.playSound(null, owner.x, owner.y, owner.z, c.fireSound, SoundSource.PLAYERS,
                c.fireVolume, c.firePitch)
        }
        var d = 0.0
        while (d <= len) {
            c.world.sendParticles(c.boomParticle,
                origin.x + dir.x * d, origin.y + dir.y * d, origin.z + dir.z * d,
                1, 0.0, 0.0, 0.0, 0.0)
            d += c.particleSpacing
        }

        val broad = AABB(
            minOf(origin.x, end.x) - c.width, minOf(origin.y, end.y) - 1.5, minOf(origin.z, end.z) - c.width,
            maxOf(origin.x, end.x) + c.width, maxOf(origin.y, end.y) + 1.5, maxOf(origin.z, end.z) + c.width
        )
        val victims = c.world.getEntitiesOfClass(LivingEntity::class.java, broad) {
            it !== owner && it.isAlive && c.affects.matches(owner, it) &&
                isOnRay(Vec3(it.x, it.y + it.bbHeight / 2.0, it.z), origin, dir, len, c.width)
        }
        for (victim in victims) {
            dealDamage(c.world, owner, victim, c.damage, c.pierce)
            victim.push(dir.x * c.knockback, 0.3 + c.knockback * 0.2, dir.z * c.knockback)
            victim.hurtMarked = true
            c.world.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                victim.x, victim.y + 1.0, victim.z, 6, 0.25, 0.25, 0.25, 0.08)
        }
        return false
    }

    /** Is [point] within [width] of the ray segment? */
    private fun isOnRay(point: Vec3, origin: Vec3, dir: Vec3, len: Double, width: Double): Boolean {
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

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = ResourceLocation.tryParse(id) ?: return null
        val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return null
        return HolderAccess.value(holder)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
