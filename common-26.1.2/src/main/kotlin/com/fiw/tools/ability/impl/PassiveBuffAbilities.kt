package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.applyBuffs
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.parseBuffs
import com.fiw.tools.ability.parseDebuffs
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB

/**
 * `while_held` self-buffs. These re-apply a short effect each passive sweep, so they hold while the
 * item is in hand and fade shortly after it leaves. Pure buffs leave `cooldownTicks` at 0.
 */

/** Apply a configurable list of potion effects while held. The flexible buff engine. */
object PassiveBuffAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val duration = ctx.params.optI("buffDuration", 40)
        val amplifier = ctx.params.optI("amplifier", 0)
        ctx.player.applyBuffs(ctx.params.parseBuffs(listOf(MobEffects.SPEED)), duration, amplifier)
        return true
    }
}

/** Slow Falling while held — negates fall damage. */
object FeatherweightAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        ctx.player.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, ctx.params.optI("buffDuration", 40), 0))
        ctx.player.fallDistance = 0.0
        return true
    }
}

/** Underwater kit: Water Breathing + Dolphin's Grace + Night Vision, only while submerged. */
object AquaKitAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        if (!ctx.player.isUnderWater) return false
        ctx.player.applyBuffs(
            listOf(MobEffects.WATER_BREATHING, MobEffects.DOLPHINS_GRACE, MobEffects.NIGHT_VISION),
            ctx.params.optI("buffDuration", 60), 0
        )
        return true
    }
}

/** Fire Resistance while held. */
object ThermalWardAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        ctx.player.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, ctx.params.optI("buffDuration", 60), 0))
        return true
    }
}

/** Slowly tops off hunger while held. Use a `cooldownTicks` to pace it. */
object SaturationAuraAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val food = ctx.player.foodData
        val target = ctx.params.optI("foodLevel", 20)
        if (food.foodLevel >= target) return false
        food.foodLevel = minOf(target, food.foodLevel + ctx.params.optI("amount", 1))
        if (food.saturationLevel < 2f) food.setSaturation(2f)
        return true
    }
}

/** Continuously tugs nearby dropped items and XP toward you while held. */
object MagnetAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 6.0)
        val player = ctx.player
        val world = ctx.world
        val magnet = player.position().add(0.0, 0.5, 0.0)
        val box = AABB.ofSize(player.position(), radius * 2, radius * 2, radius * 2)
        var pulled = false
        for (item in world.getEntitiesOfClass(ItemEntity::class.java, box) { it.isAlive }) {
            if (item.position().distanceTo(player.position()) < 1.2) continue
            item.setDeltaMovement(magnet.subtract(item.position()).normalize().scale(0.25))
            pulled = true
        }
        for (orb in world.getEntitiesOfClass(ExperienceOrb::class.java, box) { it.isAlive }) {
            orb.setDeltaMovement(magnet.subtract(orb.position()).normalize().scale(0.25))
            pulled = true
        }
        return pulled
    }
}

/** Strength + Speed that scale up the lower your HP — nothing at full health. */
object BerserkerAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val maxHp = ctx.player.maxHealth
        if (maxHp <= 0f) return false
        val frac = ctx.player.health / maxHp
        if (frac >= 0.99f) return false
        val maxAmp = ctx.params.optI("maxAmplifier", 2)
        val amp = (((1f - frac) / 0.99f) * (maxAmp + 1)).toInt().coerceIn(0, maxAmp)
        val duration = ctx.params.optI("buffDuration", 40)
        ctx.player.addEffect(MobEffectInstance(MobEffects.STRENGTH, duration, amp))
        ctx.player.addEffect(MobEffectInstance(MobEffects.SPEED, duration, amp))
        return true
    }
}

/** Haste + Speed only while a hostile is within range — on in fights, off while mining. */
object CombatFocusAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 8.0)
        if (collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, AffectScope.HOSTILES).isEmpty()) return false
        val duration = ctx.params.optI("buffDuration", 40)
        val amplifier = ctx.params.optI("amplifier", 0)
        ctx.player.addEffect(MobEffectInstance(MobEffects.HASTE, duration, amplifier))
        ctx.player.addEffect(MobEffectInstance(MobEffects.SPEED, duration, amplifier))
        return true
    }
}

/** Slow Regeneration only when out of combat (no recent damage). Sustain, not in-fight healing. */
object LifelineAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val idleTicks = ctx.params.optI("idleTicks", 100)
        if (AbilityState.ticksSinceDamage(ctx.player.uuid, ctx.world.gameTime) <= idleTicks) return false
        if (ctx.player.health >= ctx.player.maxHealth) return false
        ctx.player.addEffect(MobEffectInstance(MobEffects.REGENERATION, ctx.params.optI("buffDuration", 40), ctx.params.optI("amplifier", 0)))
        return true
    }
}

/**
 * Applies a configurable list of negative status effects to the holder. The intended balance tool:
 * powerful items can impose drawbacks on whoever carries them. Works on `while_held` and `while_worn`.
 * Effect names are the same strings as `passive_buff` — any vanilla effect name works here.
 */
object HolderDebuffAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val duration = ctx.params.optI("buffDuration", 40)
        val amplifier = ctx.params.optI("amplifier", 0)
        ctx.player.applyBuffs(ctx.params.parseDebuffs(listOf(MobEffects.SLOWNESS)), duration, amplifier)
        return true
    }
}
