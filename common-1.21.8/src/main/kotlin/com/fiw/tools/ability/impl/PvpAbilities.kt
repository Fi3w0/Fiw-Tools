package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.optD
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

/**
 * PvP-leaning abilities: control, counters and finishers. All damage routes through the player as the
 * source so PvP/keep-inventory rules apply, and none of them can touch the caster.
 */

/** Yanks the target toward the caster — a pull instead of vanilla knockback, to catch runners. */
object GrapplingPullAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val strength = ctx.params.optD("pullStrength", 1.2)
        val player = ctx.player
        val delta = player.position().subtract(target.position())
        val flat = Vec3(delta.x, 0.0, delta.z)
        if (flat.length() < 0.1) return false
        val dir = flat.normalize()
        target.push(dir.x * strength, 0.25, dir.z * strength)
        target.hurtMarked = true
        ctx.world.sendParticles(ParticleTypes.CRIT, target.x, target.y + 1.0, target.z, 12, 0.2, 0.3, 0.2, 0.1)
        ctx.world.playSound(null, target.x, target.y, target.z,
            SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8f, 1.1f)
        return true
    }
}

/** Soft disarm: Weakness + Mining Fatigue so the victim's next hits hit softer. Pair with a low chance. */
object DisarmAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val duration = ctx.params.optI("duration", 60)
        val amplifier = ctx.params.optI("amplifier", 0)
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, duration, amplifier))
        ctx.world.sendParticles(ParticleTypes.SCRAPE, target.x, target.y + 1.2, target.z, 10, 0.3, 0.3, 0.3, 0.0)
        ctx.world.playSound(null, target.x, target.y, target.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.6f)
        return true
    }
}

/** Reflects a capped fraction of incoming melee damage back at the attacker and shoves them off. */
object ParryCounterAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val attacker = ctx.target ?: return false
        val percent = ctx.params.optF("reflectPercent", 0.3f).coerceIn(0f, 1f)
        val maxReflect = ctx.params.optF("maxReflect", 6f)
        val reflect = (ctx.damageAmount * percent).coerceAtMost(maxReflect)
        if (reflect <= 0f) return false
        val player = ctx.player
        val world = ctx.world
        attacker.hurtServer(world, world.damageSources().magic(), reflect)
        val delta = attacker.position().subtract(player.position())
        val flat = Vec3(delta.x, 0.0, delta.z)
        if (flat.length() > 0.1) {
            val dir = flat.normalize()
            val kb = ctx.params.optD("knockback", 0.5)
            attacker.push(dir.x * kb, 0.3, dir.z * kb)
            attacker.hurtMarked = true
        }
        world.sendParticles(ParticleTypes.ENCHANTED_HIT, attacker.x, attacker.y + 1.0, attacker.z, 10, 0.3, 0.3, 0.3, 0.1)
        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 1.3f)
        return true
    }
}

/** Finisher: bonus damage only when the target is already below a HP fraction — useless on full HP. */
object ExecuteAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        if (target.maxHealth <= 0f) return false
        val threshold = ctx.params.optF("threshold", 0.25f)
        if (target.health / target.maxHealth > threshold) return false
        val bonus = ctx.params.optF("bonus", 6f)
        val world = ctx.world
        target.hurtServer(world, world.damageSources().playerAttack(ctx.player), bonus)
        world.sendParticles(ParticleTypes.SOUL, target.x, target.y + 1.0, target.z, 16, 0.3, 0.4, 0.3, 0.02)
        world.playSound(null, target.x, target.y, target.z,
            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.9f, 0.8f)
        return true
    }
}

/** Lifesteal: heal a small flat amount on hit (capped by max HP naturally). */
object LeechAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val amount = ctx.params.optF("amount", 1.0f)
        val player = ctx.player
        if (player.health >= player.maxHealth) return false
        player.heal(amount)
        ctx.world.sendParticles(ParticleTypes.HEART, player.x, player.y + 1.0, player.z, 3, 0.3, 0.4, 0.3, 0.0)
        ctx.world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.x, target.y + 1.0, target.z, 4, 0.2, 0.2, 0.2, 0.0)
        return true
    }
}

/** Marks the victim with Hunger + Slowness — denies regen and chases, no hard lock. */
object SilenceSigilAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val duration = ctx.params.optI("duration", 60)
        val amplifier = ctx.params.optI("amplifier", 0)
        target.addEffect(MobEffectInstance(MobEffects.HUNGER, duration, amplifier))
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, duration, amplifier))
        ctx.world.sendParticles(ParticleTypes.SCULK_SOUL, target.x, target.y + 1.4, target.z, 8, 0.2, 0.3, 0.2, 0.01)
        ctx.world.playSound(null, target.x, target.y, target.z,
            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.5f, 1.3f)
        return true
    }
}

/** Marks a target on hit; striking the same marked target again before it expires deals bonus damage. */
object TetherAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        val markDuration = ctx.params.optI("markDuration", 100)
        val bonus = ctx.params.optF("bonus", 4f)
        val world = ctx.world
        val wasMarked = AbilityState.markTether(ctx.player.uuid, target.uuid, world.gameTime, markDuration)
        if (wasMarked) {
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, target.x, target.y + 1.0, target.z, 14, 0.3, 0.4, 0.3, 0.2)
            world.playSound(null, target.x, target.y, target.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9f, 1.1f)
            target.hurtServer(world, world.damageSources().playerAttack(ctx.player), bonus)
        } else {
            world.sendParticles(ParticleTypes.GLOW, target.x, target.y + 1.0, target.z, 8, 0.3, 0.4, 0.3, 0.0)
        }
        return true
    }
}
