package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AbilityState
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.util.TextParser
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * `riposte` — a timing parry set by a `mode` param.
 *
 *  - `mode: "parry"` (best on right-click): primes a brief parry. If a hit lands within `parryWindow`
 *    ticks it's **negated** (handled in the damage hook), a fraction is reflected, and a riposte
 *    window opens.
 *  - `mode: "strike"` (best on `on_attack`): while the riposte window is open, your hit lands `bonus`
 *    damage — or executes a target below `executeThreshold` HP.
 *
 * The skill is reading the incoming attack and tapping parry just before it connects — early or late
 * and you simply eat the hit.
 */
object RiposteAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val mode = ctx.params.get("mode")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: "parry"
        return if (mode == "strike") strike(ctx) else parry(ctx)
    }

    private fun parry(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val window = p.optI("parryWindow", 8).coerceIn(2, 40)
        AbilityState.armParry(player.uuid, ctx.world.gameTime + window, p.optF("reflect", 0.5f), p.optI("riposteWindow", 30).coerceIn(5, 200))
        ctx.world.playSound(null, player.x, player.y, player.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5f, 0.8f)
        player.sendOverlayMessage(TextParser.parse("&7Bracing…"))
        return true
    }

    private fun strike(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        if (!AbilityState.consumeRiposte(ctx.player.uuid, ctx.world.gameTime)) return false
        val p = ctx.params
        val world = ctx.world
        val src = world.damageSources().indirectMagic(ctx.player, ctx.player)
        val execute = p.optF("executeThreshold", 0f)
        if (execute > 0f && target.maxHealth > 0f && target.health / target.maxHealth <= execute) {
            target.hurtServer(world, src, target.maxHealth * 100f)
        } else {
            val bonus = p.optF("bonus", 8f)
            if (bonus > 0f) target.hurtServer(world, src, bonus)
        }
        world.sendParticles(ParticleTypes.CRIT, target.x, target.y + target.bbHeight * 0.5, target.z, 16, 0.3, 0.3, 0.3, 0.3)
        world.playSound(null, target.x, target.y, target.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.9f)
        return true
    }
}

/**
 * `sanguine_tether` — a two-step blood link set by a `mode` param.
 *
 *  - `mode: "mark"` (best on `on_attack`): marks the struck target for `markWindow` ticks.
 *  - `mode: "bind"` (best on right-click): if a fresh mark exists, binds it for `durationTicks`.
 *    While bound, `splitFraction` of every hit you take is dealt to the tethered target and healed
 *    back on you (a net split). If the target dies bound, the link snaps and `backlash`es you.
 *
 * The skill is the mark→bind timing and picking a durable target — bind something fragile and its
 * death turns the crutch into a wound.
 */
object SanguineTetherAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val mode = ctx.params.get("mode")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: "mark"
        return if (mode == "bind") bind(ctx) else mark(ctx)
    }

    private fun mark(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        AbilityState.markSanguine(ctx.player.uuid, target.uuid, ctx.world.gameTime + ctx.params.optI("markWindow", 60))
        ctx.world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.x, target.y + target.bbHeight * 0.5, target.z, 8, 0.2, 0.3, 0.2, 0.05)
        return true
    }

    private fun bind(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val ok = AbilityState.tryBindSanguine(ctx.player, p.optF("splitFraction", 0.5f), p.optF("backlash", 6f), p.optI("durationTicks", 200).coerceIn(20, 1200))
        if (!ok) {
            ctx.player.sendOverlayMessage(TextParser.parse("&cNo fresh mark to bind."))
            return false
        }
        ctx.player.sendOverlayMessage(TextParser.parse("&4Blood tether bound."))
        ctx.world.playSound(null, ctx.player.x, ctx.player.y, ctx.player.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 0.5f)
        return true
    }
}
