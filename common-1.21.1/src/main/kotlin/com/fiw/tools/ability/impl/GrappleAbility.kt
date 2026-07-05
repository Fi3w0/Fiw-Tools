package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
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
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Grappling hook — latch onto a **block** under the crosshair (entity pulls already exist via
 * `grappling_pull`) and get reeled toward it over the next ticks, rope drawn in particles.
 * Sneak mid-flight to let go. Fall damage is suppressed while reeling and on arrival.
 */
object GrappleAbility : Ability {
    private class Hook(
        val world: ServerLevel,
        val owner: ServerPlayer,
        val anchor: Vec3,
        val pullSpeed: Double,
        val releaseDistance: Double,
        val maxTicks: Int,
        val ropeParticle: ParticleOptions,
        var tick: Int = 0
    )

    private val hooks: MutableList<Hook> = ArrayList()

    fun tick() {
        synchronized(hooks) {
            val it = hooks.iterator()
            while (it.hasNext()) {
                if (!step(it.next())) it.remove()
            }
        }
    }

    /** Drop all active hooks (server stop) so none tick into a freshly loaded world. */
    fun clear() {
        synchronized(hooks) { hooks.clear() }
    }

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world

        val range = p.optD("range", 24.0).coerceIn(4.0, 96.0)
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.normalize().scale(range))
        val hit = world.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) return false

        val fireSound = soundById(p.optS("fireSound", "minecraft:entity.fishing_bobber.throw"))
        if (fireSound != null) {
            world.playSound(null, player.x, player.y, player.z, fireSound, SoundSource.PLAYERS,
                p.optF("fireSoundVolume", 1.0f), p.optF("fireSoundPitch", 0.8f))
        }
        val latchSound = soundById(p.optS("latchSound", "minecraft:block.chain.place"))
        if (latchSound != null) {
            world.playSound(null, hit.location.x, hit.location.y, hit.location.z, latchSound,
                SoundSource.PLAYERS, p.optF("latchSoundVolume", 1.0f), p.optF("latchSoundPitch", 0.8f))
        }

        // Initial hop so ground friction doesn't eat the first pull tick.
        player.push(0.0, p.optD("launchBoost", 0.35), 0.0)
        player.hurtMarked = true

        synchronized(hooks) {
            hooks.removeIf { it.owner === player } // one hook per player
            hooks.add(Hook(
                world = world, owner = player, anchor = hit.location,
                pullSpeed = p.optD("pullSpeed", 0.9).coerceIn(0.2, 3.0),
                releaseDistance = p.optD("releaseDistance", 1.5),
                maxTicks = p.optI("maxTicks", 60).coerceIn(5, 200),
                ropeParticle = p.optParticle("ropeParticle", ParticleTypes.CRIT)
            ))
        }
        return true
    }

    /** Advances [h] one tick; false removes it. */
    private fun step(h: Hook): Boolean {
        val owner = h.owner
        if (owner.isRemoved || !owner.isAlive || owner.level() !== h.world) return false
        h.tick++
        if (h.tick > h.maxTicks) return false
        if (owner.isShiftKeyDown && h.tick > 2) return false // let go

        val from = owner.position().add(0.0, owner.bbHeight * 0.5, 0.0)
        val toAnchor = h.anchor.subtract(from)
        val distance = toAnchor.length()
        if (distance <= h.releaseDistance) {
            owner.fallDistance = 0.0f
            return false
        }

        val dir = toAnchor.scale(1.0 / distance)
        owner.setDeltaMovement(dir.scale(h.pullSpeed))
        owner.hurtMarked = true
        owner.fallDistance = 0.0f

        // Rope: sparse particles from hand to anchor.
        val steps = distance.toInt().coerceIn(1, 32)
        for (s in 0..steps) {
            val t = s.toDouble() / steps
            h.world.sendParticles(h.ropeParticle,
                from.x + toAnchor.x * t, from.y + toAnchor.y * t, from.z + toAnchor.z * t,
                1, 0.0, 0.0, 0.0, 0.0)
        }
        return true
    }

    private fun soundById(id: String): SoundEvent? {
        if (id.isEmpty()) return null
        val sid = ResourceLocation.tryParse(id) ?: return null
        return BuiltInRegistries.SOUND_EVENT.get(sid)
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
