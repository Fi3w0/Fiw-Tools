package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.effectByName
import com.fiw.tools.ability.optB
import com.fiw.tools.ability.optI
import com.google.gson.JsonObject
import net.minecraft.world.effect.MobEffectInstance

/**
 * Apply-effect (`apply_effect`) — the general-purpose potion-effect tool. Names any vanilla effect
 * (`speed`, `poison`, `wither`, `strength`, `levitation`, …) and drops it on the caster or the
 * ability's target, for any trigger. Replaces reaching for a bespoke buff/debuff ability every time.
 *
 * | Param | Default | Meaning |
 * |-------|---------|---------|
 * | `effect` | — | Effect name (see the buff list). Required; unknown = no-op. |
 * | `target` | `self` | `self` = the holder, `target` = the attacked/looked-at entity. |
 * | `duration` | 100 | Ticks. |
 * | `amplifier` | 0 | 0 = level I. |
 * | `ambient` | false | Fainter "beacon" particles. |
 * | `particles` | true | Show the swirl particles at all. |
 */
object ApplyEffectAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val effect = effectByName(p.optS("effect", "")) ?: return false
        val target = if (p.optS("target", "self").lowercase() == "target") ctx.target ?: return false
        else ctx.player
        target.addEffect(MobEffectInstance(
            effect,
            p.optI("duration", 100),
            p.optI("amplifier", 0),
            p.optB("ambient", false),
            p.optB("particles", true)
        ))
        return true
    }

    private fun JsonObject?.optS(key: String, default: String): String =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
