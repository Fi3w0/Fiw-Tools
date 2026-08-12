package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import com.fiw.tools.util.HolderAccess
import com.fiw.tools.util.TextParser
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Teleport-behind (`teleport_behind`) — a standalone version of the arc-slash barrage's finisher.
 * From `on_attack` (uses the struck target) or `on_right_click` (the entity under the crosshair,
 * within `targetRange`), the caster blinks to a safe spot `behindDistance` blocks behind the
 * target's back and optionally lands one armor-piercing hit. With no target it does nothing (there
 * is no "behind" to reach), so its cooldown isn't spent. Every particle, sound and message is a param.
 */
object TeleportBehindAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val target = ctx.target ?: findLookTarget(player, world, p.optD("targetRange", 16.0)) ?: return false
        if (target.isRemoved || !target.isAlive || target.level() !== world) return false

        // A spot directly behind the target's back, pulled in to solid ground if that spot is blocked.
        val back = target.lookAngle
        val flatRaw = Vec3(back.x, 0.0, back.z)
        val flat = if (flatRaw.length() > 0.05) flatRaw.normalize()
        else target.position().subtract(player.position()).let { Vec3(it.x, 0.0, it.z) }.normalize()
        var dest = target.position().subtract(flat.scale(p.optD("behindDistance", 2.0)))
        if (!isOpen(world, dest)) dest = target.position()

        player.teleportTo(dest.x, dest.y, dest.z)

        val sound = soundById(p.optS("sound", "minecraft:entity.enderman.teleport"))
        if (sound != null) {
            world.playSound(null, dest.x, dest.y, dest.z, sound, SoundSource.PLAYERS,
                p.optF("soundVolume", 1.0f), p.optF("soundPitch", 1.0f))
        }
        val burst = p.optParticle("particle", ParticleTypes.PORTAL)
        emitBurst(world, dest.add(0.0, 1.0, 0.0), burst, p.optI("particleCount", 40))

        val damage = p.optF("damage", 0f)
        if (damage > 0f) {
            dealDamage(world, player, target, damage, pierceFraction(p, "ignoreArmor", 1.0))
            world.sendParticles(ParticleTypes.CRIT, target.x, target.y + target.bbHeight / 2.0, target.z,
                12, 0.3, 0.4, 0.3, 0.2)
        }
        val message = p.optS("message", "")
        if (message.isNotEmpty()) player.displayClientMessage(TextParser.parse(message), true)
        return true
    }

    private fun findLookTarget(player: ServerPlayer, world: ServerLevel, range: Double): LivingEntity? {
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.normalize().scale(range))
        val sweep = player.boundingBox.expandTowards(end.subtract(eye)).inflate(1.0)
        var best: LivingEntity? = null
        var bestDist = Double.MAX_VALUE
        for (e in world.getEntitiesOfClass(LivingEntity::class.java, sweep) { it !== player && it.isAlive }) {
            val hit = e.boundingBox.inflate(0.3).clip(eye, end).orElse(null) ?: continue
            val d = eye.distanceToSqr(hit)
            if (d < bestDist) { bestDist = d; best = e }
        }
        return best
    }

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
                target.invulnerableTime = 0
                target.hurtServer(world, magicSrc, pierced)
            }
        }
    }

    private fun emitBurst(world: ServerLevel, at: Vec3, particle: ParticleOptions, amount: Int) {
        world.sendParticles(particle, at.x, at.y, at.z, amount, 0.4, 0.6, 0.4, 0.05)
    }

    private fun isOpen(world: ServerLevel, pos: Vec3): Boolean {
        val feet = BlockPos.containing(pos)
        return world.getBlockState(feet).getCollisionShape(world, feet).isEmpty &&
            world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty
    }

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
