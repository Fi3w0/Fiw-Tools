package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.applyBuffs
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.effectByName
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.parseBuffs
import net.minecraft.world.effect.MobEffects

/**
 * `while_held` auras — buff or heal the caster and nearby allies each passive sweep. Never reach
 * enemies (scope is fixed to allies + self).
 */

/** AoE buff to you and nearby allies — configurable `buffs` list. */
object RallyAuraAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 6.0)
        val duration = ctx.params.optI("buffDuration", 40)
        val amplifier = ctx.params.optI("amplifier", 0)
        val buffs = ctx.params.parseBuffs(listOf(MobEffects.SPEED, MobEffects.RESISTANCE))
        val recipients = collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, AffectScope.ALLIES) + ctx.player
        for (entity in recipients) entity.applyBuffs(buffs, duration, amplifier)
        return true
    }
}

/** Radiates a single configurable buff to you and nearby allies. */
object BeaconAuraAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val name = ctx.params.get("buff")?.takeIf { !it.isJsonNull }?.asString ?: "speed"
        val effect = effectByName(name) ?: return false
        val radius = ctx.params.optD("radius", 6.0)
        val duration = ctx.params.optI("buffDuration", 40)
        val amplifier = ctx.params.optI("amplifier", 0)
        val recipients = collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, AffectScope.ALLIES) + ctx.player
        for (entity in recipients) entity.applyBuffs(listOf(effect), duration, amplifier)
        return true
    }
}

/** Gentle healing pulse to you and nearby allies. Pace it with `cooldownTicks`. */
object MendingAuraAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val radius = ctx.params.optD("radius", 5.0)
        val heal = ctx.params.optF("heal", 1f)
        ctx.player.heal(heal)
        for (ally in collectTargets(ctx.world, ctx.player.position(), radius, ctx.player, AffectScope.ALLIES)) ally.heal(heal)
        return true
    }
}
