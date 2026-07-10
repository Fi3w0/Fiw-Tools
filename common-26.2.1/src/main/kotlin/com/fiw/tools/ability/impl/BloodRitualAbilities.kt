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
 * `blood_debt` — a two-part risk mechanic driven by a `mode` param.
 *
 *  - `mode: "bank"` (best on right-click / shift-right-click): pay `healthCost` current HP to bank
 *    `charges` blood charges. They must be **spent within `windowTicks`** or the debt collects —
 *    every unspent charge backlashes for `backlashPerCharge`.
 *  - `mode: "spend"` (best on `on_attack`): each hit consumes one banked charge for `bonusDamage`
 *    (plus `lifesteal` healing).
 *
 * The skill is the loop: open a debt when you can afford the HP, then land enough hits to clear it
 * before the window closes — overcommit and the collector kills you.
 */
object BloodDebtAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val mode = ctx.params.get("mode")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: "bank"
        return if (mode == "spend") spend(ctx) else bank(ctx)
    }

    private fun bank(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val healthCost = p.optF("healthCost", 6f).coerceAtLeast(0f)
        // Never let the sacrifice itself kill you — refuse if you can't spare the blood.
        if (player.health <= healthCost + 1f) {
            player.sendOverlayMessage(TextParser.parse("&cNot enough blood to spare."))
            return false
        }
        val charges = p.optI("charges", 3).coerceIn(1, 64)
        val window = p.optI("windowTicks", 120).coerceIn(20, 1200)
        val backlash = p.optF("backlashPerCharge", 4f).coerceAtLeast(0f)

        player.health = (player.health - healthCost).coerceAtLeast(0.5f)
        AbilityState.addBloodDebt(player.uuid, charges, world.gameTime + window, backlash)

        val total = AbilityState.bloodDebtCharges(player.uuid)
        player.sendOverlayMessage(TextParser.parse("&4Blood Debt: &c$total&4 charge(s) — spend before it collects."))
        world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.x, player.y + 1.0, player.z, 20, 0.4, 0.6, 0.4, 0.05)
        world.playSound(null, player.x, player.y, player.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.5f)
        return true
    }

    private fun spend(ctx: AbilityContext): Boolean {
        val target = ctx.target ?: return false
        if (!AbilityState.spendBloodDebt(ctx.player.uuid)) return false
        val p = ctx.params
        val world = ctx.world
        val bonus = p.optF("bonusDamage", 4f)
        val lifesteal = p.optF("lifesteal", 2f)
        if (bonus > 0f) target.hurtServer(world, world.damageSources().indirectMagic(ctx.player, ctx.player), bonus)
        if (lifesteal > 0f) ctx.player.heal(lifesteal)
        world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.x, target.y + target.bbHeight * 0.5, target.z, 10, 0.2, 0.3, 0.2, 0.1)
        world.playSound(null, target.x, target.y, target.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 0.6f)
        return true
    }
}