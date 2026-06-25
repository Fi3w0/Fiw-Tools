package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.ZoneEffects
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optParticle
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * Movement and survival abilities, all self-targeted.
 */

/** Blink with i-frames: short teleport in look direction plus a sliver of damage immunity. */
object PhaseDashAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val maxDistance = ctx.params.optD("distance", 5.0)
        val immunityTicks = ctx.params.optI("immunity", 10)
        val particle = ctx.params.optParticle("particle", ParticleTypes.PORTAL)
        val player = ctx.player
        val world = ctx.world

        val look = player.lookAngle.normalize()
        var travelled = 0.0
        var safe: Vec3 = player.position()
        while (travelled < maxDistance) {
            travelled += 0.5
            val candidate = player.position().add(look.x * travelled, 0.0, look.z * travelled)
            val pos = BlockPos.containing(candidate)
            val feet = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty
            val head = world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty
            if (!feet || !head) break
            val below = pos.below()
            if (!world.getBlockState(below).getCollisionShape(world, below).isEmpty) {
                safe = Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            }
        }

        emitBurst(world, player.position().add(0.0, 1.0, 0.0), particle)
        player.teleportTo(safe.x, safe.y, safe.z)
        if (immunityTicks > 0) {
            // Resistance V (amplifier 4) is full damage immunity for the duration.
            player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, immunityTicks, 4))
        }
        emitBurst(world, safe.add(0.0, 1.0, 0.0), particle)
        world.playSound(null, safe.x, safe.y, safe.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f)
        return true
    }

    private fun emitBurst(world: ServerLevel, at: Vec3, particle: ParticleOptions) {
        for (i in 0 until 24) {
            world.sendParticles(particle, at.x + (Math.random() - 0.5), at.y + Math.random() * 1.4, at.z + (Math.random() - 0.5), 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}

/** Cancels out fall damage by healing it back the tick after it lands. */
object FeatherFallAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val source = ctx.damageSource ?: return false
        if (!source.`is`(DamageTypeTags.IS_FALL)) return false
        val reduce = ctx.params.optF("reducePercent", 1.0f).coerceIn(0f, 1f)
        val heal = ctx.damageAmount * reduce
        if (heal <= 0f) return false
        val player = ctx.player
        // Heal after the fall damage has been applied this tick, so it negates rather than overheals.
        ZoneEffects.schedule(1) {
            if (player.isAlive) player.heal(heal)
        }
        ctx.world.sendParticles(ParticleTypes.CLOUD, player.x, player.y, player.z, 12, 0.3, 0.1, 0.3, 0.02)
        return true
    }
}

/** Clutch heal: once below a HP fraction, burst-heal + Resistance/Absorption. Lock it behind a long cooldown. */
object SecondWindAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val player = ctx.player
        if (player.maxHealth <= 0f) return false
        val threshold = ctx.params.optF("threshold", 0.35f)
        if (player.health / player.maxHealth > threshold) return false
        val heal = ctx.params.optF("heal", 8f)
        val resistDuration = ctx.params.optI("resistDuration", 100)
        val resistAmp = ctx.params.optI("resistAmplifier", 1)
        val absorbDuration = ctx.params.optI("absorptionDuration", 200)
        player.heal(heal)
        player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, resistDuration, resistAmp))
        player.addEffect(MobEffectInstance(MobEffects.ABSORPTION, absorbDuration, 1))
        val world = ctx.world
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.x, player.y + 1.0, player.z, 30, 0.4, 0.6, 0.4, 0.1)
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.0f)
        return true
    }
}

/** First cast anchors your spot; a second cast within range/time teleports you back to it. */
object EnderRecallAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val player = ctx.player
        val world = ctx.world
        val now = world.gameTime
        val maxDistance = ctx.params.optD("maxDistance", 32.0)
        val window = ctx.params.optI("window", 600)
        val dimension = world.dimension()
        val anchor = AbilityState.recallAnchors[player.uuid]

        if (anchor != null && anchor.dimension == dimension && now - anchor.setAtTick <= window) {
            val dx = player.x - anchor.x
            val dy = player.y - anchor.y
            val dz = player.z - anchor.z
            if (sqrt(dx * dx + dy * dy + dz * dz) <= maxDistance) {
                world.sendParticles(ParticleTypes.PORTAL, player.x, player.y + 1.0, player.z, 24, 0.4, 0.6, 0.4, 0.1)
                player.teleportTo(anchor.x, anchor.y, anchor.z)
                world.sendParticles(ParticleTypes.PORTAL, anchor.x, anchor.y + 1.0, anchor.z, 24, 0.4, 0.6, 0.4, 0.1)
                world.playSound(null, anchor.x, anchor.y, anchor.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f)
                AbilityState.recallAnchors.remove(player.uuid)
                return true
            }
        }

        AbilityState.recallAnchors[player.uuid] = AbilityState.Anchor(player.x, player.y, player.z, dimension, now)
        world.sendParticles(ParticleTypes.GLOW, player.x, player.y + 1.0, player.z, 16, 0.3, 0.5, 0.3, 0.0)
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.7f, 1.3f)
        return true
    }
}

/** A controlled upward boost for traversal — not flight. */
object LevitateSelfAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val power = ctx.params.optD("power", 0.8)
        val player = ctx.player
        player.push(0.0, power, 0.0)
        player.hurtMarked = true
        player.fallDistance = 0.0
        ctx.world.sendParticles(ParticleTypes.CLOUD, player.x, player.y, player.z, 14, 0.3, 0.1, 0.3, 0.02)
        ctx.world.playSound(null, player.x, player.y, player.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f)
        return true
    }
}
