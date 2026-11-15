package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.ZoneEffects
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.AABB

/**
 * Support and social abilities — buffs for teammates, aggro control, and pure-flair effects with
 * little or no damage. These are the "for other players" tools.
 */

private fun effectByName(name: String): Holder<MobEffect>? = when (name.lowercase()) {
    "speed" -> MobEffects.SPEED
    "strength" -> MobEffects.STRENGTH
    "resistance" -> MobEffects.RESISTANCE
    "regeneration", "regen" -> MobEffects.REGENERATION
    "jump_boost", "jump" -> MobEffects.JUMP_BOOST
    "fire_resistance" -> MobEffects.FIRE_RESISTANCE
    "absorption" -> MobEffects.ABSORPTION
    "slow_falling" -> MobEffects.SLOW_FALLING
    else -> null
}

/** AoE buff to the caster and nearby allies — configurable `buffs` list, never reaches enemies. */
object RallyBannerAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 6.0)
        val duration = ctx.params.optI("duration", 200)
        val amplifier = ctx.params.optI("amplifier", 0)
        val effects = ctx.params.getAsJsonArray("buffs")
            ?.mapNotNull { effectByName(it.asString) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(MobEffects.SPEED, MobEffects.RESISTANCE)
        val player = ctx.player
        val world = ctx.world

        val recipients = collectTargets(world, player.position(), radius, player, AffectScope.ALLIES) + player
        for (entity in recipients) {
            for (effect in effects) entity.addEffect(MobEffectInstance(effect, duration, amplifier))
        }
        for (i in 0 until 24) {
            world.sendParticles(ParticleTypes.GLOW, player.x + (Math.random() - 0.5) * radius, player.y + Math.random() * 2.0, player.z + (Math.random() - 0.5) * radius, 1, 0.0, 0.0, 0.0, 0.0)
        }
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f)
        return true
    }
}

/** Forces nearby hostile mobs to target the caster — a tank tool for group PvE. */
object TauntAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 12.0)
        val player = ctx.player
        val world = ctx.world
        val box = AABB.ofSize(player.position(), radius * 2, radius, radius * 2)
        val mobs = world.getEntitiesOfClass(Mob::class.java, box) { it.isAlive && it is Enemy }
        for (mob in mobs) mob.setTarget(player)
        for (i in 0 until 20) {
            world.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.x + (Math.random() - 0.5) * 2, player.y + 1.5 + Math.random(), player.z + (Math.random() - 0.5) * 2, 1, 0.0, 0.0, 0.0, 0.0)
        }
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 1.4f)
        return mobs.isNotEmpty()
    }
}

/** Drops a stationary totem that pulses healing to the caster and nearby allies. */
object HealingTotemAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 5.0)
        val pulse = ctx.params.optF("pulseAmount", 1f)
        val lifetime = ctx.params.optI("lifetime", 200)
        val period = ctx.params.optI("period", 20)
        val player = ctx.player
        val world = ctx.world
        val center = player.position()

        ZoneEffects.repeating(lifetime, period) {
            if (player.isAlive) player.heal(pulse)
            for (ally in collectTargets(world, center, radius, player, AffectScope.ALLIES)) ally.heal(pulse)
            for (i in 0 until 12) {
                val a = i / 12.0 * Math.PI * 2
                world.sendParticles(ParticleTypes.HEART, center.x + Math.cos(a) * radius, center.y + 0.4, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y + 0.5, center.z, 20, 0.3, 0.4, 0.3, 0.05)
        world.playSound(null, center.x, center.y, center.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.0f)
        return true
    }
}

/** A tall particle beam + sound to mark a spot for everyone nearby. Pure communication. */
object BeaconPingAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val height = ctx.params.optD("height", 16.0)
        val player = ctx.player
        val world = ctx.world
        val steps = (height * 2).toInt().coerceIn(2, 80)
        for (i in 0..steps) {
            val y = player.y + i * 0.5
            world.sendParticles(ParticleTypes.END_ROD, player.x, y, player.z, 1, 0.02, 0.0, 0.02, 0.0)
        }
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.2f)
        return true
    }
}

/** Celebration burst on a kill — cosmetic only. */
object FireworkBurstAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val pos = ctx.targetPos ?: ctx.target?.position() ?: ctx.player.position()
        val world = ctx.world
        world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y + 1.0, pos.z, 40, 0.5, 0.5, 0.5, 0.18)
        world.playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.0f)
        return true
    }
}

/** Marks the target with Glowing so teammates can track them through walls. No damage. */
object GlowMarkAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val duration = ctx.params.optI("duration", 120)
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, duration, 0))
        ctx.world.sendParticles(ParticleTypes.GLOW, target.x, target.y + 1.2, target.z, 10, 0.3, 0.4, 0.3, 0.0)
        ctx.world.playSound(null, target.x, target.y, target.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 1.6f)
        return true
    }
}

/** Swaps positions with the nearest other player in range — chaotic social utility, no damage. */
object PrankSwapAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val range = ctx.params.optD("range", 8.0)
        val player = ctx.player
        val world = ctx.world
        val target = collectTargets(world, player.position(), range, player, AffectScope.PLAYERS)
            .filterIsInstance<ServerPlayer>()
            .minByOrNull { it.position().distanceTo(player.position()) } ?: return false
        val from = player.position()
        val to = target.position()
        player.teleportTo(to.x, to.y, to.z)
        target.teleportTo(from.x, from.y, from.z)
        world.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 20, 0.3, 0.5, 0.3, 0.1)
        world.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1.0, to.z, 20, 0.3, 0.5, 0.3, 0.1)
        world.playSound(null, from.x, from.y, from.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f)
        return true
    }
}
